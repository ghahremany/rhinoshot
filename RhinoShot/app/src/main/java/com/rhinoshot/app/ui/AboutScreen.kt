package com.rhinoshot.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhinoshot.app.data.AppInfo

/**
 * The About screen: app information, the developer credits and the licenses of
 * the third party components.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("بازگشت") }
            Spacer(Modifier.width(8.dp))
            Text(
                "درباره‌ی اپ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        PanelCard("برنامه") {
            InfoLine("نام", AppInfo.APP_NAME)
            InfoLine("نسخه", "${AppInfo.APP_VERSION} (کد ${AppInfo.VERSION_CODE})")
            InfoLine("شناسه‌ی بسته", AppInfo.PACKAGE)
            InfoLine("نسخه‌ی iOS", "${AppInfo.IOS_BUNDLE_ID}  •  ${AppInfo.APP_VERSION}")
            InfoLine("حداقل اندروید", AppInfo.MIN_ANDROID)
        }

        PanelCard("توسعه‌دهنده") {
            Text(
                AppInfo.DEVELOPER_NAME,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                AppInfo.DEVELOPER_ROLE,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                AppInfo.DEVELOPER_APP,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
            Spacer(Modifier.height(6.dp))
            InfoLine("اپ", AppInfo.DEVELOPER_APP_PACKAGE)
            InfoLine("تماس", AppInfo.DEVELOPER_PHONE)
            InfoLine("وب‌سایت", AppInfo.DEVELOPER_SITE)
            Spacer(Modifier.height(6.dp))
            Text(
                "گیت‌هاب (پروژه):\n${AppInfo.DEVELOPER_GITHUB}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF80CBC4),
            )
            Text(
                "گیت‌هاب (پروفایل):\n${AppInfo.DEVELOPER_PROFILE}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF80CBC4),
            )
            if (AppInfo.DEVELOPER_EMAIL.isNotBlank()) {
                Text(
                    AppInfo.DEVELOPER_EMAIL,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF80CBC4),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = { openLink(ctx, AppInfo.DEVELOPER_GITHUB) }) {
                    Text("مشاهده در گیت‌هاب")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { openLink(ctx, AppInfo.DEVELOPER_SITE) }) {
                    Text("وب‌سایت مرجع")
                }
            }
        }

        PanelCard("قابلیت‌ها") {
            Text(
                "• عکاسی هدایت‌شده‌ی ۹ نما با کنترل زاویه، فاصله، نور و تاری\n" +
                    "• شاتر خودکار وقتی وضعیت درست ۰٫۹ ثانیه حفظ شود\n" +
                    "• کالیبراسیون جهت‌ها برای هر گوشی (یک‌بار)\n" +
                    "• بازسازی سه‌بعدی چهره و چرخش دستی برای بررسی بینی\n" +
                    "• خروجی چاپی ۱۰×۱۵ و ۱۳×۱۸ در ۳۰۰ dpi\n" +
                    "• ذخیره‌ی خصوصی، بدون دسترسی شبکه، با حذف کامل",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
        }

        PanelCard("حریم خصوصی") {
            Text(
                AppInfo.PRIVACY_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
        }

        PanelCard("هشدار پزشکی") {
            Text(
                AppInfo.MEDICAL_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE0A458),
            )
        }

        PanelCard("پروانه‌ها و منابع") {
            AppInfo.LICENSES.forEach { license ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        license.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        license.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                    Text(
                        license.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF80CBC4),
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }

        Text(
            "${AppInfo.APP_NAME} ${AppInfo.APP_VERSION} — ساخته‌شده برای عکاسی استاندارد کلینیک جراحی بینی",
            style = MaterialTheme.typography.labelSmall,
            color = MutedText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MutedText,
            modifier = Modifier.width(110.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}

private fun openLink(context: android.content.Context, url: String) {
    try {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url),
        )
        context.startActivity(intent)
    } catch (t: Throwable) {
        // No browser available - ignore.
    }
}
