package com.rhinoshot.app.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rhinoshot.app.data.AppInfo
import com.rhinoshot.app.data.CLINIC_SHOTS
import com.rhinoshot.app.data.Session
import com.rhinoshot.app.data.Shot
import com.rhinoshot.app.util.FaceMesh
import java.io.File

/**
 * Shows the reconstructed 3D face of the session. The model can be rotated
 * with a finger so the nose can be inspected from every direction.
 */
@Composable
fun ThreeDScreen(
    session: Session,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val topology = remember { FaceMesh.loadTopology(ctx) }
    var selected by remember { mutableStateOf(session.records.firstOrNull()?.shotId ?: "frontal") }
    var wireframe by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var meshError by remember { mutableStateOf<String?>(null) }

    val available = session.records.filter { session.landmarks[it.shotId] != null }
    val glView = remember { FaceGlView(ctx) }
    var currentMesh by remember { mutableStateOf<FaceMesh.Mesh?>(null) }

    LaunchedEffect(selected, topology) {
        val topo = topology
        val points = session.landmarks[selected]
        if (topo == null) {
            currentMesh = null
            meshError = "فایل مدل مرجع چهره (canonical_face_model.bin) پیدا نشد."
            return@LaunchedEffect
        }
        if (points == null) {
            currentMesh = null
            meshError = "برای این نما نقاط چهره ذخیره نشده است؛ دوباره عکس بگیرید."
            return@LaunchedEffect
        }
        val mesh = FaceMesh.build(topo, points, session.aspects[selected] ?: 1f)
        if (mesh == null) {
            currentMesh = null
            meshError = "ساخت مدل سه‌بعدی از این عکس ممکن نشد."
        } else {
            currentMesh = mesh
            meshError = null
            glView.setMesh(mesh)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("بازگشت") }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "نمایش سه‌بعدی چهره",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    "با کشیدن انگشت، مدل بچرخد؛ با دو انگشت زوم کنید",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
        }

        AndroidView(
            factory = { glView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        if (meshError != null) {
            Text(
                meshError!!,
                style = MaterialTheme.typography.labelMedium,
                color = ErrorRed,
            )
        }

        if (available.isEmpty()) {
            PanelCard("مدلی موجود نیست") {
                Text(
                    "برای ساخت مدل سه‌بعدی باید حداقل یک عکس در همین جلسه ثبت شده باشد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(available, key = { it.shotId }) { record ->
                    val shot = CLINIC_SHOTS.firstOrNull { it.id == record.shotId }
                    FilterChip(
                        selected = selected == record.shotId,
                        onClick = { selected = record.shotId },
                        label = { Text(shot?.title ?: record.shotId) },
                    )
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { glView.resetView() }) { Text("نمای اولیه", maxLines = 1) }
                OutlinedButton(onClick = {
                    currentMesh?.let { glView.focusNose(it) }
                }) { Text("تمرکز روی بینی", maxLines = 1) }
                OutlinedButton(onClick = {
                    wireframe = !wireframe
                    glView.setWireframe(wireframe)
                }) { Text(if (wireframe) "سطح" else "تور", maxLines = 1) }
                OutlinedButton(onClick = {
                    val mesh = currentMesh
                    val shot = CLINIC_SHOTS.firstOrNull { it.id == selected }
                    if (mesh == null || shot == null) {
                        message = "مدلی برای این نما موجود نیست"
                    } else {
                        val file = writeObj(session, shot, mesh)
                        message = "فایل سه‌بعدی ساخته شد: ${file.name}"
                    }
                }) { Text("خروجی OBJ", maxLines = 1) }
            }

            if (message != null) {
                Text(message!!, style = MaterialTheme.typography.labelMedium, color = OkGreen)
            }

            PanelCard("راهنما") {
                Text(
                    "مدل از روی نقاط تشخیص‌داده‌شده‌ی چهره در عکس «${shotTitle(selected)}» ساخته می‌شود " +
                        "و یک بازسازی تقریبی (۲٫۵ بعدی) است؛ برای اندازه‌گیری دقیق جراحی " +
                        "جایگزین عکس‌برداری استاندارد نیست.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
        }
    }
}

private fun shotTitle(shotId: String): String =
    CLINIC_SHOTS.firstOrNull { it.id == shotId }?.title ?: shotId

private fun writeObj(session: Session, shot: Shot, mesh: FaceMesh.Mesh): File {
    val folder = File(session.dir, "model_3d").apply { mkdirs() }
    val file = File(folder, "${shot.fileName}_3d.obj")
    file.writeText(FaceMesh.toObj(mesh, shot.fileName))
    return file
}
