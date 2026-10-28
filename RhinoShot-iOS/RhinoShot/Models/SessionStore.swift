import Foundation

/// All sessions live in the app-private Application Support folder and are
/// excluded from iCloud/iTunes backups (medical data must not leave the device
/// without an explicit action).
final class SessionStore {
    let root: URL

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        root = base.appendingPathComponent("RhinoShot", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        excludeFromBackup(root)
    }

    private func excludeFromBackup(_ url: URL) {
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var mutable = url
        try? mutable.setResourceValues(values)
    }

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyyMMdd_HHmmss"
        return f
    }()

    func createSession(patient: PatientInfo, printSize: PrintSize) -> Session {
        let stamp = SessionStore.formatter.string(from: Date())
        let safeCode = patient.code.replacingOccurrences(
            of: "[^A-Za-z0-9_-]",
            with: "_",
            options: .regularExpression
        )
        let code = safeCode.isEmpty ? "patient" : safeCode
        let directory = root.appendingPathComponent("\(code)_\(stamp)", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        excludeFromBackup(directory)
        let session = Session(directory: directory, patient: patient, printSize: printSize)
        session.writeConsent()
        session.writeManifest()
        return session
    }

    func list() -> [SessionInfo] {
        let dirs = (try? FileManager.default.contentsOfDirectory(
            at: root,
            includingPropertiesForKeys: [.creationDateKey],
            options: [.skipsHiddenFiles]
        )) ?? []
        return dirs
            .filter { (try? $0.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true }
            .compactMap { dir -> SessionInfo? in
                let manifestURL = dir.appendingPathComponent("session.json")
                var code = dir.lastPathComponent
                var name = ""
                var created = (try? dir.resourceValues(forKeys: [.creationDateKey]).creationDate) ?? Date()
                var size = PrintSize.raw
                if let data = try? Data(contentsOf: manifestURL),
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    code = json["patient_code"] as? String ?? code
                    name = json["patient_name"] as? String ?? ""
                    size = json["print_size"] as? String ?? PrintSize.raw.rawValue
                    if let iso = json["created_at"] as? String {
                        let isoFormatter = ISO8601DateFormatter()
                        if let d = isoFormatter.date(from: iso) { created = d }
                    }
                }
                let shots = ((try? FileManager.default.contentsOfDirectory(
                    at: dir,
                    includingPropertiesForKeys: nil
                )) ?? []).filter { $0.pathExtension.lowercased() == "jpg" }.count
                return SessionInfo(
                    directory: dir,
                    patientCode: code,
                    patientName: name,
                    createdAt: created,
                    printSize: PrintSize(rawValue: size) ?? .raw,
                    shotCount: shots
                )
            }
            .sorted { $0.createdAt > $1.createdAt }
    }

    func load(_ info: SessionInfo) -> Session {
        let session = Session(
            directory: info.directory,
            patient: PatientInfo(code: info.patientCode, name: info.patientName),
            printSize: info.printSize,
            createdAt: info.createdAt
        )
        let manifestURL = info.directory.appendingPathComponent("session.json")
        if let data = try? Data(contentsOf: manifestURL),
           let manifest = try? JSONDecoder.iso8601.decode(SessionManifest.self, from: data) {
            session.records = manifest.shots
        }
        session.loadLandmarks()
        return session
    }

    /// Privacy requirement: a session can be erased completely from the device.
    func delete(_ info: SessionInfo) {
        try? FileManager.default.removeItem(at: info.directory)
    }

    static func displayDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "fa_IR")
        formatter.dateFormat = "yyyy/MM/dd HH:mm"
        return formatter.string(from: date)
    }
}

extension JSONDecoder {
    static let iso8601: JSONDecoder = {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .iso8601
        return d
    }()
}
