import Foundation
import CoreVideo
import MediaPipeTasksVision
import simd

/// Runs the MediaPipe Face Landmarker on every camera frame and reports
/// head pose (yaw / pitch / roll), the smile score, the face box and the
/// image-quality metrics.
///
/// This is the iOS twin of the Android `FaceAnalyzer` and produces exactly the
/// same numbers, so the quality gates behave identically on both platforms.
final class FaceAnalyzer {

    private var landmarker: FaceLandmarker?
    private var lastTimestampMs: Int64 = -1

    /// Non-nil when the MediaPipe model could not be opened.
    private(set) var loadError: String?

    init(modelPath: String) {
        do {
            let baseOptions = BaseOptions()
            baseOptions.modelAssetPath = modelPath
            let options = FaceLandmarkerOptions()
            options.baseOptions = baseOptions
            options.runningMode = .video
            options.numFaces = 1
            options.outputFaceBlendshapes = true
            options.outputFacialTransformationMatrixes = true
            landmarker = try FaceLandmarker(options: options)
        } catch {
            loadError = "مدل تشخیص چهره بارگذاری نشد: \(error.localizedDescription)"
            landmarker = nil
        }
    }

    /// - Parameters:
    ///   - pixelBuffer: 32BGRA, upright (portrait) frame.
    ///   - timestampMs: monotonically increasing timestamp in milliseconds.
    func analyze(pixelBuffer: CVPixelBuffer, timestampMs: Int64) -> FaceState {
        guard let landmarker = landmarker else {
            return FaceState(detected: false, error: loadError)
        }
        let ts = max(timestampMs, lastTimestampMs + 1)
        lastTimestampMs = ts

        do {
            let image = try MPImage(pixelBuffer: pixelBuffer)
            let result = try landmarker.detect(videoFrame: image, timestampInMilliseconds: Int(ts))
            return Self.makeState(result: result, pixelBuffer: pixelBuffer)
        } catch {
            return FaceState(detected: false, error: error.localizedDescription)
        }
    }

    // MARK: - Result conversion

    private static func makeState(result: FaceLandmarkerResult, pixelBuffer: CVPixelBuffer) -> FaceState {
        guard let landmarks = result.faceLandmarks.first, landmarks.count > 400 else {
            return FaceState(detected: false)
        }

        var minX = Float.greatestFiniteMagnitude
        var maxX = -Float.greatestFiniteMagnitude
        var minY = Float.greatestFiniteMagnitude
        var maxY = -Float.greatestFiniteMagnitude
        for point in landmarks {
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }
        let box = CGRect(x: CGFloat(minX), y: CGFloat(minY),
                         width: CGFloat(maxX - minX), height: CGFloat(maxY - minY))

        // Chin (152) and forehead (10) give a stable "how close am I" metric.
        let chinY = landmarks.count > 152 ? landmarks[152].y : maxY
        let foreheadY = landmarks.count > 10 ? landmarks[10].y : minY
        let noseX: Float = landmarks.count > 1 ? landmarks[1].x : Float(box.midX)
        let noseY: Float = landmarks.count > 1 ? landmarks[1].y : Float(box.midY)

        var smile: Float = 0
        var blink: Float = 0
        if let blendshapes = result.faceBlendshapes.first {
            var smileScores: [Float] = []
            var blinkScores: [Float] = []
            for category in blendshapes.categories {
                guard let name = category.categoryName else { continue }
                if name.hasPrefix("mouthSmile") { smileScores.append(category.score) }
                if name.hasPrefix("eyeBlink") { blinkScores.append(category.score) }
            }
            if !smileScores.isEmpty {
                smile = (smileScores.reduce(0, +) / Float(smileScores.count)).clamped01
            }
            if let maxBlink = blinkScores.max() { blink = maxBlink }
        }

        let quality = ImageQuality.measure(pixelBuffer: pixelBuffer, box: box)

        // Landmarks and the transformation matrix are the input of the 3D model.
        let landmarkPoints: [SIMD3<Float>] = landmarks.map {
            SIMD3<Float>(Float($0.x), Float($0.y), Float($0.z))
        }
        var matrixData: [Float] = []
        if let matrix = result.facialTransformationMatrixes.first {
            let rows = Int(matrix.rows)
            let cols = Int(matrix.columns)
            for i in 0..<(rows * cols) { matrixData.append(matrix.data[i]) }
        }
        let bufferWidth = Float(CVPixelBufferGetWidth(pixelBuffer))
        let bufferHeight = Float(CVPixelBufferGetHeight(pixelBuffer))
        let aspect = bufferWidth > 0 ? bufferHeight / bufferWidth : 1

        var state = FaceState(
            detected: true,
            yawRaw: 0,
            pitchRaw: 0,
            rollRaw: 0,
            smile: smile,
            eyesOpen: blink < 0.4,
            faceBox: box,
            faceWidth: Float(box.width),
            faceHeight: max(0, chinY - foreheadY),
            noseX: noseX,
            noseY: noseY,
            brightness: quality.brightness,
            sharpness: quality.sharpness,
            error: nil,
            timestamp: Date().timeIntervalSince1970,
            landmarks: landmarkPoints,
            transform: matrixData,
            aspect: aspect
        )

        if let matrix = result.facialTransformationMatrixes.first,
           let angles = rotationAngles(from: matrix) {
            state.yawRaw = angles.yaw
            state.pitchRaw = angles.pitch
            state.rollRaw = angles.roll
        }
        return state
    }

    /// Extracts yaw / pitch / roll from the 4x4 facial transformation matrix.
    ///
    /// MediaPipe stores the matrix column-major (the last column is the
    /// translation). Some builds ship the transposed layout, which is detected
    /// here by looking for the rigid-transform row `[0 0 0 1]`.
    private static func rotationAngles(from matrix: TransformMatrix) -> (yaw: Float, pitch: Float, roll: Float)? {
        let rows = Int(matrix.rows)
        let cols = Int(matrix.columns)
        guard rows >= 4, cols >= 4 else { return nil }

        func columnMajor(_ r: Int, _ c: Int) -> Float {
            matrix.data[c * rows + r]
        }
        func rowMajor(_ r: Int, _ c: Int) -> Float {
            matrix.data[r * cols + c]
        }

        let useColumnMajor: Bool = {
            let bottom = (columnMajor(3, 0), columnMajor(3, 1), columnMajor(3, 3))
            if abs(bottom.0) < 1e-3 && abs(bottom.1) < 1e-3 && abs(bottom.2 - 1) < 1e-3 {
                return true
            }
            let right = (rowMajor(0, 3), rowMajor(1, 3), rowMajor(3, 3))
            if abs(right.0) < 1e-3 && abs(right.1) < 1e-3 && abs(right.2 - 1) < 1e-3 {
                return false
            }
            return true
        }()

        let element: (Int, Int) -> Float
        if useColumnMajor {
            element = columnMajor
        } else {
            element = rowMajor
        }

        let r00 = element(0, 0)
        let r10 = element(1, 0)
        let r20 = element(2, 0)
        let r21 = element(2, 1)
        let r22 = element(2, 2)

        let pitch = Float(atan2(Double(r21), Double(r22)) * 180.0 / .pi)
        let yaw = Float(atan2(Double(-r20), Double((r21 * r21 + r22 * r22).squareRoot())) * 180.0 / .pi)
        let roll = Float(atan2(Double(r10), Double(r00)) * 180.0 / .pi)
        return (yaw: yaw, pitch: pitch, roll: roll)
    }
}

extension Float {
    var clamped01: Float {
        Swift.min(1, Swift.max(0, self))
    }
}
