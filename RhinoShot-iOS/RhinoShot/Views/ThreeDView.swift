import SwiftUI
import SceneKit

/// Shows the reconstructed 3D face of the session. The model is rotated with
/// a finger so the nose can be inspected from every direction.
struct ThreeDView: View {
    @EnvironmentObject var app: AppState

    @State private var selectedShot: String = "frontal"
    @State private var wireframe = false
    @State private var scene = SCNScene()
    @State private var model: FaceMesh.Model?
    @State private var message: String?
    @State private var shareItems: [Any] = []
    @State private var showShare = false

    private let topology = FaceMesh.loadTopology()

    private struct ShotEntry: Identifiable {
        let id: String
        let title: String
    }

    private var session: Session? { app.session }

    private var available: [ShotEntry] {
        guard let session = session else { return [] }
        return session.records
            .filter { session.landmarks[$0.shot] != nil }
            .map { record in
                let shot = CLINIC_SHOTS.first { $0.id == record.shot }
                return ShotEntry(id: record.shot, title: shot?.title ?? record.shot)
            }
    }

    var body: some View {
        VStack(spacing: 10) {
            header

            SceneView(scene: scene, options: [.allowsCameraControl, .autoenablesDefaultLighting])
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )

            if available.isEmpty {
                PanelCard("مدلی موجود نیست") {
                    Text("برای ساخت مدل سه‌بعدی باید حداقل یک عکس در همین جلسه ثبت شده باشد.")
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                }
            } else {
                shotPicker
                controls
                if let message = message {
                    Text(message)
                        .font(.caption)
                        .foregroundStyle(Theme.ok)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(Theme.background)
        .sheet(isPresented: $showShare) { ShareSheet(items: shareItems) }
        .onAppear { rebuild() }
    }

    // MARK: - UI

    private var header: some View {
        HStack {
            Button {
                app.backToReview()
            } label: {
                Text("بازگشت").font(.caption)
            }
            .buttonStyle(.bordered)
            .tint(.white)

            Spacer()

            VStack(spacing: 2) {
                Text("نمایش سه‌بعدی چهره")
                    .font(.headline)
                    .foregroundStyle(.white)
                Text("با کشیدن انگشت، مدل بچرخد؛ با دو انگشت زوم کنید")
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
            }

            Spacer()
        }
    }

    private var shotPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(available) { item in
                    Button {
                        selectedShot = item.id
                        rebuild()
                    } label: {
                        Text(item.title)
                            .font(.caption)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(
                                selectedShot == item.id ? Theme.accent : Color.white.opacity(0.12),
                                in: Capsule()
                            )
                            .foregroundStyle(.white)
                    }
                }
            }
            .padding(.horizontal, 2)
        }
    }

    private var controls: some View {
        HStack(spacing: 8) {
            Button {
                rebuild()
            } label: {
                Text("نمای اولیه").font(.caption)
            }
            .buttonStyle(.bordered)
            .tint(.white)

            Button {
                focusNose()
            } label: {
                Text("تمرکز روی بینی").font(.caption)
            }
            .buttonStyle(.bordered)
            .tint(.white)

            Button {
                wireframe.toggle()
                applyMaterial()
            } label: {
                Text(wireframe ? "سطح" : "تور").font(.caption)
            }
            .buttonStyle(.bordered)
            .tint(.white)

            Button {
                exportObj()
            } label: {
                Text("خروجی OBJ").font(.caption)
            }
            .buttonStyle(.bordered)
            .tint(.white)
        }
    }

    // MARK: - Scene

    private func rebuild() {
        guard let session = session else { return }
        guard let topology = topology else {
            message = "فایل مدل مرجع چهره پیدا نشد."
            return
        }
        guard let points = session.landmarks[selectedShot] else {
            message = "برای این نما نقاط چهره ذخیره نشده است."
            return
        }
        let aspect = session.aspects[selectedShot] ?? 1
        guard let built = FaceMesh.build(topology: topology, landmarks: points, aspect: aspect) else {
            message = "ساخت مدل سه‌بعدی از این عکس ممکن نشد."
            return
        }
        message = nil
        model = built
        scene = makeScene(from: built, wireframe: wireframe)
    }

    private func makeScene(from model: FaceMesh.Model, wireframe: Bool) -> SCNScene {
        let scene = SCNScene()
        scene.background.contents = UIColor(red: 0.055, green: 0.078, blue: 0.094, alpha: 1)

        let vertices = model.vertices.map { SCNVector3($0.x, $0.y, $0.z) }
        let source = SCNGeometrySource(vertices: vertices)

        var indexData = Data(capacity: model.triangles.count * 2)
        for i in model.triangles {
            var value = UInt16(truncatingIfNeeded: i)
            withUnsafeBytes(of: &value) { indexData.append(contentsOf: $0) }
        }
        let element = SCNGeometryElement(
            data: indexData,
            primitiveType: .triangles,
            primitiveCount: model.triangles.count / 3,
            bytesPerIndex: 2
        )
        let geometry = SCNGeometry(sources: [source], elements: [element])

        let material = SCNMaterial()
        material.diffuse.contents = UIColor(red: 0.30, green: 0.71, blue: 0.67, alpha: 1)
        material.lightingModel = .physicallyBased
        material.roughness.contents = 0.45
        material.metalness.contents = 0.0
        material.fillMode = wireframe ? .line : .fill
        geometry.materials = [material]

        let meshNode = SCNNode(geometry: geometry)
        scene.rootNode.addChildNode(meshNode)

        let cameraNode = SCNNode()
        let camera = SCNCamera()
        camera.zNear = 1
        camera.zFar = 5000
        camera.fieldOfView = 38
        cameraNode.camera = camera
        cameraNode.position = SCNVector3(0, 0, 420)
        scene.rootNode.addChildNode(cameraNode)

        let key = SCNNode()
        key.light = SCNLight()
        key.light?.type = .omni
        key.light?.intensity = 1100
        key.position = SCNVector3(160, 220, 260)
        scene.rootNode.addChildNode(key)

        let ambient = SCNNode()
        ambient.light = SCNLight()
        ambient.light?.type = .ambient
        ambient.light?.intensity = 260
        scene.rootNode.addChildNode(ambient)

        return scene
    }

    private func applyMaterial() {
        for node in scene.rootNode.childNodes {
            if let geometry = node.geometry {
                let material = SCNMaterial()
                material.diffuse.contents = UIColor(red: 0.30, green: 0.71, blue: 0.67, alpha: 1)
                material.lightingModel = .physicallyBased
                material.roughness.contents = 0.45
                material.fillMode = wireframe ? .line : .fill
                geometry.materials = [material]
            }
        }
    }

    private func focusNose() {
        guard let model = model else { return }
        for node in scene.rootNode.childNodes {
            if node.camera != nil {
                node.position = SCNVector3(model.noseCenter.x, model.noseCenter.y, model.noseCenter.z + 150)
            }
        }
    }

    private func exportObj() {
        guard let session = session, let model = model else {
            message = "مدلی برای این نما موجود نیست"
            return
        }
        let shot = CLINIC_SHOTS.first { $0.id == selectedShot }
        let folder = session.directory.appendingPathComponent("model_3d", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let url = folder.appendingPathComponent("\(shot?.fileName ?? selectedShot)_3d.obj")
        let text = FaceMesh.obj(model, name: shot?.fileName ?? selectedShot)
        do {
            try text.write(to: url, atomically: true, encoding: .utf8)
            message = "فایل سه‌بعدی ساخته شد"
            shareItems = [url]
            showShare = true
        } catch {
            message = "ذخیره‌ی فایل سه‌بعدی ناموفق بود"
        }
    }
}
