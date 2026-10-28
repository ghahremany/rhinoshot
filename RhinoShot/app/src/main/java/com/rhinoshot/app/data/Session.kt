package com.rhinoshot.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PatientInfo(
    val code: String,
    val name: String = "",
    val note: String = "",
    val consentAt: Long = System.currentTimeMillis(),
)

/** One captured photo plus the measurements that were valid at shutter time. */
data class ShotRecord(
    val shotId: String,
    val fileName: String,
    val title: String,
    val side: String,
    val capturedAt: Long,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val smile: Float,
    val brightness: Float,
    val sharpness: Float,
    val faceWidth: Float,
    val faceHeight: Float,
    val auto: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shot", shotId)
        put("file", fileName)
        put("title", title)
        put("side", side)
        put("captured_at", iso(capturedAt))
        put("captured_at_epoch", capturedAt)
        put("yaw_deg", yaw.toDouble())
        put("pitch_deg", pitch.toDouble())
        put("roll_deg", roll.toDouble())
        put("smile", smile.toDouble())
        put("brightness", brightness.toDouble())
        put("sharpness", sharpness.toDouble())
        put("face_width_ratio", faceWidth.toDouble())
        put("face_height_ratio", faceHeight.toDouble())
        put("auto_captured", auto)
    }
}

/** A photography session = one patient, one folder, nine standard views. */
class Session(
    val dir: File,
    val patient: PatientInfo,
    val printSize: PrintSize,
    val createdAt: Long,
) {
    val records: MutableList<ShotRecord> = mutableListOf()

    /** shotId -> flattened [x, y, z] landmark triples used for the 3D model. */
    val landmarks: MutableMap<String, List<Float>> = mutableMapOf()

    /** shotId -> image height divided by image width. */
    val aspects: MutableMap<String, Float> = mutableMapOf()

    fun fileFor(shot: Shot): File = File(dir, "${shot.fileName}.jpg")

    fun recordFor(shotId: String): ShotRecord? = records.firstOrNull { it.shotId == shotId }

    fun capturedCount(): Int = records.size

    fun writeConsent(text: String) {
        File(dir, "consent.txt").writeText(text)
    }

    /**
     * The landmarks of every captured shot. They are kept in their own file so
     * that `session.json` stays readable for the clinic tools.
     */
    fun writeLandmarks() {
        val shots = JSONObject()
        landmarks.forEach { (shotId, points) ->
            shots.put(
                shotId,
                JSONObject().apply {
                    put("count", points.size / 3)
                    put("aspect", aspects[shotId] ?: 1f)
                    put("points", JSONArray().apply { points.forEach { put(it.toDouble()) } })
                },
            )
        }
        val root = JSONObject().apply {
            put("app", "RhinoShot")
            put("app_version", AppInfo.APP_VERSION)
            put("session_id", dir.name)
            put("shots", shots)
        }
        File(dir, "landmarks.json").writeText(root.toString())
    }

    fun loadLandmarks() {
        val file = File(dir, "landmarks.json")
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            val shots = root.optJSONObject("shots") ?: return
            shots.keys().forEach { shotId ->
                val entry = shots.optJSONObject(shotId) ?: return@forEach
                val array = entry.optJSONArray("points") ?: return@forEach
                val points = ArrayList<Float>(array.length())
                for (i in 0 until array.length()) points.add(array.getDouble(i).toFloat())
                landmarks[shotId] = points
                aspects[shotId] = entry.optDouble("aspect", 1.0).toFloat()
            }
        } catch (t: Throwable) {
            // A broken file must never stop the app.
        }
    }

    fun writeManifest() {
        val root = JSONObject().apply {
            put("app", "RhinoShot")
            put("app_version", AppInfo.APP_VERSION)
            put("session_id", dir.name)
            put("created_at", iso(createdAt))
            put("created_at_epoch", createdAt)
            put("patient_code", patient.code)
            put("patient_name", patient.name)
            put("note", patient.note)
            put("consent_at", iso(patient.consentAt))
            put("consent_at_epoch", patient.consentAt)
            put("print_size", printSize.id)
            put("shots_total", CLINIC_SHOTS.size)
            put("shots_captured", records.size)
            put("shots", JSONArray().apply { records.forEach { put(it.toJson()) } })
        }
        File(dir, "session.json").writeText(root.toString(2))
    }
}

data class SessionInfo(
    val dir: File,
    val patientCode: String,
    val patientName: String,
    val createdAt: Long,
    val printSize: String,
    val shotCount: Int,
)

internal fun iso(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(time))
