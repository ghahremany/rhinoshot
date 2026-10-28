import Foundation
import CoreGraphics
import simd

/// Everything the app knows about the face in the current camera frame.
struct FaceState {
    var detected: Bool = false
    /// Raw camera-space angles (degrees) coming from the MediaPipe transformation matrix.
    var yawRaw: Float = 0
    var pitchRaw: Float = 0
    var rollRaw: Float = 0
    /// 0...1
    var smile: Float = 0
    var eyesOpen: Bool = true
    /// Face bounding box in normalised image coordinates (0...1).
    var faceBox: CGRect?
    /// Face width / frame width.
    var faceWidth: Float = 0
    /// Face height / frame height.
    var faceHeight: Float = 0
    /// Nose tip position in normalised image coordinates.
    var noseX: Float = 0.5
    var noseY: Float = 0.5
    /// Mean luminance of the face crop, 0...1.
    var brightness: Float = 0
    /// Tenengrad sharpness of the face crop, normalised by brightness.
    var sharpness: Float = 0
    var error: String?
    var timestamp: TimeInterval = 0
    /// The landmarks of the detected face (x, y, z) in normalised image
    /// coordinates — the input of the 3D reconstruction.
    var landmarks: [SIMD3<Float>] = []
    /// The facial transformation matrix (16 values).
    var transform: [Float] = []
    /// Image height divided by image width.
    var aspect: Float = 1
}

/// Converts the raw camera-space pose into the subject-relative convention used
/// by the clinic form, and removes the personal offset of every patient's head.
///
/// The signs depend on the device and on the camera that is used, therefore they
/// can be calibrated once per phone (see `CalibrationSheet`).
struct Calibration: Equatable {
    var yawSignFront: Int = -1
    var yawSignBack: Int = 1
    var pitchSign: Int = 1
    var yawOffset: Float = 0
    var pitchOffset: Float = 0

    func yaw(_ raw: Float, front: Bool) -> Float {
        (front ? yawSignFront : yawSignBack) * raw + yawOffset
    }

    func pitch(_ raw: Float) -> Float {
        pitchSign * raw + pitchOffset
    }

    /// Re-centres the current pose on zero (patient looking straight at the camera).
    func withZero(rawYaw: Float, rawPitch: Float, front: Bool) -> Calibration {
        var copy = self
        copy.yawOffset = -(front ? yawSignFront : yawSignBack) * rawYaw
        copy.pitchOffset = -pitchSign * rawPitch
        return copy
    }

    func withYawSign(front: Bool, sign: Int) -> Calibration {
        var copy = self
        if front { copy.yawSignFront = sign } else { copy.yawSignBack = sign }
        return copy
    }

    // MARK: - Persistence

    private static let defaultsKey = "rhinoshot_calibration"

    static func load() -> Calibration {
        let d = UserDefaults.standard
        var c = Calibration()
        if d.object(forKey: "\(defaultsKey)_yaw_front") != nil {
            c.yawSignFront = d.integer(forKey: "\(defaultsKey)_yaw_front")
            c.yawSignBack = d.integer(forKey: "\(defaultsKey)_yaw_back")
            c.pitchSign = d.integer(forKey: "\(defaultsKey)_pitch")
            c.yawOffset = d.float(forKey: "\(defaultsKey)_yaw_offset")
            c.pitchOffset = d.float(forKey: "\(defaultsKey)_pitch_offset")
        }
        return c
    }

    func save() {
        let d = UserDefaults.standard
        d.set(yawSignFront, forKey: "\(Calibration.defaultsKey)_yaw_front")
        d.set(yawSignBack, forKey: "\(Calibration.defaultsKey)_yaw_back")
        d.set(pitchSign, forKey: "\(Calibration.defaultsKey)_pitch")
        d.set(yawOffset, forKey: "\(Calibration.defaultsKey)_yaw_offset")
        d.set(pitchOffset, forKey: "\(Calibration.defaultsKey)_pitch_offset")
    }
}
