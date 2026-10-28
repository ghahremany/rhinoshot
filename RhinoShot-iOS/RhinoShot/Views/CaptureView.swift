import SwiftUI

struct CaptureView: View {
    @EnvironmentObject var app: AppState
    @ObservedObject var camera: CameraController

    @State private var shotIndex: Int = 0
    @State private var calibration: Calibration = Calibration.load()
    @State private var qualityMode: QualityMode = .strict
    @State private var showCalibration = false
    @State private var holdProgress: Double = 0
    @State private var message: String?
    @State private var lastSavedURL: URL?
    /// true when the current capture was triggered automatically.
    @State private var pendingAuto = true

    private var shot: Shot { CLINIC_SHOTS[min(shotIndex, CLINIC_SHOTS.count - 1)] }

    private var yaw: Float {
        calibration.yaw(camera.faceState.yawRaw, front: camera.isFront)
    }

    private var pitch: Float {
        calibration.pitch(camera.faceState.pitchRaw)
    }

    private var strictEval: Evaluation {
        PoseEvaluator.evaluate(shot: shot, state: camera.faceState, yaw: yaw, pitch: pitch, mode: qualityMode)
    }

    /// A slightly wider tolerance keeps the operator from chasing the target.
    private var relaxedEval: Evaluation {
        PoseEvaluator.evaluate(
            shot: shot,
            state: camera.faceState,
            yaw: yaw,
            pitch: pitch,
            mode: qualityMode,
            toleranceScale: 1.35
        )
    }

    var body: some View {
        ZStack {
            CameraPreview(session: camera.session, isFront: camera.isFront)
                .ignoresSafeArea()

            Color.black.opacity(0.25).ignoresSafeArea()

            VStack(spacing: 0) {
                topBar
                Spacer()
                bottomPanel
            }
        }
        .onAppear {
            camera.start()
            camera.onPhotoCaptured = { data in
                handleCapturedPhoto(data)
            }
        }
        .onDisappear {
            camera.stop()
            camera.onPhotoCaptured = nil
        }
        .task(id: taskKey) {
            await runAutoCapture()
        }
        .sheet(isPresented: $showCalibration) {
            CalibrationSheet(calibration: $calibration, camera: camera)
        }
    }

    private var taskKey: String {
        "\(shotIndex)-\(relaxedEval.ready)-\(shot.manual)-\(camera.isFront)"
    }

    // MARK: - Auto capture

    private func runAutoCapture() async {
        holdProgress = 0
        guard !shot.manual, relaxedEval.ready else { return }
        let duration = PoseEvaluator.holdMilliseconds / 1000
        let start = Date()
        while Date().timeIntervalSince(start) < duration {
            try? await Task.sleep(nanoseconds: 33_000_000)
            if Task.isCancelled { return }
            holdProgress = min(1, Date().timeIntervalSince(start) / duration)
        }
        holdProgress = 0
        capture(auto: true)
    }

    // MARK: - Capture

    private func capture(auto: Bool) {
        guard !camera.isCapturing else { return }
        pendingAuto = auto
        camera.capturePhoto()
        message = auto ? nil : "در حال ثبت…"
    }

    private func handleCapturedPhoto(_ data: Data) {
        guard let session = app.session else { return }
        let state = camera.faceState
        let url = session.fileURL(for: shot)
        Exporter.writeJPEG(data, to: url, shot: shot, state: state)

        let record = ShotRecord(
            shot: shot.id,
            file: url.lastPathComponent,
            title: shot.title,
            side: shot.side,
            capturedAt: Date(),
            yawDeg: Double(yaw),
            pitchDeg: Double(pitch),
            rollDeg: Double(camera.faceState.rollRaw),
            smile: Double(state.smile),
            brightness: Double(state.brightness),
            sharpness: Double(state.sharpness),
            faceWidthRatio: Double(state.faceWidth),
            faceHeightRatio: Double(state.faceHeight),
            autoCaptured: pendingAuto
        )
        session.records.removeAll { $0.shot == shot.id }
        session.records.append(record)
        session.writeManifest()
        // Keep the landmarks so the 3D model can be rebuilt later.
        if !state.landmarks.isEmpty {
            session.landmarks[shot.id] = state.landmarks
            session.aspects[shot.id] = state.aspect
            session.writeLandmarks()
        }
        lastSavedURL = url

        if shotIndex + 1 < CLINIC_SHOTS.count {
            shotIndex += 1
            message = "ثبت شد: \(shot.title)"
        } else {
            app.finishCapture()
        }
    }

