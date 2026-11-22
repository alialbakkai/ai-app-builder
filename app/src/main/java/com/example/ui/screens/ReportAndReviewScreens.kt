package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AyahReviewScheduleEntity
import com.example.data.local.RecitationSessionEntity
import com.example.ui.theme.LocalTasmeeColors
import com.example.ui.theme.QaloonMushafFontFamily
import com.example.ui.viewmodel.SessionReportDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportScreen(
    report: SessionReportDetail?,
    resolveWord: (String) -> String,
    onBack: () -> Unit,
    onPracticeErrors: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val semantic = LocalTasmeeColors.current

    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("report_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
                Text(
                    text = "تقرير جلسة التسميع",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        if (report == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("لا يوجد تقرير محدد للعرض.", style = MaterialTheme.typography.bodyLarge)
            }
            return
        }

        val s = report.session
        val m = report.metrics

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    border = BorderStroke(1.dp, semantic.subtleBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = s.scopeTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${s.modeName} • ${formatArabicDate(s.endedAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReportMetricTile("الدقة", "${s.accuracyPercent}%", Modifier.weight(1f))
                            ReportMetricTile("الصحيحة", "${s.correctWords}", Modifier.weight(1f))
                            ReportMetricTile("الأخطاء", "${s.confirmedErrorsCount}", Modifier.weight(1f))
                            ReportMetricTile("تصحيح ذاتي", "${s.selfCorrectedCount}", Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "معدل خطأ الكلمة (WER): ${"%.2f".format(m.estimatedWer * 100)}% • زمن الاستجابة: ${m.avgCorrectionLatencyMs} ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (s.aiSummary.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, semantic.goldAccent.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "ملخص تحليلي",
                                tint = semantic.goldAccent
                            )
                            Column {
                                Text(
                                    text = "توصية المحلل التعليمي",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = s.aiSummary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = if (report.errors.isEmpty()) "لم تُسجّل أخطاء مؤكدة في هذه الجلسة" else "تفاصيل المواضع المرصودة",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (report.errors.isEmpty()) {
                item {
                    Surface(
                        color = semantic.correctWordContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = semantic.correctWord)
                            Text(
                                text = "ما شاء الله! تلاوة متقنة وفق نص قالون المعتمد.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else {
                items(report.errors) { err ->
                    val refWord = resolveWord(err.referenceTokenId)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, semantic.errorWord.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = semantic.errorWord,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${err.errorTypeLabelAr} • آية ${err.ayahNo}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                if (err.resolvedBySelfCorrection) {
                                    Text(
                                        text = "صُحّح ذاتيًا",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = semantic.correctWord
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "الكلمة الصحيحة من المصحف: $refWord",
                                fontFamily = QaloonMushafFontFamily,
                                fontSize = 22.sp,
                                color = semantic.correctWord
                            )
                            if (err.spokenHypothesis.isNotBlank()) {
                                Text(
                                    text = "ما التقطه التعرف الصوتي: ${err.spokenHypothesis}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onPracticeErrors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("practice_errors_from_report_button")
                    ) {
                        Text("مراجعة مواضع الأخطاء الآن")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReviewAndHistoryScreen(
    sessions: List<RecitationSessionEntity>,
    schedules: List<AyahReviewScheduleEntity>,
    onStartDueReview: () -> Unit,
    onOpenReport: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onWipeAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showWipeDialog by remember { mutableStateOf(false) }
    val semantic = LocalTasmeeColors.current

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("التكرار المتباعد (${schedules.size})") },
                icon = { Icon(Icons.Filled.Schedule, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("سجل الجلسات (${sessions.size})") },
                icon = { Icon(Icons.Filled.History, contentDescription = null) }
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (schedules.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "لا توجد آيات مجدولة للمراجعة حاليًا",
                            subtitle = "عند ظهور أي خطأ أثناء التسميع، تُضاف الآية تلقائيًا لخطة التكرار المتباعد SM-2."
                        )
                    }
                } else {
                    item {
                        Button(
                            onClick = onStartDueReview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("review_tab_start_button")
                        ) {
                            Text("بدء جلسة مراجعة الآيات المستحقة")
                        }
                    }
                    items(schedules, key = { it.ayahKey }) { item ->
                        val isDue = item.nextReviewAt <= System.currentTimeMillis()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                1.dp,
                                if (isDue) semantic.uncertainWord else semantic.subtleBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "سورة ${item.surahNameAr} • الآية ${item.ayahNo}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "مرات الخطأ: ${item.totalErrorsCount} • الفاصل الحالي: ${item.intervalDays} يوم",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (isDue) "مستحقة الآن" else "مجدولة",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isDue) semantic.uncertainWord else semantic.correctWord
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (sessions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "سجل الجلسات فارغ",
                            subtitle = "ابدأ جلسة تسميع جديدة لحفظ تقدمك محليًا على الجهاز."
                        )
                    }
                } else {
                    item {
                        OutlinedButton(
                            onClick = { showWipeDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wipe_history_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("حذف سجل الجلسات بالكامل")
                        }
                    }
                    items(sessions, key = { it.id }) { session ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, semantic.subtleBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.scopeTitle,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${session.modeName} • دقة ${session.accuracyPercent}% • أخطاء ${session.confirmedErrorsCount}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatArabicDate(session.endedAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onOpenReport(session.id) }) {
                                    Text("التقرير")
                                }
                                IconButton(onClick = { onDeleteSession(session.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "حذف الجلسة"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("تأكيد مسح السجل") },
            text = { Text("هل تريد حذف جميع جلسات التسميع وجدول المراجعة المتباعدة من هذا الجهاز؟ لا يؤثر هذا على نص المصحف.") },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeDialog = false
                        onWipeAllHistory()
                    }
                ) {
                    Text("حذف الكل")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatArabicDate(timestampMs: Long): String {
    return try {
        SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar")).format(Date(timestampMs))
    } catch (_: Exception) {
        ""
    }
}
