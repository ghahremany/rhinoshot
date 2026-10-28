import Foundation
import Combine
import simd

struct PatientInfo: Codable {
    var code: String
    var name: String = ""
    var note: String = ""
    var consentAt: Date = Date()
}

/// One captured photo plus the measurements that were valid at shutter time.
struct ShotRecord: Codable {
    let shot: String
    let file: String
    let title: String
    let side: String
    let capturedAt: Date
    let yawDeg: Double
    let pitchDeg: Double
    let rollDeg: Double
    let smile: Double
    let brightness: Double
    let sharpness: Double
    let faceWidthRatio: Double
    let faceHeightRatio: Double
    let autoCaptured: Bool

    enum CodingKeys: String, CodingKey {
        case shot, file, title, side
        case capturedAt = "captured_at"
        case yawDeg = "yaw_deg"
        case pitchDeg = "pitch_deg"
        case rollDeg = "roll_deg"
        case smile, brightness, sharpness
        case faceWidthRatio = "face_width_ratio"
        case faceHeightRatio = "face_height_ratio"
        case autoCaptured = "auto_captured"
    }
}

struct SessionManifest: Codable {
    let app: String = "RhinoShot-iOS"
    let appVersion: String = AppInfo.appVersion
    let sessionId: String
    let createdAt: Date
    let patientCode: String
    let patientName: String
    let note: String
    let consentAt: Date
    let printSize: String
    let shotsTotal: Int
    let shotsCaptured: Int
    let shots: [ShotRecord]

    enum CodingKeys: String, CodingKey {
        case app
        case appVersion = "app_version"
        case sessionId = "session_id"
        case createdAt = "created_at"
        case patientCode = "patient_code"
        case patientName = "patient_name"
        case note
        case consentAt = "consent_at"
        case printSize = "print_size"
        case shotsTotal = "shots_total"
        case shotsCaptured = "shots_captured"
        case shots
    }
}

/// A photography session = one patient, one folder, nine standard views.
final class Session: ObservableObject {
    let directory: URL
    let patient: PatientInfo
    let printSize: PrintSize
    let createdAt: Date

    @Published var records: [ShotRecord] = []

    /// shotId -> the landmarks (x, y, z) detected when the photo was taken.
    /// They are the input of the 3D reconstruction.
    @Published var landmarks: [String: [SIMD3<Float>]] = [:]

    /// shotId -> image height divided by image width.
    @Published var aspects: [String: Float] = [:]

    init(directory: URL, patient: PatientInfo, printSize: PrintSize, createdAt: Date = Date()) {
        self.directory = directory
        self.patient = patient
        self.printSize = printSize
        self.createdAt = createdAt
    }

    func fileURL(for shot: Shot) -> URL {
        directory.appendingPathComponent("\(shot.fileName).jpg")
    }

    func record(for shotId: String) -> ShotRecord? {
        records.first { $0.shot == shotId }
    }

    var capturedCount: Int { records.count }

    func writeConsent() {
        let text = """
        RhinoShot - رضایت‌نامه‌ی عکاسی پزشکی
        ------------------------------------
        کد بیمار: \(patient.code)
        نام بیمار: \(patient.name.isEmpty ? "-" : patient.name)
        تاریخ و ساعت: \(ISO8601DateFormatter().string(from: Date()))
        نوع خروجی: \(printSize.label)

        بیمار با آگاهی از اینکه تصاویر گرفته‌شده بخشی از پرونده‌ی پزشکی اوست،
        با ثبت این عکس‌ها موافقت می‌کند. تصاویر فقط روی همین دستگاه ذخیره
        می‌شوند و انتقال آن‌ها به سرور یا اشتراک با دیگران، تنها با اقدام صریح
        و جداگانه‌ی اپراتور انجام می‌شود. بیمار می‌تواند هر زمان درخواست حذف
        کامل تصاویر را بدهد.

        امضای بیمار: ............................
        """
        try? text.write(to: directory.appendingPathComponent("consent.txt"), atomically: true, encoding: .utf8)
    }

    /// Saves the landmarks that are needed to rebuild the 3D model.
    /// The format is identical to the one used by the Android version, so a
    /// session folder can be moved between the two apps.
    func writeLandmarks() {
        var shots: [String: Any] = [:]
        for (shotId, points) in landmarks {
            var flat: [Double] = []
            flat.reserveCapacity(points.count * 3)
            for p in points {
                flat.append(Double(p.x))
                flat.append(Double(p.y))
                flat.append(Double(p.z))
            }
            shots[shotId] = [
                "count": points.count,
                "aspect": Double(aspects[shotId] ?? 1),
                "points": flat
            ]
        }
        let root: [String: Any] = [
            "app": "RhinoShot-iOS",
            "app_version": AppInfo.appVersion,
            "session_id": directory.lastPathComponent,
            "shots": shots
        ]
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(root) {
            try? data.write(to: directory.appendingPathComponent("landmarks.json"), options: .atomic)
        }
    }

    /// Reads the landmarks back — a broken file must never stop the app.
    func loadLandmarks() {
        let url = directory.appendingPathComponent("landmarks.json")
        guard let data = try? Data(contentsOf: url),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let shots = root["shots"] as? [String: Any] else { return }
        for (shotId, value) in shots {
            guard let entry = value as? [String: Any],
                  let flat = entry["points"] as? [Double] else { continue }
            var points: [SIMD3<Float>] = []
            points.reserveCapacity(flat.count / 3)
            var i = 0
            while i + 2 < flat.count {
                points.append(SIMD3<Float>(Float(flat[i]), Float(flat[i + 1]), Float(flat[i + 2])))
                i += 3
            }
            landmarks[shotId] = points
            aspects[shotId] = Float((entry["aspect"] as? Double) ?? 1)
        }
    }

    func writeManifest() {
        let manifest = SessionManifest(
            sessionId: directory.lastPathComponent,
            createdAt: createdAt,
            patientCode: patient.code,
            patientName: patient.name,
            note: patient.note,
            consentAt: patient.consentAt,
            printSize: printSize.rawValue,
            shotsTotal: CLINIC_SHOTS.count,
            shotsCaptured: records.count,
            shots: records
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        if let data = try? encoder.encode(manifest) {
            try? data.write(to: directory.appendingPathComponent("session.json"), options: .atomic)
        }
    }
}

struct SessionInfo: Identifiable {
    let directory: URL
    let patientCode: String
    let patientName: String
    let createdAt: Date
    let printSize: PrintSize
    let shotCount: Int

    var id: String { directory.path }
}
