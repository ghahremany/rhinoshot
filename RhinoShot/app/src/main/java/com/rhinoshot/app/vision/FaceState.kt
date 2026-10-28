package com.rhinoshot.app.vision

import android.content.Context
import android.graphics.RectF

/** Everything the app knows about the face in the current camera frame. */
data class FaceState(
    val detected: Boolean = false,
    /** Raw camera-space angles (degrees) coming from the MediaPipe transformation matrix. */
    val yawRaw: Float = 0f,
    val pitchRaw: Float = 0f,
    val rollRaw: Float = 0f,
    /** 0..1 */
    val smile: Float = 0f,
    val eyesOpen: Boolean = true,
    /** Face bounding box in normalised image coordinates (0..1). */
    val faceBox: RectF? = null,
    /** Face width / frame width. */
    val faceWidth: Float = 0f,
    /** Face height / frame height. */
    val faceHeight: Float = 0f,
    /** Nose tip position in normalised image coordinates. */
    val noseX: Float = 0.5f,
    val noseY: Float = 0.5f,
    /** Mean luminance of the face crop, 0..1. */
    val brightness: Float = 0f,
    /** Tenengrad sharpness of the face crop, normalised by brightness. */
    val sharpness: Float = 0f,
    /**
     * Flattened [x, y, z] triples of the detected landmarks (MediaPipe returns
     * 478 points). They are used to rebuild the 3D model of the face.
     */
    val landmarks: List<Float> = emptyList(),
    /** The 4x4 facial transformation matrix (column-major, 16 values). */
    val transform: List<Float> = emptyList(),
    /** Image height divided by image width - needed to undo the image aspect. */
    val aspect: Float = 1f,
    val error: String? = null,
    val ts: Long = 0L,
)

/**
 * Converts the raw camera-space pose into the subject-relative convention used by
 * the clinic form, and removes the personal offset of every patient's head.
 *
 * The signs depend on the device and on the camera that is used, therefore they
 * can be calibrated once per phone (see [com.rhinoshot.app.ui.CalibrationDialog]).
 */
data class Calibration(
    val yawSignFront: Int = -1,
    val yawSignBack: Int = 1,
    val pitchSign: Int = 1,
    val yawOffset: Float = 0f,
    val pitchOffset: Float = 0f,
) {
    fun yaw(raw: Float, front: Boolean): Float =
        (if (front) yawSignFront else yawSignBack) * raw + yawOffset

    fun pitch(raw: Float): Float = pitchSign * raw + pitchOffset

    /** Re-centres the current pose on zero (patient looking straight at the camera). */
    fun withZero(rawYaw: Float, rawPitch: Float, front: Boolean): Calibration = copy(
        yawOffset = -(if (front) yawSignFront else yawSignBack) * rawYaw,
        pitchOffset = -pitchSign * rawPitch,
    )

    fun withYawSign(front: Boolean, sign: Int): Calibration =
        if (front) copy(yawSignFront = sign) else copy(yawSignBack = sign)

    companion object {
        private const val PREFS = "rhinoshot_calibration"

        fun load(context: Context): Calibration {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return Calibration(
                yawSignFront = p.getInt("yaw_front", -1),
                yawSignBack = p.getInt("yaw_back", 1),
                pitchSign = p.getInt("pitch", 1),
                yawOffset = p.getFloat("yaw_offset", 0f),
                pitchOffset = p.getFloat("pitch_offset", 0f),
            )
        }

        fun save(context: Context, c: Calibration) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("yaw_front", c.yawSignFront)
                .putInt("yaw_back", c.yawSignBack)
                .putInt("pitch", c.pitchSign)
                .putFloat("yaw_offset", c.yawOffset)
                .putFloat("pitch_offset", c.pitchOffset)
                .apply()
        }
    }
}
