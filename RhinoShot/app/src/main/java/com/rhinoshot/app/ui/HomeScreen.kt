package com.rhinoshot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rhinoshot.app.data.CLINIC_SHOTS
import com.rhinoshot.app.data.PatientInfo
import com.rhinoshot.app.data.PrintSize
import com.rhinoshot.app.data.SessionInfo
import com.rhinoshot.app.data.SessionStore

@Composable
fun HomeScreen(
    sessions: List<SessionInfo>,
    onStart: (PatientInfo, PrintSize) -> Unit,
    onOpen: (SessionInfo) -> Unit,
    onDelete: (SessionInfo) -> Unit,
    onThreeD: () -> Unit = {},
    onAbout: () -> Unit = {},
    onGuide: () -> Unit = {},
    onWipeAll: () -> Unit = {},
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var printSize by remember { mutableStateOf(PrintSize.RAW) }
    var consent by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "منو",
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("نمایش سه‌بعدی چهره") },
                            onClick = { menuOpen = false; onThreeD() },
                        )
                        DropdownMenuItem(
                            text = { Text("راهنمای استفاده") },
                            onClick = { menuOpen = false; onGuide() },
                        )
                        DropdownMenuItem(
                            text = { Text("درباره‌ی اپ و توسعه‌دهنده") },
                            onClick = { menuOpen = false; onAbout() },
                        )
                        DropdownMenuItem(
                            text = { Text("حذف همه‌ی داده‌ها", color = ErrorRed) },
                            onClick = { menuOpen = false; onWipeAll() },
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "RhinoShot",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "عکاسی استاندارد ۹ نما برای پرونده‌ی جراحی بینی",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                }
            }
        }

        item {
            PanelCard("مشخصات بیمار") {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("کد بیمار (اجباری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام بیمار (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیح (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("اندازه‌ی خروجی چاپ", color = MutedText, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrintSize.entries.forEach { size ->
                        FilterChip(
                            selected = printSize == size,
                            onClick = { printSize = size },
                            label = { Text(size.label) },
                        )
                    }
                }
            }
        }

        item {
            PanelCard("رضایت بیمار") {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "بیمار از ثبت تصاویر چهره و نگهداری آن‌ها در پرونده‌ی پزشکی خود آگاه است و " +
                            "با این کار موافقت می‌کند. تصاویر فقط روی همین دستگاه نگهداری می‌شود.",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onStart(PatientInfo(code.trim(), name.trim(), note.trim()), printSize) },
                enabled = code.isNotBlank() && consent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("شروع عکاسی ۹ نما", modifier = Modifier.padding(vertical = 6.dp))
            }
        }

        item {
            PanelCard("نماهای فرم کلینیک") {
                CLINIC_SHOTS.forEachIndexed { index, shot ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            "${index + 1}",
                            color = Color(0xFF4DB6AC),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp),
                        )
                        Text(
                            shot.title,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            shot.side,
                            color = MutedText,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        item {
            Text(
                "جلسه‌های قبلی",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    "هنوز جلسه‌ای ثبت نشده است.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(sessions, key = { it.dir.absolutePath }) { info ->
                PanelCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                info.patientCode,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                SessionStore.formatDate(info.createdAt) + "  •  ${info.shotCount} عکس",
                                color = MutedText,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        OutlinedButton(onClick = { onOpen(info) }) { Text("باز کردن") }
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { onDelete(info) }) {
                            Text("حذف", color = ErrorRed)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "تصاویر در حافظه‌ی خصوصی برنامه ذخیره می‌شوند و تنها با اقدام صریح شما به گالری یا " +
                    "برنامه‌ی دیگری منتقل می‌شوند. حذف یک جلسه، فایل‌ها را کامل پاک می‌کند.",
                color = MutedText,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
