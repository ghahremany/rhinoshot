import SwiftUI

/// The About screen: app information, the developer credits and the licenses
/// of the third party components.
struct AboutView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Button {
                        app.backToHome()
                    } label: {
                        Text("بازگشت").font(.caption)
                    }
                    .buttonStyle(.bordered)
                    .tint(.white)

                    Spacer()

                    Text("درباره‌ی اپ")
                        .font(.title2.bold())
                        .foregroundStyle(.white)
                }

                PanelCard(title: "برنامه") {
                    VStack(alignment: .leading, spacing: 4) {
                        infoLine("نام", AppInfo.appName)
                        infoLine("نسخه", "\(AppInfo.appVersion) (کد \(AppInfo.versionCode))")
                        infoLine("شناسه‌ی بسته", AppInfo.bundleId)
                        infoLine("نسخه‌ی اندروید", "\(AppInfo.packageId)  •  \(AppInfo.appVersion)")
                        infoLine("حداقل iOS", AppInfo.minIOS)
                    }
                }

                PanelCard(title: "توسعه‌دهنده") {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(AppInfo.developerName)
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)
                        Text(AppInfo.developerRole)
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                        Text(AppInfo.developerApp)
                            .font(.caption)
                            .foregroundStyle(Theme.muted)

                        infoLine("اپ", AppInfo.developerAppPackage)
                        infoLine("تماس", AppInfo.developerPhone)
                        infoLine("وب‌سایت", AppInfo.developerSite)

                        Text("گیت‌هاب (پروژه):\n\(AppInfo.developerGitHub)")
                            .font(.caption)
                            .foregroundStyle(Theme.accent)
                        Text("گیت‌هاب (پروفایل):\n\(AppInfo.developerProfile)")
                            .font(.caption)
                            .foregroundStyle(Theme.accent)
                        if !AppInfo.developerEmail.isEmpty {
                            Text(AppInfo.developerEmail)
                                .font(.caption)
                                .foregroundStyle(Theme.accent)
                        }

                        HStack(spacing: 10) {
                            Button {
                                if let url = URL(string: AppInfo.developerGitHub) { openURL(url) }
                            } label: {
                                Text("مشاهده در گیت‌هاب")
                            }
                            .buttonStyle(.bordered)
                            .tint(Theme.accent)

                            Button {
                                if let url = URL(string: AppInfo.developerSite) { openURL(url) }
                            } label: {
                                Text("وب‌سایت مرجع")
                            }
                            .buttonStyle(.bordered)
                            .tint(Theme.accent)
                        }
                    }
                }

                PanelCard(title: "قابلیت‌ها") {
                    Text(AppInfo.features)
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                }

                PanelCard(title: "حریم خصوصی") {
                    Text(AppInfo.privacyNote)
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                }

                PanelCard(title: "هشدار پزشکی") {
                    Text(AppInfo.medicalNote)
                        .font(.caption)
                        .foregroundStyle(Theme.warn)
                }

                PanelCard(title: "پروانه‌ها و منابع") {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(AppInfo.licenses, id: \.name) { license in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(license.name)
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                                Text(license.note)
                                    .font(.caption2)
                                    .foregroundStyle(Theme.muted)
                                Text(license.url)
                                    .font(.caption2)
                                    .foregroundStyle(Theme.accent)
                            }
                        }
                    }
                }

                Text("\(AppInfo.appName) \(AppInfo.appVersion) — ساخته‌شده برای عکاسی استاندارد کلینیک جراحی بینی")
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity)
            }
            .padding(16)
        }
        .background(Theme.background)
    }

    private func infoLine(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption)
                .foregroundStyle(Theme.muted)
                .frame(width: 120, alignment: .leading)
            Text(value)
                .font(.caption)
                .foregroundStyle(.white)
        }
    }
}
