import Foundation
import CoreGraphics

/// How strict the image-quality gate is.
enum QualityMode: String, CaseIterable {
    case strict = "سخت‌گیرانه"
    case relaxed = "آسان"
}

struct Check: Identifiable {
    let id = UUID()
    let ok: Bool
    let blocking: Bool
    let message: String
}

struct Evaluation {
    let checks: [Check]
    /// true when nothing blocks the shutter
    let ready: Bool
    /// true when the head pose itself is inside the tolerance of the shot
    let inTolerance: Bool

    var issues: [Check] { checks.filter { !$0.ok } }
    var blocking: [Check] { issues.filter { $0.blocking } }
    var warnings: [Check] { issues.filter { !$0.blocking } }
}

/// Turns the raw face measurement into operator guidance in Persian.
/// This is a line-by-line twin of the Android `PoseEvaluator`: same gates,
/// same thresholds, same wording.
enum PoseEvaluator {

    /// How long the pose has to be held before the automatic shutter fires.
    static let holdMilliseconds: Double = 900

    private static let minFaceWidth: Float = 0.20
    private static let maxFaceWidth: Float = 0.72
    private static let minFaceHeight: Float = 0.22
    private static let maxFaceHeight: Float = 0.88
    private static let centerTolX: CGFloat = 0.14
    private static let centerTolY: CGFloat = 0.16
    private static let noseMinFaceHeight: Float = 0.42
    private static let noseCenterTol: CGFloat = 0.20

    static func noFace() -> Evaluation {
        Evaluation(
            checks: [Check(ok: false, blocking: true, message: "چهره دیده نمی‌شود؛ قاب را روی صورت بیمار بگیرید")],
            ready: false,
            inTolerance: false
        )
    }

    static func evaluate(
        shot: Shot,
        state: FaceState,
        yaw: Float,
        pitch: Float,
        mode: QualityMode,
        toleranceScale: Float = 1
    ) -> Evaluation {
        guard state.detected else { return noFace() }

        var checks: [Check] = []
        var inTolerance = true

        // ---- pose ----------------------------------------------------------
        if let targetYaw = shot.yaw {
            let tol = shot.tolYaw * toleranceScale
            let delta = yaw - targetYaw
            let ok = abs(delta) <= tol
            inTolerance = inTolerance && ok
            checks.append(Check(
                ok: ok, blocking: true,
                message: ok ? "زاویه‌ی افقی درست است (\(Int(yaw))°)" : yawInstruction(target: targetYaw, current: yaw)
            ))
        }
        if let targetPitch = shot.pitch {
            let tol = shot.tolPitch * toleranceScale
            let delta = pitch - targetPitch
            let ok = abs(delta) <= tol
            inTolerance = inTolerance && ok
            checks.append(Check(
                ok: ok, blocking: true,
                message: ok ? "زاویه‌ی عمودی درست است (\(Int(pitch))°)" : pitchInstruction(target: targetPitch, current: pitch)
            ))
        }
        if shot.needSmile {
            let ok = state.smile >= 0.35
            checks.append(Check(ok: ok, blocking: true, message: ok ? "لبخند ثبت شد" : "لبخند بزنید"))
        }

        // ---- framing -------------------------------------------------------
        if shot.framing == .nose {
            let closeEnough = state.faceHeight >= noseMinFaceHeight
            checks.append(Check(
                ok: closeEnough, blocking: true,
                message: closeEnough ? "فاصله‌ی دوربین مناسب است" : "دوربین را نزدیک‌تر بیاورید تا بینی پر کادر شود"
            ))
            let noseCentered = abs(CGFloat(state.noseX) - 0.5) <= noseCenterTol
            checks.append(Check(
                ok: noseCentered, blocking: true,
                message: noseCentered ? "بینی در مرکز کادر است" : "بینی را در مرکز کادر قرار دهید"
            ))
        } else {
            let widthOk = state.faceWidth >= minFaceWidth && state.faceWidth <= maxFaceWidth
                && state.faceHeight >= minFaceHeight && state.faceHeight <= maxFaceHeight
            var framingMessage = "فاصله‌ی دوربین مناسب است"
            if state.faceWidth < minFaceWidth || state.faceHeight < minFaceHeight {
                framingMessage = "کمی نزدیک‌تر شوید"
            } else if state.faceWidth > maxFaceWidth || state.faceHeight > maxFaceHeight {
                framingMessage = "کمی دورتر شوید"
            }
            checks.append(Check(ok: widthOk, blocking: true, message: framingMessage))

            let centered: Bool = {
                guard let box = state.faceBox else { return false }
                return abs(box.midX - 0.5) <= centerTolX && abs(box.midY - 0.45) <= centerTolY
            }()
            checks.append(Check(
                ok: centered, blocking: true,
                message: centered ? "صورت در مرکز کادر است" : "صورت را در مرکز کادر قرار دهید"
            ))
        }

        // ---- light ---------------------------------------------------------
        let brightness = state.brightness
        let tooDark = brightness < (mode == .strict ? 0.18 : 0.12)
        let tooBright = brightness > (mode == .strict ? 0.94 : 0.97)
        let lightOk = !tooDark && !tooBright
        var lightMessage = "نور مناسب است"
        if tooDark { lightMessage = "نور محیط کم است؛ روشن‌تر کنید" }
        else if tooBright { lightMessage = "نور خیلی زیاد است؛ از پنجره یا فلاش مستقیم پرهیز کنید" }
        else if brightness < 0.30 { lightMessage = "کمی روشن‌تر شود بهتر است" }
        else if brightness > 0.85 { lightMessage = "کمی تاریک‌تر بهتر است" }
        checks.append(Check(ok: lightOk, blocking: true, message: lightMessage))

        // ---- sharpness -----------------------------------------------------
        let sharpLimit: Float = mode == .strict ? 0.012 : 0.006
        let warnLimit: Float = mode == .strict ? 0.022 : 0.010
        let sharp = state.sharpness
        var sharpMessage = "تصویر شارپ است"
        if sharp < sharpLimit { sharpMessage = "تصویر تار است؛ گوشی را ثابت نگه دارید" }
        else if sharp < warnLimit { sharpMessage = "کمی تاری دیده می‌شود" }
        checks.append(Check(ok: sharp >= sharpLimit, blocking: true, message: sharpMessage))

        // ---- eyes ----------------------------------------------------------
        checks.append(Check(
            ok: state.eyesOpen, blocking: false,
            message: state.eyesOpen ? "چشم‌ها باز است" : "چشم‌ها را باز نگه دارید"
        ))

        let blocking = checks.contains { !$0.ok && $0.blocking }
        return Evaluation(checks: checks, ready: !blocking, inTolerance: inTolerance)
    }

    private static func yawInstruction(target: Float, current: Float) -> String {
        let need = target - current
        let direction = need > 0 ? "راست" : "چپ"
        return "سر را به سمت \(direction) بچرخانید (\(Int(abs(need)))° مانده)"
    }

    private static func pitchInstruction(target: Float, current: Float) -> String {
        let need = target - current
        let direction = need > 0 ? "بالا" : "پایین"
        return "چانه را \(direction) ببرید (\(Int(abs(need)))° مانده)"
    }
}
