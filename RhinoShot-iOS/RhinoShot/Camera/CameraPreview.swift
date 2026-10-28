import SwiftUI
import AVFoundation

/// Live camera preview. The front camera is mirrored so the operator sees the
/// patient the way the patient sees them in a mirror.
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    var isFront: Bool = true

    func makeUIView(context: Context) -> PreviewContainerView {
        let view = PreviewContainerView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        applyOrientation(to: view)
        // The capture connection only exists once the session is running.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            applyOrientation(to: view)
        }
        return view
    }

    func updateUIView(_ uiView: PreviewContainerView, context: Context) {
        if uiView.previewLayer.session !== session {
            uiView.previewLayer.session = session
        }
        applyOrientation(to: uiView)
    }

    private func applyOrientation(to view: PreviewContainerView) {
        guard let connection = view.previewLayer.connection else { return }
        let angle: Float = 90
        if #available(iOS 17.0, *) {
            if connection.isVideoRotationAngleSupported(angle) {
                connection.videoRotationAngle = angle
            }
        } else if connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        if connection.isVideoMirroringSupported {
            connection.automaticallyAdjustsVideoMirroring = false
            connection.isVideoMirrored = isFront
        }
    }
}

final class PreviewContainerView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    var previewLayer: AVCaptureVideoPreviewLayer {
        // swiftlint:disable:next force_cast
        layer as! AVCaptureVideoPreviewLayer
    }
}
