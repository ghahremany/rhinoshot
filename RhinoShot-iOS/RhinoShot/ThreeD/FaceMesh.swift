import Foundation
import simd

/// Builds a 3D mesh of the patient's face from the MediaPipe landmarks.
///
/// The topology (which landmark belongs to which triangle) comes from the
/// MediaPipe *canonical face model* (468 vertices, 898 triangles) that ships
/// with the SDK as `canonical_face_model.bin` inside the app bundle.
///
/// The vertex positions come from the landmarks that were detected while the
/// photo was taken. MediaPipe reports them in normalised image coordinates
/// (x and y relative to the image size, z with roughly the same scale as x),
/// so they are converted into millimetres using the average adult distance
/// between the outer eye corners (≈ 88 mm).
///
/// Only the depth channel is smoothed: x/y are accurate, z is a monocular
/// estimate and therefore noisy.
enum FaceMesh {

    static let assetName = "canonical_face_model"
    static let assetExtension = "bin"

    /// Average distance between the outer corners of the eyes in an adult face.
    private static let outerEyeMm: Float = 88

    /// Landmarks that describe the tip and the wings of the nose.
    private static let noseLandmarks: [Int] = [1, 4, 5, 6, 19, 44, 45, 125, 141, 168, 195, 197]

    struct Topology {
        let vertexCount: Int
        let faceCount: Int
        let vertices: [Float]
        let indices: [Int]
    }

    struct Model {
        /// xyz triples in millimetres.
        let vertices: [SIMD3<Float>]
        /// triangle indices.
        let triangles: [Int]
        /// unit normals.
        let normals: [SIMD3<Float>]
        /// centre of the nose in millimetres.
        let noseCenter: SIMD3<Float>
        /// width of the face at the eyes, in millimetres.
        let faceWidthMm: Float
    }

    // MARK: - Loading

    static func loadTopology(bundle: Bundle = .main) -> Topology? {
        guard let url = bundle.url(forResource: assetName, withExtension: assetExtension),
              let data = try? Data(contentsOf: url) else { return nil }
        let bytes = [UInt8](data)
        guard bytes.count > 12 else { return nil }
        let magic = String(bytes: bytes[0..<4], encoding: .ascii)
        guard magic == "RHM1" else { return nil }

        func uint32(_ offset: Int) -> UInt32 {
            UInt32(bytes[offset]) | (UInt32(bytes[offset + 1]) << 8)
                | (UInt32(bytes[offset + 2]) << 16) | (UInt32(bytes[offset + 3]) << 24)
        }
        func float32(_ offset: Int) -> Float {
            Float(bitPattern: uint32(offset))
        }

        let vertexCount = Int(uint32(4))
        let faceCount = Int(uint32(8))
        guard vertexCount > 0, faceCount > 0 else { return nil }

        var vertices: [Float] = []
        vertices.reserveCapacity(vertexCount * 3)
        var offset = 12
        for _ in 0..<(vertexCount * 3) {
            vertices.append(float32(offset))
            offset += 4
        }
        var indices: [Int] = []
        indices.reserveCapacity(faceCount * 3)
        for _ in 0..<(faceCount * 3) {
            indices.append(Int(uint32(offset)))
            offset += 4
        }
        return Topology(vertexCount: vertexCount, faceCount: faceCount, vertices: vertices, indices: indices)
    }

    // MARK: - Building

