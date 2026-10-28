package com.rhinoshot.app.util

import android.content.Context
import android.util.Log
import com.rhinoshot.app.data.AppInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Builds a 3D mesh of the patient's face from the MediaPipe landmarks.
 *
 * The topology (which landmark belongs to which triangle) comes from the
 * MediaPipe *canonical face model* (468 vertices, 898 triangles) that ships
 * with the SDK as `canonical_face_model.bin` in the assets folder.
 *
 * The vertex positions come from the landmarks that were detected while the
 * photo was taken. MediaPipe reports them in normalised image coordinates
 * (x and y relative to the image size, z with roughly the same scale as x),
 * so they are converted into millimetres with the average adult distance
 * between the outer eye corners (≈ 88 mm).
 *
 * Only the depth channel is smoothed: x/y are accurate, z is a monocular
 * estimate and therefore noisy.
 */
object FaceMesh {

    const val ASSET = "canonical_face_model.bin"

    /** Average distance between the outer corners of the eyes in an adult face. */
    private const val OUTER_EYE_MM = 88f

    /** Landmarks that describe the tip and the wings of the nose. */
    private val NOSE_LANDMARKS = intArrayOf(1, 4, 5, 6, 19, 44, 45, 125, 141, 168, 195, 197)

    class Topology(
        val vertexCount: Int,
        val faceCount: Int,
        val vertices: FloatArray,
        val indices: IntArray,
    )

    class Mesh(
        /** xyz triples in millimetres. */
        val vertices: FloatArray,
        /** triangle indices. */
        val triangles: IntArray,
        /** unit normals. */
        val normals: FloatArray,
        /** centre of the nose in millimetres. */
        val noseCenter: FloatArray,
        /** width of the face at the eyes, in millimetres. */
        val faceWidthMm: Float,
    )

    // ------------------------------------------------------------------ loading

    fun loadTopology(context: Context): Topology? {
        return try {
            val bytes = context.assets.open(ASSET).use { it.readBytes() }
            if (bytes.size < 12) return null
            // The file is written little-endian (the same format the iOS app reads).
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            if (String(magic, Charsets.US_ASCII) != MAGIC) return null
            val vertexCount = buffer.int
            val faceCount = buffer.int
            if (vertexCount <= 0 || faceCount <= 0) return null
            if (bytes.size < 12 + vertexCount * 12 + faceCount * 12) return null
            val vertices = FloatArray(vertexCount * 3)
            for (i in vertices.indices) vertices[i] = buffer.float
            val indices = IntArray(faceCount * 3)
            for (i in indices.indices) indices[i] = buffer.int
            Topology(vertexCount, faceCount, vertices, indices)
        } catch (t: Throwable) {
            Log.w("FaceMesh", "Could not read the canonical face model: ${t.message}")
            null
        }
    }

    private const val MAGIC = "RHM1"

    // ------------------------------------------------------------------ building

    /**
     * @param landmarks flattened [x, y, z] triples in normalised image coordinates.
     * @param aspect    image height divided by image width.
     */
    fun build(topology: Topology, landmarks: List<Float>, aspect: Float): Mesh? {
        val count = minOf(topology.vertexCount, landmarks.size / 3)
        if (count < 264) return null

        // Scale: the outer eye corners are 88 mm apart in an adult face.
        val dx = abs(landmarks[33 * 3] - landmarks[263 * 3])
        val faceWidthMm = if (dx > 1e-4f) OUTER_EYE_MM / dx else OUTER_EYE_MM
        val faceHeightMm = faceWidthMm * aspect

        // Centre the model on the nose tip.
        val noseX = landmarks[3]
        val noseY = landmarks[4]

        val vertices = FloatArray(count * 3)
        for (i in 0 until count) {
            val x = landmarks[i * 3]
            val y = landmarks[i * 3 + 1]
            val z = landmarks[i * 3 + 2]
            vertices[i * 3] = (x - noseX) * faceWidthMm
            vertices[i * 3 + 1] = (noseY - y) * faceHeightMm
            vertices[i * 3 + 2] = -z * faceWidthMm
        }

        // Smooth the depth channel only (2.5 iterations, factor 0.45).
        smoothDepth(vertices, topology.indices, count)

        // Keep only the triangles whose three vertices exist.
        val triangleList = ArrayList<Int>(topology.indices.size)
        var t = 0
        while (t + 2 < topology.indices.size) {
            val a = topology.indices[t]
            val b = topology.indices[t + 1]
            val c = topology.indices[t + 2]
            if (a < count && b < count && c < count) {
                triangleList.add(a); triangleList.add(b); triangleList.add(c)
            }
            t += 3
        }
        val triangles = triangleList.toIntArray()
        val normals = computeNormals(vertices, triangles)

        var nx = 0f
        var ny = 0f
        var nz = 0f
        var n = 0
        for (i in NOSE_LANDMARKS) {
            if (i < count) {
                nx += vertices[i * 3]
                ny += vertices[i * 3 + 1]
                nz += vertices[i * 3 + 2]
                n++
            }
        }
        val noseCenter = if (n > 0) {
            floatArrayOf(nx / n, ny / n, nz / n)
        } else {
            floatArrayOf(0f, 0f, 0f)
        }

        return Mesh(vertices, triangles, normals, noseCenter, faceWidthMm)
    }

