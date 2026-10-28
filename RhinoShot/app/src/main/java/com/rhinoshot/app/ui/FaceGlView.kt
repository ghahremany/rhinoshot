package com.rhinoshot.app.ui

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.rhinoshot.app.util.FaceMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * A small OpenGL ES 2.0 view that renders the 3D face model.
 *
 * Dragging rotates the model and pinching zooms in, so the surgeon can
 * inspect the nose from any direction.
 */
class FaceGlView(context: Context) : GLSurfaceView(context) {

    private val renderer = FaceRenderer()

    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.zoom(detector.scaleFactor)
                requestRender()
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setMesh(mesh: FaceMesh.Mesh) {
        queueEvent { renderer.setMesh(mesh) }
        requestRender()
    }

    fun resetView() {
        queueEvent { renderer.reset() }
        requestRender()
    }

    fun focusNose(mesh: FaceMesh.Mesh) {
        queueEvent { renderer.focus(mesh.noseCenter) }
        requestRender()
    }

    fun setWireframe(enabled: Boolean) {
        queueEvent { renderer.wireframe = enabled }
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    renderer.rotate(dx, dy)
                    requestRender()
                }
            }
        }
        return true
    }
}

/** Renders one mesh: solid or wireframe, with simple head-light shading. */
private class FaceRenderer : GLSurfaceView.Renderer {

    private var program = 0
    private var aPosition = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uLight = 0

    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null
    private var lineBuffer: ShortBuffer? = null

    private var vertexCount = 0
    private var indexCount = 0
    private var lineCount = 0
    private var viewWidth = 1
    private var viewHeight = 1

    private var yaw = 0f
    private var pitch = 0f
    private var distance = 420f
    private val target = floatArrayOf(0f, 0f, 0f)

    var wireframe = false

    fun rotate(dx: Float, dy: Float) {
        yaw += dx * 0.4f
        pitch = (pitch + dy * 0.4f).coerceIn(-89f, 89f)
    }

    fun zoom(factor: Float) {
        distance = (distance / factor).coerceIn(90f, 1400f)
    }

    fun reset() {
        yaw = 0f
        pitch = 0f
        distance = 420f
        target[0] = 0f
        target[1] = 0f
        target[2] = 0f
    }

    fun focus(nose: FloatArray) {
        target[0] = nose[0]
        target[1] = nose[1]
        target[2] = nose[2]
        distance = 210f
    }

    fun setMesh(mesh: FaceMesh.Mesh) {
        vertexBuffer = toFloatBuffer(mesh.vertices)
        normalBuffer = toFloatBuffer(mesh.normals)
        vertexCount = mesh.vertices.size / 3

        val shorts = ShortArray(mesh.triangles.size) { mesh.triangles[it].toShort() }
        indexBuffer = toShortBuffer(shorts)
        indexCount = shorts.size

        val lines = ArrayList<Short>(mesh.triangles.size * 2)
        var i = 0
        while (i < mesh.triangles.size) {
            val a = mesh.triangles[i].toShort()
            val b = mesh.triangles[i + 1].toShort()
            val c = mesh.triangles[i + 2].toShort()
            lines.add(a); lines.add(b)
            lines.add(b); lines.add(c)
            lines.add(c); lines.add(a)
            i += 3
        }
        val lineShorts = ShortArray(lines.size) { lines[it] }
        lineBuffer = toShortBuffer(lineShorts)
        lineCount = lineShorts.size
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.055f, 0.078f, 0.094f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        program = buildProgram()
        aPosition = GLES20.glGetAttribLocation(program, "aPosition")
        aNormal = GLES20.glGetAttribLocation(program, "aNormal")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uLight = GLES20.glGetUniformLocation(program, "uLight")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (vertexCount == 0) return

        val projection = FloatArray(16)
        val aspect = if (viewHeight > 0) viewWidth.toFloat() / viewHeight.toFloat() else 1f
        Matrix.perspectiveM(projection, 0, 38f, aspect, 5f, 3000f)

        val view = FloatArray(16)
        Matrix.setLookAtM(
            view, 0,
            target[0], target[1], target[2] + distance,
            target[0], target[1], target[2],
            0f, 1f, 0f,
        )

        val model = FloatArray(16)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, target[0], target[1], target[2])
        Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)

        val viewModel = FloatArray(16)
        Matrix.multiplyMM(viewModel, 0, view, 0, model, 0)
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, projection, 0, viewModel, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform3f(uLight, 0.4f, 0.6f, 0.8f)

        vertexBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        normalBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)

        if (wireframe) {
            lineBuffer?.position(0)
            GLES20.glDrawElements(GLES20.GL_LINES, lineCount, GLES20.GL_UNSIGNED_SHORT, lineBuffer)
        } else {
            indexBuffer?.position(0)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, indexBuffer,
            )
        }

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    // ------------------------------------------------------------------ helpers

    private fun toFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply {
                put(values)
                position(0)
            }

    private fun toShortBuffer(values: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder())
            .asShortBuffer().apply {
                put(values)
                position(0)
            }

    private fun buildProgram(): Int {
        val vertexShader = compile(
            GLES20.GL_VERTEX_SHADER,
            """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                // uModel only rotates and translates, so the w component of the
                // normal stays zero and the normal is rotated correctly.
                vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
            """.trimIndent(),
        )
        val fragmentShader = compile(
            GLES20.GL_FRAGMENT_SHADER,
            """
            precision mediump float;
            uniform vec3 uLight;
            varying vec3 vNormal;
            void main() {
                vec3 n = normalize(vNormal);
                float diff = max(dot(n, normalize(uLight)), 0.0);
                float rim = pow(1.0 - abs(n.z), 2.0);
                vec3 base = vec3(0.30, 0.71, 0.67);
                vec3 color = base * (0.32 + 0.78 * diff) + vec3(0.25, 0.45, 0.50) * rim * 0.35;
                gl_FragColor = vec4(color, 1.0);
            }
            """.trimIndent(),
        )
        val id = GLES20.glCreateProgram()
        GLES20.glAttachShader(id, vertexShader)
        GLES20.glAttachShader(id, fragmentShader)
        GLES20.glLinkProgram(id)
        val status = IntArray(1)
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("FaceGlView", "Shader link failed: ${GLES20.glGetProgramInfoLog(id)}")
        }
        return id
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("FaceGlView", "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
        }
        return shader
    }
}