    /// - Parameters:
    ///   - landmarks: detected landmarks (x, y, z) in normalised image coordinates.
    ///   - aspect: image height divided by image width.
    static func build(topology: Topology, landmarks: [SIMD3<Float>], aspect: Float) -> Model? {
        let count = min(topology.vertexCount, landmarks.count)
        guard count > 263 else { return nil }

        // Scale: the outer eye corners are 88 mm apart in an adult face.
        let dx = abs(landmarks[33].x - landmarks[263].x)
        let faceWidthMm = dx > 1e-4 ? outerEyeMm / dx : outerEyeMm
        let faceHeightMm = faceWidthMm * aspect

        // Centre the model on the nose tip.
        let nose = landmarks[1]

        var vertices: [SIMD3<Float>] = []
        vertices.reserveCapacity(count)
        for i in 0..<count {
            let p = landmarks[i]
            vertices.append(SIMD3<Float>(
                (p.x - nose.x) * faceWidthMm,
                (nose.y - p.y) * faceHeightMm,
                -p.z * faceWidthMm
            ))
        }

        // Smooth the depth channel only.
        smoothDepth(&vertices, indices: topology.indices, count: count)

        // Keep only the triangles whose three vertices exist.
        var triangles: [Int] = []
        triangles.reserveCapacity(topology.indices.count)
        var i = 0
        while i + 2 < topology.indices.count {
            let a = topology.indices[i], b = topology.indices[i + 1], c = topology.indices[i + 2]
            if a < count && b < count && c < count {
                triangles.append(contentsOf: [a, b, c])
            }
            i += 3
        }
        let normals = computeNormals(vertices: vertices, triangles: triangles)

        var noseCenter = SIMD3<Float>(0, 0, 0)
        var used = 0
        for i in noseLandmarks where i < count {
            noseCenter += vertices[i]
            used += 1
        }
        if used > 0 { noseCenter /= Float(used) }

        return Model(
            vertices: vertices,
            triangles: triangles,
            normals: normals,
            noseCenter: noseCenter,
            faceWidthMm: faceWidthMm
        )
    }

    private static func smoothDepth(_ vertices: inout [SIMD3<Float>], indices: [Int], count: Int) {
        guard count >= 3 else { return }
        var adjacency = [[Int]](repeating: [], count: count)
        var i = 0
        while i + 2 < indices.count {
            let a = indices[i], b = indices[i + 1], c = indices[i + 2]
            if a < count && b < count && c < count {
                adjacency[a].append(contentsOf: [b, c])
                adjacency[b].append(contentsOf: [a, c])
                adjacency[c].append(contentsOf: [a, b])
            }
            i += 3
        }
        for _ in 0..<3 {
            let copy = vertices
            for v in 0..<count {
                let neighbours = adjacency[v]
                guard !neighbours.isEmpty else { continue }
                var sum: Float = 0
                for n in neighbours { sum += copy[n].z }
                let average = sum / Float(neighbours.count)
                vertices[v].z = copy[v].z * 0.55 + average * 0.45
            }
        }
    }

    private static func computeNormals(vertices: [SIMD3<Float>], triangles: [Int]) -> [SIMD3<Float>] {
        var normals = [SIMD3<Float>](repeating: .zero, count: vertices.count)
        var i = 0
        while i + 2 < triangles.count {
            let a = triangles[i], b = triangles[i + 1], c = triangles[i + 2]
            guard a < vertices.count, b < vertices.count, c < vertices.count else {
                i += 3
                continue
            }
            let u = vertices[b] - vertices[a]
            let v = vertices[c] - vertices[a]
            let n = simd_cross(u, v)
            normals[a] += n
            normals[b] += n
            normals[c] += n
            i += 3
        }
        for index in normals.indices {
            let length = simd_length(normals[index])
            if length > 1e-6 {
                normals[index] /= length
            }
        }
        return normals
    }

    // MARK: - Export

    /// Wavefront OBJ of the mesh — opens in every 3D tool.
    static func obj(_ model: Model, name: String) -> String {
        var lines: [String] = []
        lines.append("# RhinoShot 3D face model")
        lines.append("# generated on the device, units = millimetres")
        lines.append("o \(name)")
        for v in model.vertices {
            lines.append(String(format: "v %.3f %.3f %.3f", v.x, v.y, v.z))
        }
        var i = 0
        while i + 2 < model.triangles.count {
            lines.append("f \(model.triangles[i] + 1) \(model.triangles[i + 1] + 1) \(model.triangles[i + 2] + 1)")
            i += 3
        }
        return lines.joined(separator: "\n") + "\n"
    }
}
