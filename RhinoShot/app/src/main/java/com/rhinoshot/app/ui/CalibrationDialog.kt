package com.rhinoshot.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rhinoshot.app.vision.Calibration
import com.rhinoshot.app.vision.FaceState
import kotlin.math.abs

/**
 * One-off per-device setup: it removes the personal offset of the patient's head
 * and teaches the app which way "right" and "up" are on this phone.
 */
@Composable
fun CalibrationDialog(
    state: FaceState?,
    front: Boolean,
    initial: Calibration,
    onDismiss: () -> Unit,
    onSave: (Calibration) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var calibration by remember { mutableStateOf(initial) }
    var neutralYaw by remember { mutableFloatStateOf(0f) }
    var neutralPitch by remember { mutableFloatStateOf(0f) }

    val detected = state?.detected == true
    val yawNow = state?.yawRaw ?: 0f
    val pitchNow = state?.pitchRaw ?: 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیم جهت‌ها (یک‌بار برای هر گوشی)") },
        text = {
            Column {
                when (step) {
                    0 -> {
                        Text(
                            "سر بیمار را کاملاً مستقیم رو به دوربین نگه دارید و «ثبت زاویه‌ی صفر» را بزنید.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val s = state ?: return@Button
                                neutralYaw = s.yawRaw
                                neutralPitch = s.pitchRaw
                                calibration = calibration.withZero(s.yawRaw, s.pitchRaw, front)
                                step = 1
                            },
                            enabled = detected,
                        ) {
                            Text("ثبت زاویه‌ی صفر")
                        }
                    }

                    1 -> {
                        Text(
                            "حالا از بیمار بخواهید سرش را به سمت راست خودش بچرخاند. " +
                                "وقتی زاویه به‌اندازه‌ی کافی چرخید، دکمه را بزنید.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "زاویه‌ی فعلی: ${yawNow.toInt()}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val sign = if (yawNow >= 0) 1 else -1
                                calibration = calibration
                                    .withYawSign(front, sign)
                                    .withZero(neutralYaw, neutralPitch, front)
                                step = 2
                            },
                            enabled = detected && abs(yawNow) > 18f,
                        ) {
                            Text("ثبت جهت راست")
                        }
                    }

                    2 -> {
                        Text(
                            "در آخر از بیمار بخواهید چانه را بالا ببرد تا جهت عمودی هم مشخص شود.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "زاویه‌ی فعلی: ${pitchNow.toInt()}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val sign = if (pitchNow >= 0) 1 else -1
                                calibration = calibration
                                    .copy(pitchSign = sign)
                                    .withZero(neutralYaw, neutralPitch, front)
                                step = 3
                            },
                            enabled = detected && abs(pitchNow) > 12f,
                        ) {
                            Text("ثبت جهت بالا")
                        }
                    }

                    else -> {
                        Text(
                            "تنظیم انجام شد. این مقدارها برای همیشه روی این گوشی ذخیره می‌مانند.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OkGreen,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (step >= 3) {
                TextButton(onClick = { onSave(calibration) }) { Text("ذخیره") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        },
    )
}
