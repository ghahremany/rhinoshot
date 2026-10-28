package com.rhinoshot.app.data

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * All sessions live in the app-private external folder:
 *   Android/data/com.rhinoshot.app/files/Pictures/RhinoShot/<code>_<session-id>/
 * The folder never leaves the device unless the operator explicitly exports it.
 */
class SessionStore(private val root: File) {

    companion object {
        fun root(context: Context): File {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            return File(base, "RhinoShot")
        }

        fun formatDate(time: Long): String =
            java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(java.util.Date(time))
    }

    fun createSession(patient: PatientInfo, printSize: PrintSize): Session {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
        val safeCode = patient.code.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "patient" }
        val dir = File(root, "${safeCode}_$stamp")
        dir.mkdirs()
        val session = Session(dir, patient, printSize, System.currentTimeMillis())
        session.writeConsent(consentText(patient, printSize))
        session.writeManifest()
        return session
    }

    fun list(): List<SessionInfo> {
        val dirs = root.listFiles { f: File -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val manifest = runCatching { File(dir, "session.json").readText() }.getOrNull()
            val json = manifest?.let { runCatching { JSONObject(it) }.getOrNull() }
            val shots = dir.listFiles { f: File -> f.extension.equals("jpg", true) }?.size ?: 0
            SessionInfo(
                dir = dir,
                patientCode = json?.optString("patient_code") ?: dir.name,
                patientName = json?.optString("patient_name") ?: "",
                createdAt = json?.optLong("created_at_epoch", dir.lastModified()) ?: dir.lastModified(),
                printSize = json?.optString("print_size") ?: PrintSize.RAW.id,
                shotCount = shots,
            )
        }.sortedByDescending { it.createdAt }
    }

    fun loadSession(info: SessionInfo): Session {
        val manifest = runCatching { File(info.dir, "session.json").readText() }.getOrNull()
        val json = manifest?.let { runCatching { JSONObject(it) }.getOrNull() }
        val patient = PatientInfo(
            code = json?.optString("patient_code") ?: info.patientCode,
            name = json?.optString("patient_name") ?: "",
            note = json?.optString("note") ?: "",
            consentAt = json?.optLong("consent_at", info.createdAt) ?: info.createdAt,
        )
        val session = Session(
            dir = info.dir,
            patient = patient,
            printSize = PrintSize.from(json?.optString("print_size")),
            createdAt = info.createdAt,
        )
        val arr = json?.optJSONArray("shots")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                session.records.add(
                    ShotRecord(
                        shotId = o.optString("shot"),
                        fileName = o.optString("file"),
                        title = o.optString("title"),
                        side = o.optString("side"),
                        capturedAt = o.optLong("captured_at_epoch", info.createdAt),
                        yaw = o.optDouble("yaw_deg", 0.0).toFloat(),
                        pitch = o.optDouble("pitch_deg", 0.0).toFloat(),
                        roll = o.optDouble("roll_deg", 0.0).toFloat(),
                        smile = o.optDouble("smile", 0.0).toFloat(),
                        brightness = o.optDouble("brightness", 0.0).toFloat(),
                        sharpness = o.optDouble("sharpness", 0.0).toFloat(),
                        faceWidth = o.optDouble("face_width_ratio", 0.0).toFloat(),
                        faceHeight = o.optDouble("face_height_ratio", 0.0).toFloat(),
                        auto = o.optBoolean("auto_captured", false),
                    )
                )
            }
        }
        // The landmarks are needed for the 3D model.
        session.loadLandmarks()
        return session
    }

    /** Privacy requirement: a session can be erased completely from the device. */
    fun delete(dir: File) {
        dir.deleteRecursively()
    }

    private fun consentText(patient: PatientInfo, printSize: PrintSize): String = buildString {
        appendLine("RhinoShot - رضایت‌نامه‌ی عکاسی پزشکی")
        appendLine("------------------------------------")
        appendLine("کد بیمار: ${patient.code}")
        appendLine("نام بیمار: ${patient.name.ifBlank { "-" }}")
        appendLine("تاریخ و ساعت: ${iso(System.currentTimeMillis())}")
        appendLine("نوع خروجی: ${printSize.label}")
        appendLine()
        appendLine("بیمار با آگاهی از اینکه تصاویر گرفته‌شده بخشی از پرونده‌ی پزشکی اوست،")
        appendLine("با ثبت این عکس‌ها موافقت می‌کند. تصاویر فقط روی همین دستگاه ذخیره")
        appendLine("می‌شوند و انتقال آن‌ها به سرور یا اشتراک با دیگران، تنها با اقدام صریح")
        appendLine("و جداگانه‌ی اپراتور انجام می‌شود. بیمار می‌تواند هر زمان درخواست حذف")
        appendLine("کامل تصاویر را بدهد.")
        appendLine()
        appendLine("امضای بیمار: ............................")
    }
}
