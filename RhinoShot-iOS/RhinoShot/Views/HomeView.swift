import SwiftUI

struct HomeView: View {
    @EnvironmentObject var app: AppState

    @State private var code: String = ""
    @State private var name: String = ""
    @State private var note: String = ""
    @State private var printSize: PrintSize = .raw
    @State private var consent: Bool = false
    @State private var cameraDenied: Bool = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("RhinoShot")
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(.white)
                        Text("عکاسی استاندارد ۹ نما برای پرونده‌ی جراحی بینی")
                            .font(.subheadline)
                            .foregroundStyle(Theme.muted)
                    }
                    Spacer()
                    Menu {
                        Button {
                            app.openThreeD()
                        } label: {
                            Label("نمایش سه‌بعدی چهره", systemImage: "cube.transparent")
                        }
                        Button {
                            app.openGuide()
                        } label: {
                            Label("راهنمای استفاده", systemImage: "questionmark.circle")
                        }
                        Button {
                            app.openAbout()
                        } label: {
                            Label("درباره‌ی اپ و توسعه‌دهنده", systemImage: "info.circle")
                        }
                        Divider()
                        Button(role: .destructive) {
                            app.requestWipeAll()
                        } label: {
                            Label("حذف همه‌ی داده‌ها", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "line.3.horizontal")
                            .font(.title3)
                            .foregroundStyle(.white)
                            .padding(8)
                            .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                    }
                }

                PanelCard(title: "مشخصات بیمار") {
                    VStack(spacing: 8) {
                        TextField("کد بیمار (اجباری)", text: $code)
                            .textFieldStyle(.roundedBorder)
                        TextField("نام بیمار (اختیاری)", text: $name)
                            .textFieldStyle(.roundedBorder)
                        TextField("توضیح (اختیاری)", text: $note)
                            .textFieldStyle(.roundedBorder)

                        Text("اندازه‌ی خروجی چاپ")
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        Picker("اندازه‌ی خروجی چاپ", selection: $printSize) {
                            ForEach(PrintSize.allCases, id: \.self) { size in
                                Text(size.label).tag(size)
                            }
                        }
                        .pickerStyle(.segmented)
                    }
                }

                PanelCard(title: "رضایت بیمار") {
                    Toggle(isOn: $consent) {
                        Text(
                            "بیمار از ثبت تصاویر چهره و نگهداری آن‌ها در پرونده‌ی پزشکی خود آگاه است " +
                            "و با این کار موافقت می‌کند. تصاویر فقط روی همین دستگاه نگهداری می‌شود."
                        )
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                    }
                    .tint(Theme.accent)
                }

                Button {
                    app.startSession(
                        patient: PatientInfo(code: code.trimmingCharacters(in: .whitespaces), name: name, note: note),
                        printSize: printSize
                    )
                } label: {
                    Text("شروع عکاسی ۹ نما")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .tint(Theme.accent)
                .disabled(code.trimmingCharacters(in: .whitespaces).isEmpty || !consent)

                if cameraDenied {
                    Text("برای عکاسی، دسترسی به دوربین لازم است. از تنظیمات گوشی آن را فعال کنید.")
                        .font(.caption)
                        .foregroundStyle(Theme.error)
                }

                PanelCard(title: "نماهای فرم کلینیک") {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(Array(CLINIC_SHOTS.enumerated()), id: \.offset) { index, shot in
                            HStack(spacing: 8) {
                                Text("\(index + 1)")
                                    .font(.caption.bold())
                                    .foregroundStyle(Theme.accent)
                                    .frame(width: 22, alignment: .leading)
                                Text(shot.title)
                                    .font(.caption)
                                    .foregroundStyle(.white)
                                Text(shot.side)
                                    .font(.caption2)
                                    .foregroundStyle(Theme.muted)
                            }
                        }
                    }
                }

                Text("جلسه‌های قبلی")
                    .font(.headline)
                    .foregroundStyle(.white)

                if app.sessions.isEmpty {
                    Text("هنوز جلسه‌ای ثبت نشده است.")
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                } else {
                    ForEach(app.sessions) { info in
                        PanelCard {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(info.patientCode)
                                        .font(.subheadline.bold())
                                        .foregroundStyle(.white)
                                    Text("\(SessionStore.displayDate(info.createdAt))  •  \(info.shotCount) عکس")
                                        .font(.caption2)
                                        .foregroundStyle(Theme.muted)
                                }
                                Spacer()
                                Button("باز کردن") { app.openSession(info) }
                                    .buttonStyle(.bordered)
                                    .tint(Theme.accent)
                                Button(role: .destructive) { app.deleteSession(info) } label: {
                                    Image(systemName: "trash")
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                    }
                }

                Text(
                    "تصاویر در حافظه‌ی خصوصی برنامه ذخیره می‌شوند و از پشتیبان‌گیری ابری " +
                    "دستگاه کنار گذاشته می‌شوند. تنها با اقدام صریح شما به گالری یا برنامه‌ی " +
                    "دیگری منتقل می‌شوند. حذف یک جلسه، فایل‌ها را کامل پاک می‌کند."
                )
                .font(.caption2)
                .foregroundStyle(Theme.muted)
            }
            .padding(16)
        }
        .background(Theme.background)
        .onAppear {
            app.refreshSessions()
            app.requestCameraAccess { granted in
                cameraDenied = !granted
            }
        }
    }
}
