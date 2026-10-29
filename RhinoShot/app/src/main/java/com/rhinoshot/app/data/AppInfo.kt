package com.rhinoshot.app.data

/**
 * All the information the "About" screen shows.
 *
 * The developer block is kept in one place so it can be updated without
 * touching the UI code.
 */
object AppInfo {

    const val APP_NAME = "RhinoShot"
    const val APP_VERSION = "0.3.3"
    const val VERSION_CODE = 6
    const val PACKAGE = "com.rhinoshot.app"
    const val IOS_BUNDLE_ID = "ir.rhinoshot.clinic"
    const val MIN_ANDROID = "8.0 (API 26)"

    // ---------------------------------------------------------------- developer
    // The developer block is taken from the public GitHub repository of the
    // "بامین تهویه" app — the Persian, fully offline HVAC quick-estimation
    // app (28 calculators) whose menu style RhinoShot follows.
    const val DEVELOPER_NAME = "محمد جواد قهرمانی"
    const val DEVELOPER_ROLE = "توسعه‌دهنده‌ی اپ «بامین تهویه» — سامانه‌ی محاسبات سرانگشتی تاسیسات"
    const val DEVELOPER_APP = "بامین تهویه — ۲۸ ماشین‌حساب تاسیسات، کاملاً آفلاین و فارسی"
    const val DEVELOPER_APP_PACKAGE = "com.bamintahvie.calculation"
    const val DEVELOPER_GITHUB = "https://github.com/ghahremany/bamin-tahvie-app"
    const val DEVELOPER_PROFILE = "https://github.com/ghahremany"
    const val DEVELOPER_SITE = "https://calculation.bamintahvie.com/"
    const val DEVELOPER_PHONE = "۰۹۱۲۷۲۸۵۰۶۵"
    const val DEVELOPER_EMAIL = ""
    const val SOURCE_URL = "https://github.com/google-ai-edge/mediapipe"

    data class License(val name: String, val url: String, val note: String)

    val LICENSES = listOf(
        License(
            name = "MediaPipe Tasks Vision",
            url = "https://github.com/google-ai-edge/mediapipe",
            note = "Apache License 2.0 — تشخیص چهره و مدل Face Landmarker",
        ),
        License(
            name = "Face Landmarker model (float16/1)",
            url = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
            note = "مدل تشخیص چهره — به‌صورت محلی روی دستگاه اجرا می‌شود",
        ),
        License(
            name = "Canonical Face Model",
            url = "https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/modules/face_geometry/data/canonical_face_model.obj",
            note = "Apache License 2.0 — توپولوژی مش برای ساخت مدل سه‌بعدی",
        ),
        License(
            name = "Jetpack Compose & AndroidX",
            url = "https://developer.android.com/jetpack",
            note = "Apache License 2.0",
        ),
    )

    const val PRIVACY_NOTE =
        "این اپ هیچ دسترسی شبکه‌ای ندارد. تصاویر و مدل سه‌بعدی فقط روی همین دستگاه " +
            "ذخیره می‌شوند و تنها با اقدام صریح اپراتور منتقل می‌شوند."

    const val MEDICAL_NOTE =
        "مدل سه‌بعدی، بازسازی تقریبی از عکس‌های گرفته‌شده است و برای " +
            "اندازه‌گیری دقیق جراحی جایگزین عکس‌برداری استاندارد یا سنسور عمق نیست."
}
