import Foundation
import Combine
import AVFoundation
import MediaPipeTasksVision

/// AVFoundation capture session (photo + video) wired to the MediaPipe analyzer.
///
/// This is the iOS twin of the Android `CameraX` pipeline: the video output
/// feeds the analyzer, the photo output produces the clinic photos.
final class CameraController: NSObject, ObservableObject {

    let session = AVCaptureSession()

    private let photoOutput = AVCapturePhotoOutput()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let sessionQueue = DispatchQueue(label: "rhinoshot.camera.session")
    private let videoQueue = DispatchQueue(label: "rhinoshot.camera.video")
    private var analyzer: FaceAnalyzer?
    private var currentInput: AVCaptureDeviceInput?

    @Published var faceState = FaceState()
    @Published var isFront = true
    @Published var errorMessage: String?
    @Published var isCapturing = false

    /// Called with the raw JPEG data of the captured photo.
    var onPhotoCaptured: ((Data) -> Void)?

    override init() {
        super.init()
        videoOutput.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
        ]
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(self, queue: videoQueue)

        if let path = Bundle.main.path(forResource: "face_landmarker", ofType: "task") {
            analyzer = FaceAnalyzer(modelPath: path)
            if let error = analyzer?.loadError {
                errorMessage = error
            }
        } else {
            errorMessage = "فایل face_landmarker.task در بسته‌ی اپ پیدا نشد."
        }
    }

    // MARK: - Session lifecycle

    func start() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            if self.session.inputs.isEmpty {
                self.configureSession()
            }
            if !self.session.isRunning {
                self.session.startRunning()
            }
        }
    }

    func stop() {
        sessionQueue.async { [weak self] in
            guard let self = self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    func toggleCamera() {
        sessionQueue.async { [weak self] in
            guard let self = self else { return }
            let newFront = !self.isFront
            guard let device = self.camera(device: newFront) else { return }
            do {
                let input = try AVCaptureDeviceInput(device: device)
                self.session.beginConfiguration()
                if let current = self.currentInput {
                    self.session.removeInput(current)
                }
                if self.session.canAddInput(input) {
                    self.session.addInput(input)
                    self.currentInput = input
                    DispatchQueue.main.async { self.isFront = newFront }
                }
                self.session.commitConfiguration()
                self.applyOrientation()
                self.limitFrameRate()
            } catch {
                DispatchQueue.main.async { self.errorMessage = error.localizedDescription }
            }
        }
    }

    private func camera(device front: Bool) -> AVCaptureDevice? {
        let types: [AVCaptureDevice.DeviceType] = [
            .builtInWideAngleCamera
        ]
        let discovery = AVCaptureDevice.DiscoverySession(
            deviceTypes: types,
            mediaType: .video,
            position: front ? .front : .back
        )
        return discovery.devices.first
    }

    private func configureSession() {
        session.beginConfiguration()
        session.sessionPreset = .photo

        guard let device = camera(device: isFront),
              let input = try? AVCaptureDeviceInput(device: device) else {
            session.commitConfiguration()
            DispatchQueue.main.async { self.errorMessage = "دوربین در دسترس نیست." }
            return
        }
        if session.canAddInput(input) {
            session.addInput(input)
            currentInput = input
        }
        if session.canAddOutput(photoOutput) {
            session.addOutput(photoOutput)
        }
        if session.canAddOutput(videoOutput) {
            session.addOutput(videoOutput)
        }
        photoOutput.maxPhotoQualityPrioritization = .quality
        session.commitConfiguration()
        applyOrientation()
        limitFrameRate()
    }

    /// Keeps the delivered buffers upright so the analysis and the stored
    /// photos always match the portrait screen.
    private func applyOrientation() {
        let angle: Float = 90
        if #available(iOS 17.0, *) {
            if let connection = videoOutput.connection(with: .video),
               connection.isVideoRotationAngleSupported(angle) {
                connection.videoRotationAngle = angle
            }
        } else if let connection = videoOutput.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
        }
        if #available(iOS 17.0, *) {
            if let connection = photoOutput.connection(with: .video),
               connection.isVideoRotationAngleSupported(angle) {
                connection.videoRotationAngle = angle
            }
        } else if let connection = photoOutput.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
        }
    }

    /// 15 frames per second are enough for the guidance and keep the phone cool.
    private func limitFrameRate() {
        guard let connection = videoOutput.connection(with: .video) else { return }
        let interval = CMTime(value: 1, timescale: 15)
        if #available(iOS 17.0, *) {
            connection.minFrameDuration = interval
            connection.maxFrameDuration = interval
        } else {
            connection.videoMinFrameDuration = interval
            connection.videoMaxFrameDuration = interval
        }
    }

    // MARK: - Photo capture

    func capturePhoto() {
        guard !isCapturing else { return }
        isCapturing = true
        let settings = AVCapturePhotoSettings()
        settings.photoQualityPrioritization = .quality
        settings.flashMode = .off
        photoOutput.capturePhoto(with: settings, delegate: self)
    }
}

extension CameraController: AVCapturePhotoCaptureDelegate {
    func photoOutput(
        _ output: AVCapturePhotoOutput,
        didFinishProcessingPhoto photo: AVCapturePhoto,
        error: Error?
    ) {
        DispatchQueue.main.async { self.isCapturing = false }
        guard error == nil, let data = photo.fileDataRepresentation() else {
            DispatchQueue.main.async {
                self.errorMessage = "ثبت عکس انجام نشد: \(error?.localizedDescription ?? "خطای نامشخص")"
            }
            return
        }
        onPhotoCaptured?(data)
    }
}

extension CameraController: AVCaptureVideoDataOutputSampleBufferDelegate {
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let analyzer = analyzer else { return }
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let ts = Int64(CMSampleBufferGetPresentationTimeStamp(sampleBuffer).seconds * 1000)
        let state = analyzer.analyze(pixelBuffer: pixelBuffer, timestampMs: ts)
        DispatchQueue.main.async { self.faceState = state }
    }
}
