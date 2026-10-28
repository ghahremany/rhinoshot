import Foundation

/// Framing target for a shot.
enum Framing: String, Codable {
    case face
    case nose
}

/// One of the nine standard views of the clinic photography form.
///
/// Angles are *subject relative*:
///   yaw   > 0  -> the patient turns to their own RIGHT
///   pitch > 0  -> the chin goes UP
///
/// The camera-space values produced by MediaPipe are converted to this
/// convention by `Calibration`.
struct Shot: Identifiable {
    let id: String
    let fileName: String
    let title: String
    let hint: String
    let yaw: Float?
    let pitch: Float?
    var tolYaw: Float = 9
    var tolPitch: Float = 9
    var needSmile: Bool = false
    /// When true the operator triggers the shutter manually (nose close-up).
    var manual: Bool = false
    var framing: Framing = .face
    /// Extra label written into the manifest, e.g. "سمت راست بیمار".
    var side: String = ""
}

let CLINIC_SHOTS: [Shot] = [
    Shot(
        id: "frontal",
        fileName: "01_frontal",
        title: "روبروی کامل",
        hint: "چهره را مستقیم رو به دوربین نگه دارید، حالت خنثی، لب‌ها بسته",
        yaw: 0, pitch: 0
    ),
    Shot(
        id: "frontal_smile",
        fileName: "02_frontal_smile",
        title: "روبروی خندان",
        hint: "رو به دوربین و لبخند طبیعی بزنید",
        yaw: 0, pitch: 0, needSmile: true
    ),
    Shot(
        id: "profile_right",
        fileName: "03_profile_right",
        title: "نیم‌رخ راست",
        hint: "سر را کامل به سمت راست خودتان بچرخانید",
        yaw: 75, pitch: 0, tolYaw: 12,
        side: "نیم‌رخ راست بیمار"
    ),
    Shot(
        id: "profile_left",
        fileName: "04_profile_left",
        title: "نیم‌رخ چپ",
        hint: "سر را کامل به سمت چپ خودتان بچرخانید",
        yaw: -75, pitch: 0, tolYaw: 12,
        side: "نیم‌رخ چپ بیمار"
    ),
    Shot(
        id: "threequarter_right",
        fileName: "05_threequarter_right",
        title: "سه‌رخ راست",
        hint: "حدود ۴۵ درجه به سمت راست خودتان بچرخانید",
        yaw: 45, pitch: 0,
        side: "سه‌رخ راست بیمار"
    ),
    Shot(
        id: "threequarter_left",
        fileName: "06_threequarter_left",
        title: "سه‌رخ چپ",
        hint: "حدود ۴۵ درجه به سمت چپ خودتان بچرخانید",
        yaw: -45, pitch: 0,
        side: "سه‌رخ چپ بیمار"
    ),
    Shot(
        id: "head_up",
        fileName: "07_head_up",
        title: "سر بالا",
        hint: "چانه را بالا ببرید تا قاعده‌ی بینی دیده شود",
        yaw: 0, pitch: 30,
        side: "نمای از پایین (چانه بالا)"
    ),
    Shot(
        id: "head_down",
        fileName: "08_head_down",
        title: "سر پایین",
        hint: "سر را پایین بیاورید تا پشت بینی از بالا دیده شود",
        yaw: 0, pitch: -30,
        side: "نمای از بالا (چانه پایین)"
    ),
    Shot(
        id: "nose_closeup",
        fileName: "09_nose_closeup",
        title: "کلوزآپ بینی",
        hint: "دوربین را تا حد ممکن به بینی نزدیک کنید و دکمه‌ی «ثبت عکس» را بزنید",
        yaw: 0, pitch: 0, tolYaw: 14, tolPitch: 14,
        manual: true, framing: .nose,
        side: "نمای نزدیک بینی"
    ),
]

/// Print / export sizes offered to the clinic.
enum PrintSize: String, CaseIterable, Codable {
    case raw = "raw"
    case p10x15 = "10x15"
    case p13x18 = "13x18"

    var label: String {
        switch self {
        case .raw: return "خام (بیشترین کیفیت)"
        case .p10x15: return "۱۰ × ۱۵ سانتی‌متر"
        case .p13x18: return "۱۳ × ۱۸ سانتی‌متر"
        }
    }

    /// Target pixel size at 300 dpi (nil = keep the original).
    var pixelSize: (width: Int, height: Int)? {
        switch self {
        case .raw: return nil
        case .p10x15: return (1181, 1772)
        case .p13x18: return (1535, 2126)
        }
    }
}
