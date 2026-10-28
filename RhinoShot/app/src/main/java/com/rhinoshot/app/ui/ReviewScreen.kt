package com.rhinoshot.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhinoshot.app.data.CLINIC_SHOTS
import com.rhinoshot.app.data.Session
import com.rhinoshot.app.data.SessionStore
import com.rhinoshot.app.util.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReviewScreen(
    session: Session,
    onRetake: (Int) -> Unit,
    onExportGallery: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onThreeD: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "پیش‌نمایش جلسه",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${session.patient.code}  •  ${SessionStore.formatDate(session.createdAt)}  •  " +
                "${session.capturedCount()} از ${CLINIC_SHOTS.size} عکس",
            color = MutedText,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(CLINIC_SHOTS) { index, shot ->
                ShotCell(
                    file = session.fileFor(shot),
                    title = shot.title,
                    captured = session.recordFor(shot.id) != null,
                    onClick = { onRetake(index) },
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onExportGallery, modifier = Modifier.weight(1f)) {
                Text("خروجی به گالری")
            }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Text("اشتراک‌گذاری")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(onClick = onThreeD, modifier = Modifier.weight(1f)) {
                Text("نمایش سه‌بعدی")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("بازگشت")
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.weight(1f),
            ) {
                Text("حذف کامل جلسه", color = ErrorRed)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف کامل جلسه") },
            text = {
                Text(
                    "همه‌ی عکس‌ها و اطلاعات این جلسه برای همیشه از حافظه‌ی دستگاه پاک می‌شود. " +
                        "این عمل قابل بازگشت نیست."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("حذف کن", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("انصراف") }
            },
        )
    }
}

@Composable
private fun ShotCell(
    file: java.io.File,
    title: String,
    captured: Boolean,
    onClick: () -> Unit,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.absolutePath) {
        bitmap = withContext(Dispatchers.IO) { Exporter.loadOriented(file, maxSize = 400) }
    }

    Column(
        modifier = Modifier
            .background(Color(0xFF161E24), androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .background(Color(0xFF0D1317), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("—", color = MutedText, fontSize = 22.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Text(
            if (captured) "ثبت شد" else "ثبت نشده",
            color = if (captured) OkGreen else WarnAmber,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
        )
    }
}
