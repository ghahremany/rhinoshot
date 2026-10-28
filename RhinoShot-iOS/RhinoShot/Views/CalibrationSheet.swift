import SwiftUI

/// One-off per-device setup: removes the personal offset of the patient's head
/// and teaches the app which way "right" and "up" are on this phone.
/// Same three steps as the Android dialog.
struct CalibrationSheet: View {
    @Binding var calibration: Calibration
    @ObservedObject var camera: CameraController

    @Environment(\.dismiss) private var dismiss
    @State private var step = 0
    @State private var neutralYaw: Float = 0
    @State private var neutralPitch: Float = 0

    private var detected: Bool { camera.faceState.detected }
    private var yawNow: Float { camera.faceState.yawRaw }
    private var pitchNow: Float { camera.faceState.pitchRaw }

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                switch step {
                case 0:
                    Text("سر بیمار را کاملاً مستقیم رو به دوربین نگه دارید و «ثبت زاویه‌ی صفر» را بزنید.")
                        .font(.body)
                        .foregroundStyle(.white)
                    Button("ثبت زاویه‌ی صفر") {
                        neutralYaw = yawNow
                        neutralPitch = pitchNow
                        calibration = calibration.withZero(rawYaw: yawNow, rawPitch: pitchNow, front: camera.isFront)
                        step = 1
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.accent)
                    .disabled(!detected)

                case 1:
                    Text("حالا از بیمار بخواهید سرش را به سمت راست خودش بچرخاند. وقتی زاویه به‌اندازه‌ی کافی چرخید، دکمه را بزنید.")
                        .font(.body)
                        .foregroundStyle(.white)
                    Text("زاویه‌ی فعلی: \(Int(yawNow))°")
                        .font(.callout)
                        .foregroundStyle(Theme.muted)
                    Button("ثبت جهت راست") {
                        let sign = yawNow >= 0 ? 1 : -1
                        calibration = calibration
                            .withYawSign(front: camera.isFront, sign: sign)
                            .withZero(rawYaw: neutralYaw, rawPitch: neutralPitch, front: camera.isFront)
                        step = 2
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.accent)
                    .disabled(!detected || abs(yawNow) < 18)

                case 2:
                    Text("در آخر از بیمار بخواهید چانه را بالا ببرد تا جهت عمودی هم مشخص شود.")
                        .font(.body)
                        .foregroundStyle(.white)
                    Text("زاویه‌ی فعلی: \(Int(pitchNow))°")
                        .font(.callout)
                        .foregroundStyle(Theme.muted)
                    Button("ثبت جهت بالا") {
                        let sign = pitchNow >= 0 ? 1 : -1
                        calibration.pitchSign = sign
                        calibration = calibration
                            .withZero(rawYaw: neutralYaw, rawPitch: neutralPitch, front: camera.isFront)
                        step = 3
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.accent)
                    .disabled(!detected || abs(pitchNow) < 12)

                default:
                    Text("تنظیم انجام شد. این مقدارها برای همیشه روی این گوشی ذخیره می‌مانند.")
                        .font(.body)
                        .foregroundStyle(Theme.ok)
                    Button("ذخیره") {
                        calibration.save()
                        dismiss()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.accent)
                }

                Spacer()
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.background)
            .navigationTitle("تنظیم جهت‌ها")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("بستن") { dismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
