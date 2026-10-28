package com.rhinoshot.app.ui

import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rhinoshot.app.data.AppInfo
import com.rhinoshot.app.data.CLINIC_SHOTS
import com.rhinoshot.app.data.Session
import com.rhinoshot.app.data.Shot
import com.rhinoshot.app.data.ShotRecord
import com.rhinoshot.app.vision.Calibration
import com.rhinoshot.app.vision.FaceAnalyzer
import com.rhinoshot.app.vision.FaceState
import com.rhinoshot.app.vision.PoseEvaluator
import com.rhinoshot.app.vision.QualityMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The guided capture screen: CameraX preview + MediaPipe head-pose feedback +
 * automatic shutter as soon as the pose and the image quality are good.
 */
@Composable
fun CaptureScreen(
    session: Session,
    startIndex: Int,
    backToReview: Boolean,
    onFinished: (Session) -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current

    val poseFlow = remember { MutableStateFlow<FaceState?>(null) }
    var shotIndex by remember(startIndex) { mutableIntStateOf(startIndex) }
    var useFront by remember { mutableStateOf(true) }
    var capturing by remember { mutableStateOf(false) }
    var calibration by remember { mutableStateOf(Calibration.load(ctx)) }
    var qualityMode by remember { mutableStateOf(QualityMode.STRICT) }
    var showCalibration by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { FaceAnalyzer(ctx) { poseFlow.value = it } }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(1536, 2048),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
            )
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(useFront) {
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val selector = if (useFront && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    android.util.Size(640, 480),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                )
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(analysisExecutor, analyzer)
                provider.unbindAll()
                provider.bindToLifecycle(owner, selector, preview, imageCapture, analysis)
                errorText = null
            } catch (t: Throwable) {
                errorText = "اتصال به دوربین ممکن نشد: ${t.message}"
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    val state by poseFlow.collectAsState()
    val current = state
    val shot = CLINIC_SHOTS[shotIndex]
    val yaw = current?.let { calibration.yaw(it.yawRaw, useFront) } ?: 0f
    val pitch = current?.let { calibration.pitch(it.pitchRaw) } ?: 0f
    val evaluation = if (current != null) {
        PoseEvaluator.evaluate(shot, current, yaw, pitch, qualityMode)
    } else {
        PoseEvaluator.noFace()
    }
    val relaxed = if (current != null) {
        PoseEvaluator.evaluate(shot, current, yaw, pitch, qualityMode, toleranceScale = 1.35f)
    } else {
        evaluation
    }

    fun captureNow(auto: Boolean) {
        if (capturing) return
        capturing = true
        val target = session.fileFor(shot)
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = false }
        val options = ImageCapture.OutputFileOptions.Builder(target).setMetadata(metadata).build()
        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    capturing = false
                    val current = poseFlow.value
                    session.records.removeAll { it.shotId == shot.id }
                    session.records.add(
                        ShotRecord(
                            shotId = shot.id,
                            fileName = "${shot.fileName}.jpg",
                            title = shot.title,
                            side = shot.side,
                            capturedAt = System.currentTimeMillis(),
                            yaw = yaw,
                            pitch = pitch,
                            roll = current?.rollRaw ?: 0f,
                            smile = current?.smile ?: 0f,
                            brightness = current?.brightness ?: 0f,
                            sharpness = current?.sharpness ?: 0f,
                            faceWidth = current?.faceWidth ?: 0f,
                            faceHeight = current?.faceHeight ?: 0f,
                            auto = auto,
                        )
                    )
                    writeExif(target, shot, current)
                    // Keep the landmarks so the 3D model can be rebuilt later.
                    current?.landmarks?.takeIf { it.isNotEmpty() }?.let { points ->
                        session.landmarks[shot.id] = points
                        session.aspects[shot.id] = current.aspect
                    }
                    session.writeManifest()
                    session.writeLandmarks()
                    if (shotIndex + 1 >= CLINIC_SHOTS.size) onFinished(session) else shotIndex++
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    errorText = "ثبت عکس انجام نشد: ${exception.message}"
                }
            }
        )
    }

    // Automatic shutter: the pose has to be held for HOLD_MS.
    LaunchedEffect(relaxed.ready, shotIndex, capturing, shot.manual) {
        if (!relaxed.ready || capturing || shot.manual) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        val t0 = SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - t0
            holdProgress = (elapsed.toFloat() / PoseEvaluator.HOLD_MS).coerceIn(0f, 1f)
            if (elapsed >= PoseEvaluator.HOLD_MS) break
            delay(40)
        }
        holdProgress = 0f
        captureNow(auto = true)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Canvas(Modifier.fillMaxSize()) {
            val guideW = size.width * 0.68f
            val guideH = size.height * 0.40f
            val cx = size.width / 2f
            val cy = size.height * 0.40f
            val guideColor = if (evaluation.ready) OkGreen else Color.White.copy(alpha = 0.7f)
            drawOval(
                color = guideColor,
                topLeft = Offset(cx - guideW / 2f, cy - guideH / 2f),
                size = Size(guideW, guideH),
                style = Stroke(width = 5f),
            )
            if (holdProgress > 0f) {
                drawArc(
                    color = OkGreen,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress,
                    useCenter = false,
                    topLeft = Offset(cx - guideW / 2f, cy - guideH / 2f),
                    size = Size(guideW, guideH),
                    style = Stroke(width = 8f, cap = StrokeCap.Round),
                )
            }
            state?.faceBox?.let { box ->
                drawRect(
                    color = Color(0x554DB6AC),
                    topLeft = Offset(box.left * size.width, box.top * size.height),
                    size = Size(box.width() * size.width, box.height() * size.height),
                    style = Stroke(width = 3f),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color(0xD90E1418))
                .padding(14.dp)
        ) {
            Text(
                "نمای ${shotIndex + 1} از ${CLINIC_SHOTS.size}  •  ${shot.title}",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                shot.hint,
                color = MutedText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { shotIndex.toFloat() / CLINIC_SHOTS.size },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xE60E1418))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AngleDial(
                yaw = yaw,
                targetYaw = shot.yaw,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
            )

            val issues = evaluation.issues.take(3)
            if (issues.isEmpty()) {
                Text(
                    "همه‌چیز آماده است — نگه دارید…",
                    color = OkGreen,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                issues.forEach { CheckRow(it) }
            }

            if (holdProgress > 0f) {
                LinearProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            errorText?.let {
                Text(it, color = ErrorRed, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { captureNow(false) },
                    enabled = !capturing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("ثبت عکس")
                }
                OutlinedButton(
                    onClick = {
                        if (shotIndex + 1 >= CLINIC_SHOTS.size) onFinished(session) else shotIndex++
                    },
                    enabled = !capturing,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("رد کردن")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = { useFront = !useFront }) { Text("چرخش دوربین") }
                TextButton(onClick = { showCalibration = true }) { Text("تنظیم جهت‌ها") }
                TextButton(
                    onClick = {
                        qualityMode = if (qualityMode == QualityMode.STRICT) QualityMode.RELAXED else QualityMode.STRICT
                    }
                ) {
                    Text(if (qualityMode == QualityMode.STRICT) "کنترل کیفیت: سخت‌گیرانه" else "کنترل کیفیت: آسان")
                }
                TextButton(onClick = { onCancel() }) { Text("انصراف") }
            }

            // The live read-outs sit at the very bottom of the screen so that
            // they never cover the camera preview or the face guide.
            Spacer(Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MetricBox("چرخش", "${yaw.toInt()}°")
                MetricBox("سر", "${pitch.toInt()}°")
                MetricBox("لبخند", "${((state?.smile ?: 0f) * 100).toInt()}٪")
                MetricBox(
                    "نور",
                    "${((state?.brightness ?: 0f) * 100).toInt()}٪",
                )
            }
        }
    }

    if (showCalibration) {
        CalibrationDialog(
            state = state,
            front = useFront,
            initial = calibration,
            onDismiss = { showCalibration = false },
            onSave = {
                calibration = it
                Calibration.save(ctx, it)
                showCalibration = false
            },
        )
    }
}

