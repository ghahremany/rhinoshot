import Foundation

/// All the information the "About" screen shows.
///
/// The developer block lives in one place so it can be updated without
/// touching the UI code.
enum AppInfo {

    static let appName = "RhinoShot"
    static let appVersion = "0.3.1"
    static let versionCode = 4
    static let bundleId = "ir.rhinoshot.clinic"
    static let packageId = "com.rhinoshot.app"
    static let minAndroid = "8.0 (API 26)"
    static let minIOS = "16.0"

    // ---------------------------------------------------------------- developer
    // The developer block is taken from the public GitHub repository of the
    // "بامین تهویه" app — the Persian, fully offline HVAC quick-estimation
    // app (28 calculators) whose menu style RhinoShot follows.
    static let developerName = "محمد جواد قهرمانی"
    static let developerRole = "توسعه‌دهنده‌ی اپ «بامین تهویه» — سامانه‌ی محاسبات سرانگشتی تاسیسات"
    static let developerApp = "بامین تهویه — ۲۸ ماشین‌حساب تاسیسات، کاملاً آفلاین و فارسی"
    static let developerAppPackage = "com.bamintahvie.calculation"
    static let developerGitHub = "https://github.com/ghahremany/bamin-tahvie-app"
    static let developerProfile = "https://github.com/ghahremany"
    static let developerSite = "https://calculation.bamintahvie.com/"
    static let developerPhone = "۰۹۱۲۷۲۸۵۰۶۵"
    static let developerEmail = ""

    struct LicenseItem {
        let name: String
        let url: String
        let note: String
    }

    static let licenses: [LicenseItem] = [
        LicenseItem(
            name: "MediaPipe Tasks Vision",
            url: "https://github.com/google-ai-edge/mediapipe",
            note: "Apache License 2.0 — تشخیص چهره و مدل Face Landmarker"
        ),
        LicenseItem(
            name: "Face Landmarker model (float16/1)",
            url: "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task",
            note: "مدل تشخیص چهره — به‌صورت محلی روی دستگاه اجرا می‌شود"
        ),
        LicenseItem(
            name: "Canonical Face Model",
            url: "https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/modules/face_geometry/data/canonical_face_model.obj",
            note: "Apache License 2.0 — توپولوژی مش برای ساخت مدل سه‌بعدی"
        ),
        LicenseItem(
            name: "SceneKit & SwiftUI",
            url: "https://developer.apple.com/documentation/scenekit",
            note: "نمایشگر سه‌بعدی مدل چهره"
        )
    ]

    static let privacyNote =
        "این اپ هیچ دسترسی شبکه‌ای ندارد. تصاویر و مدل سه‌بعدی فقط روی همین دستگاه " +
        "ذخیره می‌شوند و تنها با اقدام صریح اپراتور منتقل می‌شوند."

    static let medicalNote =
        "مدل سه‌بعدی، بازسازی تقریبی از عکس‌های گرفته‌شده است و برای " +
        "اندازه‌گیری دقیق جراحی جایگزین عکس‌برداری استاندارد یا سنسور عمق نیست."

    static let features =
        "• عکاسی هدایت‌شده‌ی ۹ نما با کنترل زاویه، فاصله، نور و تاری\n" +
        "• شاتر خودکار وقتی وضعیت درست ۰٫۹ ثانیه حفظ شود\n" +
        "• کالیبراسیون جهت‌ها برای هر گوشی (یک‌بار)\n" +
        "• بازسازی سه‌بعدی چهره و چرخش دستی برای بررسی بینی\n" +
        "• خروجی چاپی ۱۰×۱۵ و ۱۳×۱۸ در ۳۰۰ dpi\n" +
        "• ذخیره‌ی خصوصی، بدون دسترسی شبکه، با حذف کامل"
}
