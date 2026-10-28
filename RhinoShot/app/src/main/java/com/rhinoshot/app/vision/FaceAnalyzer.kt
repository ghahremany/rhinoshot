package com.rhinoshot.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.io.Closeable
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Runs the MediaPipe Face Landmarker on every camera frame and reports
 * head pose (yaw / pitch / roll), the smile score, the face box and the
 * image-quality metrics.
 */
class FaceAnalyzer(
    context: Context,
    private val onState: (FaceState) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    private val landmarker: FaceLandmarker? = try {
        FaceLandmarker.createFromOptions(
            context,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .build()
        )
    } catch (t: Throwable) {
        null
    }

    /** Non-null when the MediaPipe model could not be opened. */
    val loadError: String? = if (landmarker == null) {
        "مدل تشخیص چهره بارگذاری نشد. فایل face_landmarker.task را در app/src/main/assets قرار دهید."
    } else {
        null
    }

    override fun analyze(image: ImageProxy) {
        val lm = landmarker
        if (lm == null) {
            image.close()
            onState(FaceState(detected = false, error = loadError))
            return
        }
        try {
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val upright = if (rotation == 0) raw else Bitmap.createBitmap(
                raw, 0, 0, raw.width, raw.height,
                Matrix().apply { postRotate(rotation.toFloat()) }, true
            )

            val result = lm.detect(BitmapImageBuilder(upright).build())
            val landmarks = result.faceLandmarks().firstOrNull()
            if (landmarks.isNullOrEmpty()) {
                onState(FaceState(detected = false, ts = System.currentTimeMillis()))
                return
            }

            val matrix = result.facialTransformationMatrixes().orElse(null)?.firstOrNull()
            var yaw = 0f
            var pitch = 0f
            var roll = 0f
            if (matrix != null && matrix.size >= 16) {
                // Column-major 4x4: element (row, col) = matrix[col * 4 + row]
                val r00 = matrix[0]
                val r10 = matrix[1]
                val r20 = matrix[2]
                val r21 = matrix[6]
                val r22 = matrix[10]
                pitch = Math.toDegrees(atan2(r21.toDouble(), r22.toDouble())).toFloat()
                yaw = Math.toDegrees(
                    atan2((-r20).toDouble(), hypot(r21.toDouble(), r22.toDouble()))
                ).toFloat()
                roll = Math.toDegrees(atan2(r10.toDouble(), r00.toDouble())).toFloat()
            }

            val xs = FloatArray(landmarks.size)
            val ys = FloatArray(landmarks.size)
            for (i in landmarks.indices) {
                xs[i] = landmarks[i].x()
                ys[i] = landmarks[i].y()
            }
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            for (i in xs.indices) {
                if (xs[i] < minX) minX = xs[i]
                if (xs[i] > maxX) maxX = xs[i]
                if (ys[i] < minY) minY = ys[i]
                if (ys[i] > maxY) maxY = ys[i]
            }
            val box = RectF(minX, minY, maxX, maxY)

            // Chin (152) and forehead (10) give a stable "how close am I" metric.
            val chinY = landmarks.getOrNull(152)?.y() ?: maxY
            val foreheadY = landmarks.getOrNull(10)?.y() ?: minY
            val nose = landmarks.getOrNull(1)

            val shapes = result.faceBlendshapes().orElse(null)?.firstOrNull().orEmpty()
            val smileScores = shapes.filter { it.categoryName().startsWith("mouthSmile") }
                .map { it.score() }
            val smile = if (smileScores.isEmpty()) 0f else smileScores.average().toFloat().coerceIn(0f, 1f)
            val blink = shapes.filter { it.categoryName().startsWith("eyeBlink") }
                .map { it.score() }
                .maxOrNull() ?: 0f
            val eyesOpen = blink < 0.4f

            val quality = ImageQuality.measure(upright, box)

            // Flatten the landmarks (x, y, z) - they rebuild the 3D model.
            val flat = ArrayList<Float>(landmarks.size * 3)
            for (lm in landmarks) {
                flat.add(lm.x())
                flat.add(lm.y())
                flat.add(lm.z())
            }
            val transform = if (matrix != null && matrix.size >= 16) {
                matrix.toList()
            } else {
                emptyList()
            }

            onState(
                FaceState(
                    detected = true,
                    yawRaw = yaw,
                    pitchRaw = pitch,
                    rollRaw = roll,
                    smile = smile,
                    eyesOpen = eyesOpen,
                    faceBox = box,
                    faceWidth = box.width(),
                    faceHeight = (chinY - foreheadY).coerceAtLeast(0f),
                    noseX = nose?.x() ?: box.centerX(),
                    noseY = nose?.y() ?: box.centerY(),
                    brightness = quality.brightness,
                    sharpness = quality.sharpness,
                    landmarks = flat,
                    transform = transform,
                    aspect = upright.height.toFloat() / upright.width.toFloat(),
                    ts = System.currentTimeMillis(),
                )
            )
        } catch (t: Throwable) {
            onState(FaceState(detected = false, error = t.message, ts = System.currentTimeMillis()))
        } finally {
            image.close()
        }
    }

    override fun close() {
        runCatching { landmarker?.close() }
    }

    companion object {
        const val MODEL_ASSET = "face_landmarker.task"
    }
}
