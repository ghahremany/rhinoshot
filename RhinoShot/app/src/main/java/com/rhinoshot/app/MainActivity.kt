package com.rhinoshot.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.rhinoshot.app.data.CLINIC_SHOTS
import com.rhinoshot.app.data.PatientInfo
import com.rhinoshot.app.data.PrintSize
import com.rhinoshot.app.data.Session
import com.rhinoshot.app.data.SessionStore
import com.rhinoshot.app.ui.AboutScreen
import com.rhinoshot.app.ui.CaptureScreen
import com.rhinoshot.app.ui.HomeScreen
import com.rhinoshot.app.ui.ReviewScreen
import com.rhinoshot.app.ui.ThreeDScreen
import com.rhinoshot.app.util.Exporter

private val RhinoScheme = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00312C),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF0E1418),
    onBackground = Color(0xFFE3EAEE),
    surface = Color(0xFF161E24),
    onSurface = Color(0xFFE3EAEE),
    error = Color(0xFFE57373),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = RhinoScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        RhinoApp()
                    }
                }
            }
        }
    }
}

private enum class Stage { HOME, CAPTURE, REVIEW, THREE_D, ABOUT }

@Composable
fun RhinoApp() {
    val ctx = LocalContext.current
    val store = remember { SessionStore(SessionStore.root(ctx)) }

    var sessions by remember { mutableStateOf(store.list()) }
    var stage by remember { mutableStateOf(Stage.HOME) }
    var session by remember { mutableStateOf<Session?>(null) }
    var captureStartIndex by remember { mutableIntStateOf(0) }
    var backToReview by remember { mutableStateOf(false) }
    var pendingPatient by remember { mutableStateOf<PatientInfo?>(null) }
    var pendingPrint by remember { mutableStateOf(PrintSize.RAW) }
    var showGuide by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    fun openSession(patient: PatientInfo, printSize: PrintSize) {
        pendingPatient = patient
        pendingPrint = printSize
        if (hasCamera) {
            val created = store.createSession(patient, printSize)
            session = created
            captureStartIndex = 0
            backToReview = false
            stage = Stage.CAPTURE
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCamera = granted
        val patient = pendingPatient
        if (granted && patient != null) {
            val created = store.createSession(patient, pendingPrint)
            session = created
            captureStartIndex = 0
            backToReview = false
            stage = Stage.CAPTURE
        } else if (!granted) {
            Toast.makeText(ctx, "برای عکاسی، دسترسی به دوربین لازم است", Toast.LENGTH_LONG).show()
        }
    }

    when (stage) {
        Stage.HOME -> HomeScreen(
            sessions = sessions,
            onStart = { patient, print ->
                pendingPatient = patient
                pendingPrint = print
                if (hasCamera) {
                    openSession(patient, print)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onOpen = { info ->
                session = store.loadSession(info)
                stage = Stage.REVIEW
            },
            onDelete = { info ->
                store.delete(info.dir)
                sessions = store.list()
                Toast.makeText(ctx, "جلسه حذف شد", Toast.LENGTH_SHORT).show()
            },
            onThreeD = {
                val latest = sessions.firstOrNull()
                if (latest == null) {
                    Toast.makeText(ctx, "هنوز جلسه‌ای برای نمایش سه‌بعدی وجود ندارد", Toast.LENGTH_LONG).show()
                } else {
                    val loaded = store.loadSession(latest)
                    loaded.loadLandmarks()
                    session = loaded
                    stage = Stage.THREE_D
                }
            },
            onAbout = { stage = Stage.ABOUT },
            onGuide = { showGuide = true },
            onWipeAll = { confirmWipe = true },
        )

        Stage.CAPTURE -> {
            val current = session
            if (current == null) {
                stage = Stage.HOME
            } else {
                CaptureScreen(
                    session = current,
                    startIndex = captureStartIndex,
                    backToReview = backToReview,
                    onFinished = { finished ->
                        session = finished
                        sessions = store.list()
                        stage = Stage.REVIEW
                    },
                    onCancel = {
                        sessions = store.list()
                        stage = if (backToReview) Stage.REVIEW else Stage.HOME
                    },
                )
            }
        }

        Stage.REVIEW -> {
            val current = session
            if (current == null) {
                stage = Stage.HOME
            } else {
                ReviewScreen(
                    session = current,
                    onRetake = { index ->
                        captureStartIndex = index
                        backToReview = true
                        stage = Stage.CAPTURE
                    },
                    onExportGallery = {
                        val files = mutableListOf<java.io.File>()
                        CLINIC_SHOTS.forEach { files.add(current.fileFor(it)) }
                        Exporter.buildContactSheet(current)?.let { files.add(it) }
                        files.addAll(Exporter.buildPrintSized(current, current.printSize))
                        val ok = Exporter.exportToGallery(ctx, files)
                        Toast.makeText(ctx, "$ok فایل به گالری منتقل شد", Toast.LENGTH_LONG).show()
                    },
                    onShare = {
                        val files = CLINIC_SHOTS.map { current.fileFor(it) }.filter { it.exists() }
                        if (files.isEmpty()) {
                            Toast.makeText(ctx, "هنوز عکسی ثبت نشده است", Toast.LENGTH_SHORT).show()
                        } else {
                            ctx.startActivity(Exporter.shareIntent(ctx, files))
                        }
                    },
                    onDelete = {
                        store.delete(current.dir)
                        sessions = store.list()
                        session = null
                        stage = Stage.HOME
                    },
                    onBack = {
                        sessions = store.list()
                        stage = Stage.HOME
                    },
                    onThreeD = {
                        current.loadLandmarks()
                        stage = Stage.THREE_D
                    },
                )
            }
        }

        Stage.THREE_D -> {
            val current = session
            if (current == null) {
                stage = Stage.HOME
            } else {
                ThreeDScreen(
                    session = current,
                    onBack = { stage = Stage.REVIEW },
                )
            }
        }

        Stage.ABOUT -> {
            AboutScreen(onBack = { stage = Stage.HOME })
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("راهنمای استفاده") },
            text = {
                Text(
                    "۱) کد بیمار و رضایت‌نامه را وارد کنید و «شروع عکاسی ۹ نما» را بزنید.\n\n" +
                        "۲) بیمار را مقابل دوربین قرار دهید؛ اپ زاویه، فاصله، نور و تاری را " +
                        "کنترل می‌کند و وقتی وضعیت درست ۰٫۹ ثانیه بماند، خودکار عکس می‌گیرد.\n\n" +
                        "۳) در نمای ۹ (کلوزآپ بینی) دکمه‌ی بزرگ وسط صفحه را بزنید.\n\n" +
                        "۴) در صفحه‌ی بازبینی می‌توانید خروجی بگیرید یا از منوی همبرگری " +
                        "«نمایش سه‌بعدی چهره» را باز کنید و مدل را بچرخانید.\n\n" +
                        "۵) برای هر گوشی یک‌بار «کالیبراسیون» را در صفحه‌ی عکاسی انجام دهید.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) { Text("بستن") }
            },
        )
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("حذف همه‌ی داده‌ها") },
            text = {
                Text(
                    "همه‌ی عکس‌ها، مدل‌های سه‌بعدی و متادیتای همه‌ی جلسه‌ها برای همیشه " +
                        "پاک می‌شود. این عمل قابل بازگشت نیست.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    sessions.forEach { store.delete(it.dir) }
                    sessions = store.list()
                    session = null
                    Toast.makeText(ctx, "همه‌ی داده‌ها حذف شد", Toast.LENGTH_SHORT).show()
                }) { Text("حذف کن") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("انصراف") }
            },
        )
    }
}
