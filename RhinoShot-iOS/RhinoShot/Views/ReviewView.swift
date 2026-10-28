import SwiftUI

struct ReviewView: View {
    @EnvironmentObject var app: AppState
    @State private var shareItems: [Any] = []
    @State private var showShare = false
    @State private var exportMessage: String?
    @State private var busy = false

    private var session: Session? { app.session }

    var body: some View {
        ScrollView {
            if let session = session {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(session.patient.code)
                                .font(.title2.bold())
                                .foregroundStyle(.white)
                            Text("\(session.capturedCount) از \(CLINIC_SHOTS.count) نما ثبت شده")
                                .font(.caption)
                                .foregroundStyle(Theme.muted)
                        }
                        Spacer()
                        Button {
                            app.returnToCapture()
                        } label: {
                            Label("ادامه‌ی عکاسی", systemImage: "camera")
                        }
                        .buttonStyle(.bordered)
                        .tint(Theme.accent)
                    }

                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: 3), spacing: 8) {
                        ForEach(CLINIC_SHOTS) { shot in
                            VStack(spacing: 4) {
                                if let image = Exporter.loadImage(at: session.fileURL(for: shot), maxSize: 400) {
                                    Image(uiImage: image)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(height: 110)
                                        .clipped()
                                        .cornerRadius(8)
                                } else {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(Color.white.opacity(0.07))
                                        .frame(height: 110)
                                        .overlay(
                                            Image(systemName: "photo")
                                                .foregroundStyle(Theme.muted)
                                        )
                                }
                                Text(shot.title)
                                    .font(.caption2)
                                    .foregroundStyle(session.record(for: shot.id) == nil ? Theme.muted : .white)
                                if let record = session.record(for: shot.id) {
                                    Text("yaw \(Int(record.yawDeg))° • pitch \(Int(record.pitchDeg))°")
                                        .font(.system(size: 9))
                                        .foregroundStyle(Theme.muted)
                                }
                            }
                        }
                    }

                    PanelCard(title: "خروجی") {
                        VStack(alignment: .leading, spacing: 8) {
                            Button {
                                exportContactSheet(session)
                            } label: {
                                Label("ساخت برگه‌ی تماس (۹ عکس در یک صفحه)", systemImage: "square.grid.3x3")
                            }
                            .buttonStyle(.bordered)
                            .tint(Theme.accent)

                            if session.printSize != .raw {
                                Button {
                                    exportPrint(session)
                                } label: {
                                    Label("ساخت نسخه‌ی چاپی \(session.printSize.label)", systemImage: "printer")
                                }
                                .buttonStyle(.bordered)
                                .tint(Theme.accent)
                            }

                            Button {
                                exportToPhotos(session)
                            } label: {
                                Label("انتقال به گالری گوشی", systemImage: "photo.badge.arrow.down")
                            }
                            .buttonStyle(.bordered)
                            .tint(Theme.accent)

                            Button {
                                shareSession(session)
                            } label: {
                                Label("اشتراک‌گذاری (AirDrop / پیام)", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(.bordered)
                            .tint(Theme.accent)

                            if let exportMessage = exportMessage {
                                Text(exportMessage)
                                    .font(.caption)
                                    .foregroundStyle(Theme.ok)
                            }
                        }
                    }

                    PanelCard(title: "مدل سه‌بعدی") {
                        VStack(alignment: .leading, spacing: 8) {
                            Button {
                                app.openThreeD()
                            } label: {
                                Label("نمایش سه‌بعدی چهره", systemImage: "cube.transparent")
                            }
                            .buttonStyle(.bordered)
                            .tint(Theme.accent)

                            Text("مدل از روی همان عکس‌ها ساخته می‌شود و با چرخاندن صفحه از هر جهت قابل مشاهده است. فقط برای مشاهده و گفت‌وگو است و جایگزین اندازه‌گیری جراحی نیست.")
                                .font(.caption2)
                                .foregroundStyle(Theme.warn)
                        }
                    }

                    PanelCard(title: "حریم خصوصی") {
                        Text("همه‌ی فایل‌ها در حافظه‌ی خصوصی برنامه هستند و از پشتیبان‌گیری ابری کنار گذاشته شده‌اند. با «حذف کامل»، همه‌ی عکس‌ها و متادیتا برای همیشه پاک می‌شوند.")
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                        Button(role: .destructive) {
                            app.deleteCurrentSession()
                        } label: {
                            Label("حذف کامل این جلسه", systemImage: "trash")
                        }
                        .buttonStyle(.bordered)
                    }

                    Button {
                        app.backToHome()
                    } label: {
                        Text("بازگشت به خانه")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.accent)
                }
                .padding(16)
            }
        }
        .background(Theme.background)
        .sheet(isPresented: $showShare) {
            ShareSheet(items: shareItems)
        }
        .onAppear { app.refreshSessions() }
    }

    // MARK: - Export actions

    private func exportContactSheet(_ session: Session) {
        if let url = Exporter.buildContactSheet(session: session) {
            exportMessage = "برگه‌ی تماس ساخته شد."
            shareItems = [url]
            showShare = true
        } else {
            exportMessage = "ساخت برگه‌ی تماس ناموفق بود."
        }
    }

    private func exportPrint(_ session: Session) {
        let files = Exporter.buildPrintSized(session: session, printSize: session.printSize)
        exportMessage = files.isEmpty ? "نسخه‌ی چاپی ساخته نشد." : "\(files.count) فایل چاپی ساخته شد."
        if !files.isEmpty {
            shareItems = files
            showShare = true
        }
    }

    private func exportToPhotos(_ session: Session) {
        busy = true
        var files = CLINIC_SHOTS.map { session.fileURL(for: $0) }
        if let sheet = Exporter.buildContactSheet(session: session) { files.append(sheet) }
        Exporter.exportToPhotos(files: files) { count in
            busy = false
            exportMessage = count > 0 ? "\(count) فایل به گالری منتقل شد." : "انتقال به گالری انجام نشد (دسترسی داده نشد)."
        }
    }

    private func shareSession(_ session: Session) {
        var files = CLINIC_SHOTS.compactMap { url -> URL? in
            FileManager.default.fileExists(atPath: url.path) ? url : nil
        }
        files.append(session.directory.appendingPathComponent("session.json"))
        shareItems = files
        showShare = true
    }
}