    // MARK: - UI

    private var topBar: some View {
        VStack(spacing: 8) {
            HStack {
                Button {
                    app.cancelCapture()
                } label: {
                    Image(systemName: "xmark").padding(8)
                }
                .buttonStyle(.bordered)
                .tint(.white)

                Spacer()

                VStack(spacing: 2) {
                    Text("نما \(shotIndex + 1) از \(CLINIC_SHOTS.count)  •  \(shot.title)")
                        .font(.headline)
                        .foregroundStyle(.white)
                    Text(shot.hint)
                        .font(.caption)
                        .foregroundStyle(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }

                Spacer()

                Button {
                    camera.toggleCamera()
                } label: {
                    Image(systemName: "arrow.triangle.2.circlepath.camera").padding(8)
                }
                .buttonStyle(.bordered)
                .tint(.white)
            }

            HStack(spacing: 6) {
                ForEach(Array(CLINIC_SHOTS.enumerated()), id: \.offset) { index, item in
                    Capsule()
                        .fill(index == shotIndex ? Theme.accent : Color.white.opacity(0.28))
                        .frame(height: 5)
                }
            }
            .padding(.horizontal, 4)
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
        .padding(.bottom, 10)
        .background(Color.black.opacity(0.45))
    }

    private var bottomPanel: some View {
        VStack(spacing: 10) {
            AngleDial(yaw: yaw, targetYaw: shot.yaw)
                .frame(height: 76)
                .padding(.horizontal, 40)

            HStack(spacing: 8) {
                MetricBox(label: "yaw", value: "\(Int(yaw))°")
                MetricBox(label: "pitch", value: "\(Int(pitch))°")
                MetricBox(label: "خنده", value: "\(Int(camera.faceState.smile * 100))٪")
                MetricBox(label: "نور", value: "\(Int(camera.faceState.brightness * 100))٪")
            }

            PanelCard {
                VStack(alignment: .leading, spacing: 5) {
                    ForEach(strictEval.checks) { check in
                        CheckRow(check: check)
                    }
                }
            }

            HStack(spacing: 12) {
                Button {
                    showCalibration = true
                } label: {
                    VStack(spacing: 2) {
                        Image(systemName: "dial.medium")
                        Text("کالیبراسیون").font(.caption2)
                    }
                    .frame(width: 74)
                }
                .buttonStyle(.bordered)
                .tint(.white)

                Button {
                    capture(auto: false)
                } label: {
                    ZStack {
                        Circle().fill(Color.white).frame(width: 72, height: 72)
                        Circle().stroke(Color.white.opacity(0.5), lineWidth: 3).frame(width: 84, height: 84)
                    }
                }
                .disabled(camera.isCapturing)

                Button {
                    qualityMode = qualityMode == .strict ? .relaxed : .strict
                } label: {
                    VStack(spacing: 2) {
                        Image(systemName: qualityMode == .strict ? "gauge.with.dots.needle.67percent" : "gauge.with.dots.needle.33percent")
                        Text(qualityMode.rawValue).font(.caption2)
                    }
                    .frame(width: 74)
                }
                .buttonStyle(.bordered)
                .tint(.white)
            }

            if holdProgress > 0 {
                ProgressView(value: holdProgress)
                    .tint(Theme.ok)
            }

            if let message = message {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(Theme.ok)
            }
            if let error = camera.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(Theme.error)
            }
        }
        .padding(12)
        .background(Color.black.opacity(0.55))
    }
}
