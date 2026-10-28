package com.rhinoshot.app.vision

import com.rhinoshot.app.data.Framing
import com.rhinoshot.app.data.Shot
import kotlin.math.abs

/** How strict the image-quality gate is. */
enum class QualityMode { STRICT, RELAXED }

data class Check(val ok: Boolean, val blocking: Boolean, val message: String)

data class Evaluation(
    val checks: List<Check>,
    /** true when nothing blocks the shutter */
    val ready: Boolean,
    /** true when the head pose itself is inside the tolerance of the shot */
    val inTolerance: Boolean,
) {
    val issues: List<Check> get() = checks.filter { !it.ok }
    val blocking: List<Check> get() = issues.filter { it.blocking }
    val warnings: List<Check> get() = issues.filter { !it.blocking }
}

/**
 * Turns the raw face measurement into operator guidance in Persian.
 * Every check is either blocking (the shutter stays closed) or a warning.
 */
object PoseEvaluator {

    /** How long the pose has to be held before the automatic shutter fires. */
    const val HOLD_MS = 900L

    private const val MIN_FACE_WIDTH = 0.20f
    private const val MAX_FACE_WIDTH = 0.72f
    private const val MIN_FACE_HEIGHT = 0.22f
    private const val MAX_FACE_HEIGHT = 0.88f
    private const val CENTER_TOL_X = 0.14f
    private const val CENTER_TOL_Y = 0.16f
    private const val NOSE_MIN_FACE_HEIGHT = 0.42f
    private const val NOSE_CENTER_TOL = 0.20f

    fun noFace(): Evaluation = Evaluation(
        checks = listOf(Check(false, true, "چهره دیده نمی‌شود؛ قاب را روی صورت بیمار بگیرید")),
        ready = false,
        inTolerance = false,
    )

    fun evaluate(
        shot: Shot,
        state: FaceState,
        yaw: Float,
        pitch: Float,
        mode: QualityMode,
        toleranceScale: Float = 1f,
    ): Evaluation {
        val checks = mutableListOf<Check>()
        if (!state.detected) return noFace()

        var inTolerance = true

        // ---- pose ----------------------------------------------------------
        if (shot.yaw != null) {
            val tol = shot.tolYaw * toleranceScale
            val delta = yaw - shot.yaw
            val ok = abs(delta) <= tol
            inTolerance = inTolerance && ok
            checks += Check(
                ok, true,
                if (ok) "زاویه‌ی افقی درست است (${yaw.toInt()}°)"
                else yawInstruction(shot.yaw, yaw)
            )
        }
        if (shot.pitch != null) {
            val tol = shot.tolPitch * toleranceScale
            val delta = pitch - shot.pitch
            val ok = abs(delta) <= tol
            inTolerance = inTolerance && ok
            checks += Check(
                ok, true,
                if (ok) "زاویه‌ی عمودی درست است (${pitch.toInt()}°)"
                else pitchInstruction(shot.pitch, pitch)
            )
        }
        if (shot.needSmile) {
            val ok = state.smile >= 0.35f
            checks += Check(ok, true, if (ok) "لبخند ثبت شد" else "لبخند بزنید")
        }

        // ---- framing -------------------------------------------------------
        val box = state.faceBox
        if (shot.framing == Framing.NOSE) {
            val closeEnough = state.faceHeight >= NOSE_MIN_FACE_HEIGHT
            checks += Check(
                closeEnough, true,
                if (closeEnough) "فاصله‌ی دوربین مناسب است"
                else "دوربین را نزدیک‌تر بیاورید تا بینی پر کادر شود"
            )
            val noseCentered = abs(state.noseX - 0.5f) <= NOSE_CENTER_TOL
            checks += Check(
                noseCentered, true,
                if (noseCentered) "بینی در مرکز کادر است"
                else "بینی را در مرکز کادر قرار دهید"
            )
        } else {
            val widthOk = state.faceWidth in MIN_FACE_WIDTH..MAX_FACE_WIDTH &&
                state.faceHeight in MIN_FACE_HEIGHT..MAX_FACE_HEIGHT
            checks += Check(
                widthOk, true,
                when {
                    state.faceWidth < MIN_FACE_WIDTH || state.faceHeight < MIN_FACE_HEIGHT ->
                        "کمی نزدیک‌تر شوید"
                    state.faceWidth > MAX_FACE_WIDTH || state.faceHeight > MAX_FACE_HEIGHT ->
                        "کمی دورتر شوید"
                    else -> "فاصله‌ی دوربین مناسب است"
                }
            )
            val centered = box != null &&
                abs(box.centerX() - 0.5f) <= CENTER_TOL_X &&
                abs(box.centerY() - 0.45f) <= CENTER_TOL_Y
            checks += Check(
                centered, true,
                if (centered) "صورت در مرکز کادر است" else "صورت را در مرکز کادر قرار دهید"
            )
        }

        // ---- light ---------------------------------------------------------
        val brightness = state.brightness
        val tooDark = brightness < if (mode == QualityMode.STRICT) 0.18f else 0.12f
        val tooBright = brightness > if (mode == QualityMode.STRICT) 0.94f else 0.97f
        val lightOk = !tooDark && !tooBright
        checks += Check(
            lightOk, true,
            when {
                tooDark -> "نور محیط کم است؛ روشن‌تر کنید"
                tooBright -> "نور خیلی زیاد است؛ از پنجره یا فلاش مستقیم پرهیز کنید"
                brightness < 0.30f -> "کمی روشن‌تر شود بهتر است"
                brightness > 0.85f -> "کمی تاریک‌تر بهتر است"
                else -> "نور مناسب است"
            }
        )

        // ---- sharpness -----------------------------------------------------
        val sharpLimit = if (mode == QualityMode.STRICT) 0.012f else 0.006f
        val warnLimit = if (mode == QualityMode.STRICT) 0.022f else 0.010f
        val sharp = state.sharpness
        checks += Check(
            sharp >= sharpLimit, true,
            when {
                sharp < sharpLimit -> "تصویر تار است؛ گوشی را ثابت نگه دارید"
                sharp < warnLimit -> "کمی تاری دیده می‌شود"
                else -> "تصویر شارپ است"
            }
        )

        // ---- eyes ----------------------------------------------------------
        checks += Check(
            state.eyesOpen, false,
            if (state.eyesOpen) "چشم‌ها باز است" else "چشم‌ها را باز نگه دارید"
        )

        val blocking = checks.any { !it.ok && it.blocking }
        return Evaluation(checks, ready = !blocking, inTolerance = inTolerance)
    }

    private fun yawInstruction(target: Float, current: Float): String {
        val need = target - current
        val direction = if (need > 0) "راست" else "چپ"
        return "سر را به سمت $direction بچرخانید (${abs(need).toInt()}° مانده)"
    }

    private fun pitchInstruction(target: Float, current: Float): String {
        val need = target - current
        val direction = if (need > 0) "بالا" else "پایین"
        return "چانه را $direction ببرید (${abs(need).toInt()}° مانده)"
    }
}
