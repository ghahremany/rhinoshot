package com.rhinoshot.app.data

/**
 * The 9 standard views of the clinic photography form.
 *
 * Angles are *subject relative*:
 *   yaw   > 0  -> the patient turns to their own RIGHT
 *   yaw   < 0  -> the patient turns to their own LEFT
 *   pitch > 0  -> the chin goes UP
 *   pitch < 0  -> the chin goes DOWN
 *
 * The camera-space values produced by MediaPipe are converted to this
 * convention by [com.rhinoshot.app.vision.Calibration].
 */
enum class Framing { FACE, NOSE }

data class Shot(
    val id: String,
    val fileName: String,
    val title: String,
    val hint: String,
    val yaw: Float? = null,
    val pitch: Float? = null,
    val tolYaw: Float = 9f,
    val tolPitch: Float = 9f,
    val needSmile: Boolean = false,
    /** When true the operator triggers the shutter manually (nose close-up). */
    val manual: Boolean = false,
    val framing: Framing = Framing.FACE,
    /** Extra label written into the manifest, e.g. "سمت راست بیمار". */
    val side: String = "",
)

val CLINIC_SHOTS: List<Shot> = listOf(
    Shot(
        id = "frontal",
        fileName = "01_frontal",
        title = "روبروی کامل",
        hint = "چهره را مستقیم رو به دوربین نگه دارید، حالت خنثی، لب‌ها بسته",
        yaw = 0f, pitch = 0f,
    ),
    Shot(
        id = "frontal_smile",
        fileName = "02_frontal_smile",
        title = "روبروی خندان",
        hint = "رو به دوربین و لبخند طبیعی بزنید",
        yaw = 0f, pitch = 0f, needSmile = true,
    ),
    Shot(
        id = "profile_right",
        fileName = "03_profile_right",
        title = "نیم‌رخ راست",
        hint = "سر را کامل به سمت راست خودتان بچرخانید",
        yaw = 75f, pitch = 0f, tolYaw = 12f,
        side = "نیم‌رخ راست بیمار",
    ),
    Shot(
        id = "profile_left",
        fileName = "04_profile_left",
        title = "نیم‌رخ چپ",
        hint = "سر را کامل به سمت چپ خودتان بچرخانید",
        yaw = -75f, pitch = 0f, tolYaw = 12f,
        side = "نیم‌رخ چپ بیمار",
    ),
    Shot(
        id = "threequarter_right",
        fileName = "05_threequarter_right",
        title = "سه‌رخ راست",
        hint = "حدود ۴۵ درجه به سمت راست خودتان بچرخانید",
        yaw = 45f, pitch = 0f,
        side = "سه‌رخ راست بیمار",
    ),
    Shot(
        id = "threequarter_left",
        fileName = "06_threequarter_left",
        title = "سه‌رخ چپ",
        hint = "حدود ۴۵ درجه به سمت چپ خودتان بچرخانید",
        yaw = -45f, pitch = 0f,
        side = "سه‌رخ چپ بیمار",
    ),
    Shot(
        id = "head_up",
        fileName = "07_head_up",
        title = "سر بالا",
        hint = "چانه را بالا ببرید تا قاعده‌ی بینی دیده شود",
        yaw = 0f, pitch = 30f,
        side = "نمای از پایین (چانه بالا)",
    ),
    Shot(
        id = "head_down",
        fileName = "08_head_down",
        title = "سر پایین",
        hint = "سر را پایین بیاورید تا پشت بینی از بالا دیده شود",
        yaw = 0f, pitch = -30f,
        side = "نمای از بالا (چانه پایین)",
    ),
    Shot(
        id = "nose_closeup",
        fileName = "09_nose_closeup",
        title = "کلوزآپ بینی",
        hint = "دوربین را تا حد ممکن به بینی نزدیک کنید و دکمه‌ی «ثبت عکس» را بزنید",
        yaw = 0f, pitch = 0f, tolYaw = 14f, tolPitch = 14f,
        manual = true, framing = Framing.NOSE,
        side = "نمای نزدیک بینی",
    ),
)

/** Print / export sizes offered to the clinic. */
enum class PrintSize(val id: String, val label: String, val widthPx: Int, val heightPx: Int) {
    RAW("raw", "خام (بیشترین کیفیت)", 0, 0),
    P10x15("10x15", "۱۰ × ۱۵ سانتی‌متر", 1181, 1772),
    P13x18("13x18", "۱۳ × ۱۸ سانتی‌متر", 1535, 2126);

    companion object {
        fun from(id: String?): PrintSize = entries.firstOrNull { it.id == id } ?: RAW
    }
}
