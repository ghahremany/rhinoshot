import SwiftUI
import Combine
import AVFoundation

@main
struct RhinoShotApp: App {
    @StateObject private var app = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(app)
                .environment(\.layoutDirection, .rightToLeft)
                .preferredColorScheme(.dark)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppState

    var body: some View {
        Group {
            switch app.stage {
            case .home:
                HomeView()
            case .capture:
                CaptureView(camera: app.camera)
            case .review:
                ReviewView()
            case .threeD:
                ThreeDView()
            case .about:
                AboutView()
            }
        }
        .sheet(isPresented: $app.showGuide) {
            GuideSheet()
                .environmentObject(app)
                .environment(\.layoutDirection, .rightToLeft)
                .preferredColorScheme(.dark)
        }
        .alert("حذف همه‌ی داده‌ها", isPresented: $app.showWipeConfirm) {
            Button("حذف کن", role: .destructive) { app.wipeAll() }
            Button("انصراف", role: .cancel) { app.showWipeConfirm = false }
        } message: {
            Text("همه‌ی عکس‌ها، مدل‌های سه‌بعدی و متادیتای همه‌ی جلسه‌ها برای همیشه پاک می‌شود. این عمل قابل بازگشت نیست.")
        }
    }
}

/// The short "how to use it" text, reachable from the hamburger menu.
struct GuideSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    guide("۱) کد بیمار و رضایت‌نامه را وارد کنید و «شروع عکاسی ۹ نما» را بزنید.")
                    guide("۲) بیمار را مقابل دوربین قرار دهید؛ اپ زاویه، فاصله، نور و تاری را کنترل می‌کند و وقتی وضعیت درست ۰٫۹ ثانیه بماند، خودکار عکس می‌گیرد.")
                    guide("۳) در نمای ۹ (کلوزآپ بینی) دکمه‌ی بزرگ وسط صفحه را بزنید.")
                    guide("۴) در صفحه‌ی بازبینی می‌توانید خروجی بگیرید یا از منوی همبرگری «نمایش سه‌بعدی چهره» را باز کنید و مدل را بچرخانید.")
                    guide("۵) برای هر گوشی یک‌بار «کالیبراسیون» را در صفحه‌ی عکاسی انجام دهید.")
                    guide("۶) مدل سه‌بعدی فقط برای مشاهده و گفت‌وگو است و جایگزین اندازه‌گیری جراحی نیست.")
                }
                .padding(16)
            }
            .background(Theme.background)
            .navigationTitle("راهنمای استفاده")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("بستن") { dismiss() }
                }
            }
        }
    }

    private func guide(_ text: String) -> some View {
        Text(text)
            .font(.callout)
            .foregroundStyle(Theme.muted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
    }
}

/// Application state: which screen we are on and which session is open.
final class AppState: ObservableObject {

    enum Stage {
        case home, capture, review, threeD, about
    }

    @Published var stage: Stage = .home
    @Published var session: Session?
    @Published var sessions: [SessionInfo] = []
    @Published var camera = CameraController()
    @Published var showGuide = false
    @Published var showWipeConfirm = false

    let store = SessionStore()

    // MARK: - Session flow

    func startSession(patient: PatientInfo, printSize: PrintSize) {
        let session = store.createSession(patient: patient, printSize: printSize)
        self.session = session
        stage = .capture
    }

    func finishCapture() {
        session?.writeManifest()
        session?.writeLandmarks()
        stage = .review
    }

    func cancelCapture() {
        stage = .home
    }

    func returnToCapture() {
        stage = .capture
    }

    func backToHome() {
        stage = .home
        refreshSessions()
    }

    func backToReview() {
        stage = .review
    }

    func openSession(_ info: SessionInfo) {
        session = store.load(info)
        stage = .review
    }

    /// Opens the 3D viewer. When no session is open yet the most recent one
    /// is used, so the menu entry always works.
    func openThreeD() {
        if let current = session {
            current.loadLandmarks()
            stage = .threeD
            return
        }
        guard let latest = sessions.first else { return }
        let loaded = store.load(latest)
        loaded.loadLandmarks()
        session = loaded
        stage = .threeD
    }

    func openAbout() {
        stage = .about
    }

    func openGuide() {
        showGuide = true
    }

    func requestWipeAll() {
        showWipeConfirm = true
    }

    /// Deletes every session — the complete "erase everything" requirement.
    func wipeAll() {
        showWipeConfirm = false
        for info in sessions {
            store.delete(info)
        }
        session = nil
        refreshSessions()
    }

    func deleteCurrentSession() {
        guard let session = session else { return }
        store.delete(SessionInfo(
            directory: session.directory,
            patientCode: session.patient.code,
            patientName: session.patient.name,
            createdAt: session.createdAt,
            printSize: session.printSize,
            shotCount: session.capturedCount
        ))
        self.session = nil
        stage = .home
        refreshSessions()
    }

    func deleteSession(_ info: SessionInfo) {
        store.delete(info)
        refreshSessions()
    }

    func refreshSessions() {
        sessions = store.list()
    }

    // MARK: - Camera permission

    func requestCameraAccess(_ completion: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            completion(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { completion(granted) }
            }
        default:
            completion(false)
        }
    }
}