    private fun smoothDepth(vertices: FloatArray, indices: IntArray, count: Int) {
        if (count < 3) return
        val adjacency = Array(count) { mutableListOf<Int>() }
        var i = 0
        while (i < indices.size) {
            val a = indices[i]
            val b = indices[i + 1]
            val c = indices[i + 2]
            if (a < count && b < count && c < count) {
                adjacency[a].add(b); adjacency[a].add(c)
                adjacency[b].add(a); adjacency[b].add(c)
                adjacency[c].add(a); adjacency[c].add(b)
            }
            i += 3
        }
        repeat(3) {
            val copy = vertices.copyOf()
            for (v in 0 until count) {
                val neighbours = adjacency[v]
                if (neighbours.isEmpty()) continue
                var sum = 0f
                for (n in neighbours) sum += copy[n * 3 + 2]
                val avg = sum / neighbours.size
                vertices[v * 3 + 2] = copy[v * 3 + 2] * 0.55f + avg * 0.45f
            }
        }
    }

    private fun computeNormals(vertices: FloatArray, triangles: IntArray): FloatArray {
        val normals = FloatArray(vertices.size)
        var i = 0
        while (i < triangles.size) {
            val a = triangles[i] * 3
            val b = triangles[i + 1] * 3
            val c = triangles[i + 2] * 3
            val ux = vertices[b] - vertices[a]
            val uy = vertices[b + 1] - vertices[a + 1]
            val uz = vertices[b + 2] - vertices[a + 2]
            val vx = vertices[c] - vertices[a]
            val vy = vertices[c + 1] - vertices[a + 1]
            val vz = vertices[c + 2] - vertices[a + 2]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            normals[a] += nx; normals[a + 1] += ny; normals[a + 2] += nz
            normals[b] += nx; normals[b + 1] += ny; normals[b + 2] += nz
            normals[c] += nx; normals[c + 1] += ny; normals[c + 2] += nz
            i += 3
        }
        for (v in normals.indices step 3) {
            val len = sqrt(normals[v] * normals[v] + normals[v + 1] * normals[v + 1] + normals[v + 2] * normals[v + 2])
            if (len > 1e-6f) {
                normals[v] /= len
                normals[v + 1] /= len
                normals[v + 2] /= len
            }
        }
        return normals
    }

    // ------------------------------------------------------------------ exporting

    /** Wavefront OBJ of the mesh — opens in every 3D tool. */
    fun toObj(mesh: Mesh, name: String): String {
        val sb = StringBuilder()
        sb.append("# RhinoShot 3D face model\n")
        sb.append("# ").append(AppInfo.APP_NAME).append(' ').append(AppInfo.APP_VERSION).append('\n')
        sb.append("o ").append(name).append('\n')
        var i = 0
        while (i < mesh.vertices.size) {
            sb.append("v ")
                .append(mesh.vertices[i]).append(' ')
                .append(mesh.vertices[i + 1]).append(' ')
                .append(mesh.vertices[i + 2]).append('\n')
            i += 3
        }
        i = 0
        while (i < mesh.triangles.size) {
            val a = mesh.triangles[i] + 1
            val b = mesh.triangles[i + 1] + 1
            val c = mesh.triangles[i + 2] + 1
            sb.append("f ").append(a).append(' ').append(b).append(' ').append(c).append('\n')
            i += 3
        }
        return sb.toString()
    }
}