@Composable
private fun RowScope.MetricBox(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(Color(0x33FFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .weight(1f),
    ) {
        Text(label, color = MutedText, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Simple gauge: the white dot is the current angle, the green dot the target. */
@Composable
fun AngleDial(yaw: Float, targetYaw: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.98f
        val r = size.height * 0.92f
        val start = 200f
        val sweep = 140f

        drawArc(
            color = Color(0x33FFFFFF),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(2 * r, 2 * r),
            style = Stroke(width = 4f, cap = StrokeCap.Round),
        )

        fun angleFor(value: Float): Float =
            start + (value.coerceIn(-90f, 90f) + 90f) / 180f * sweep

        fun pointAt(deg: Float): Offset {
            val rad = Math.toRadians(deg.toDouble())
            return Offset(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat())
        }

        targetYaw?.let {
            drawCircle(color = OkGreen, radius = 10f, center = pointAt(angleFor(it)))
        }
        drawCircle(color = Color.White, radius = 8f, center = pointAt(angleFor(yaw)))
    }
}

private fun writeExif(file: File, shot: Shot, state: FaceState?) {
    runCatching {
        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER ?: "")
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL ?: "")
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "RhinoShot " + AppInfo.APP_VERSION)
        exif.setAttribute(
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            shot.title + if (shot.side.isNotBlank()) " - ${shot.side}" else "",
        )
        exif.setAttribute(
            ExifInterface.TAG_DATETIME,
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date()),
        )
        exif.setAttribute(
            ExifInterface.TAG_USER_COMMENT,
            "yaw=${state?.yawRaw ?: 0f};pitch=${state?.pitchRaw ?: 0f};smile=${state?.smile ?: 0f}",
        )
        exif.saveAttributes()
    }
}
