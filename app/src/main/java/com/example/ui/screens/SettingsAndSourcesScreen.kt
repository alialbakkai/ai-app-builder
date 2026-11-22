package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UserPreferences
import com.example.domain.model.IntegrityStatus
import com.example.domain.model.QaloonRecitersCatalog
import com.example.ui.theme.LocalTasmeeColors
import com.example.ui.theme.QaloonMushafFontFamily
import com.example.ui.theme.ThemePreference

@Composable
fun SettingsAndSourcesScreen(
    preferences: UserPreferences,
    integrityStatus: IntegrityStatus,
    onSetTheme: (ThemePreference) -> Unit,
    onAdjustFontDelta: (Float) -> Unit,
    onSetAutoScroll: (Boolean) -> Unit,
    onSetCloudVerification: (Boolean) -> Unit,
    onSelectReciter: (String) -> Unit,
    onWipeHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTasmeeColors.current
    var showWipeConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance & Mushaf Typography
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, semantic.subtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("المظهر وخط المصحف", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.themePreference == ThemePreference.SYSTEM,
                            onClick = { onSetTheme(ThemePreference.SYSTEM) },
                            label = { Text("تلقائي") },
                            modifier = Modifier.testTag("theme_system_chip")
                        )
                        FilterChip(
                            selected = preferences.themePreference == ThemePreference.LIGHT,
                            onClick = { onSetTheme(ThemePreference.LIGHT) },
                            label = { Text("نهاري دافئ") },
                            modifier = Modifier.testTag("theme_light_chip")
                        )
                        FilterChip(
                            selected = preferences.themePreference == ThemePreference.DARK,
                            onClick = { onSetTheme(ThemePreference.DARK) },
                            label = { Text("الوضع الليلي") },
                            modifier = Modifier.testTag("theme_dark_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "حجم الخط العثماني: ${preferences.mushafFontSizeSp.toInt()} sp",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = preferences.mushafFontSizeSp,
                        onValueChange = { target ->
                            onAdjustFontDelta(target - preferences.mushafFontSizeSp)
                        },
                        valueRange = 18f..38f
                    )
                    Text(
                        text = "اِ۬لْحَمْدُ لِلهِ رَبِّ اِ۬لْعَٰلَمِينَ ۝١",
                        fontFamily = QaloonMushafFontFamily,
                        fontSize = preferences.mushafFontSizeSp.sp,
                        lineHeight = (preferences.mushafFontSizeSp * 1.6f).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("التمرير التلقائي للآيات", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "يتابع الانتقال تلقائيًا نحو الآية الجارية",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.autoScrollEnabled,
                            onCheckedChange = onSetAutoScroll
                        )
                    }
                }
            }
        }

        // Reference Qaloon Reciter
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, semantic.subtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("قارئ التلاوة المرجعية (برواية قالون)", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    QaloonRecitersCatalog.reciters.forEach { reciter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectReciter(reciter.id) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.selectedReciterId == reciter.id,
                                onClick = { onSelectReciter(reciter.id) }
                            )
                            Column {
                                Text(reciter.nameAr, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    reciter.riwayahLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Privacy & Cloud Hybrid Processing
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, semantic.subtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("الخصوصية والأمان", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("التحقق الذكي السحابي", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "عند التفعيل، تُرسل فرضيات عدم اليقين أو مقاطع صوتية مؤقتة عبر Firebase AI Logic للتحقق من أخطاء قالون دون حفظها.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.cloudVerificationEnabled,
                            onCheckedChange = onSetCloudVerification,
                            modifier = Modifier.testTag("cloud_verification_switch")
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Text(
                        text = "• تخزين التسجيلات الصوتية: معطّل افتراضيًا.\n• سجل الجلسات والأخطاء: محفوظ محليًا على الجهاز فقط (Room).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showWipeConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_wipe_history_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null)
                        Text(" مسح جميع بيانات الجلسات المحلية")
                    }
                }
            }
        }

        // Verified Source of Truth & SHA-256 Integrity
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, semantic.subtleBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            tint = if (integrityStatus.isVerified) semantic.correctWord else semantic.errorWord,
                            modifier = Modifier.size(22.dp)
                        )
                        Text("المرجعية القرآنية وسلامة البيانات", style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "المصدر: ${integrityStatus.sourceCitation}\n" +
                            "الرواية: ${integrityStatus.riwayah}\n" +
                            "الطريق: ${integrityStatus.tariq}\n" +
                            "نظام العد: ${integrityStatus.verseCountSystem} (${integrityStatus.ayahCount} آية / ${integrityStatus.surahCount} سورة)\n" +
                            "بصمة SHA-256:\n${integrityStatus.sha256Hex}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "لا يولد التطبيق أي نص قرآني عبر النماذج اللغوية، ولا يدّعي تقييم التجويد الآلي أو الاستغناء عن المقرئ المتقن.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("مسح السجل المحلي") },
            text = { Text("سيتم حذف جميع تقارير الجلسات وجدول المراجعة المتباعدة نهائيًا من الجهاز.") },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        onWipeHistory()
                    }
                ) {
                    Text("تأكيد المسح")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
