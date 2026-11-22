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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AyahReviewScheduleEntity
import com.example.data.local.RecitationSessionEntity
import com.example.domain.model.IntegrityStatus
import com.example.domain.model.RecitationMode
import com.example.domain.model.SurahInfo
import com.example.ui.theme.LocalTasmeeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    integrityStatus: IntegrityStatus,
    surahs: List<SurahInfo>,
    dueReviews: List<AyahReviewScheduleEntity>,
    recentSessions: List<RecitationSessionEntity>,
    onStartFreeMode: () -> Unit,
    onStartSurahMode: (surahNo: Int, mode: RecitationMode) -> Unit,
    onStartJozz: (jozz: Int) -> Unit,
    onStartHizb: (hizb: Int) -> Unit,
    onStartReviewErrors: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTasmeeColors.current
    var pickerMode by remember { mutableStateOf<RecitationMode?>(null) }
    var showJuzHizbSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Mushaf Identity Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                ),
                border = BorderStroke(1.dp, semantic.subtleBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "تحقق سلامة النص القرآني",
                            tint = if (integrityStatus.isVerified) semantic.correctWord else semantic.errorWord,
                            modifier = Modifier
                                .size(24.dp)
                                .then(
                                    if (integrityStatus.isVerified) Modifier.testTag("mushaf_verified_badge")
                                    else Modifier
                                )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تطبيق تسميع — برواية قالون عن نافع",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (integrityStatus.isVerified) {
                                    "نص مجمع الملك فهد المعتمد (${integrityStatus.surahCount} سورة • ${integrityStatus.ayahCount} آية • SHA-256 موثق)"
                                } else {
                                    integrityStatus.errorMessage ?: "جارٍ التحقق من سلامة المصحف..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatBadge(
                            label = "الجلسات",
                            value = "${recentSessions.size}",
                            modifier = Modifier.weight(1f)
                        )
                        StatBadge(
                            label = "آيات للمراجعة",
                            value = "${dueReviews.size}",
                            modifier = Modifier.weight(1f)
                        )
                        StatBadge(
                            label = "العد المعتمد",
                            value = "المدني",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Due Spaced Repetition Banner
        if (dueReviews.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("due_reviews_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = semantic.uncertainWordContainer
                    ),
                    border = BorderStroke(1.dp, semantic.goldAccent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "لديك ${dueReviews.size} آية مستحقة للمراجعة اليوم",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "مجدولة بنظام التكرار المتباعد لتثبيت المواضع المتكررة",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onStartReviewErrors,
                            modifier = Modifier.testTag("start_due_review_button")
                        ) {
                            Text("ابدأ المراجعة")
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "اختر وضع التسميع",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 6 Recitation Modes
        item {
            val modeItems = listOf(
                ModeCardData(
                    mode = RecitationMode.FREE_RECITATION,
                    icon = Icons.Filled.GraphicEq,
                    testTag = "mode_free_card",
                    onClick = onStartFreeMode
                ),
                ModeCardData(
                    mode = RecitationMode.SPECIFIC_SURAH,
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    testTag = "mode_surah_card",
                    onClick = { pickerMode = RecitationMode.SPECIFIC_SURAH }
                ),
                ModeCardData(
                    mode = RecitationMode.JUZ_OR_HIZB,
                    icon = Icons.Filled.Bookmark,
                    testTag = "mode_juz_hizb_card",
                    onClick = { showJuzHizbSheet = true }
                ),
                ModeCardData(
                    mode = RecitationMode.HIDDEN_MEMORIZATION,
                    icon = Icons.Filled.VisibilityOff,
                    testTag = "mode_hidden_card",
                    onClick = { pickerMode = RecitationMode.HIDDEN_MEMORIZATION }
                ),
                ModeCardData(
                    mode = RecitationMode.TRAINING_MUSHAF,
                    icon = Icons.Filled.AutoAwesome,
                    testTag = "mode_training_card",
                    onClick = { pickerMode = RecitationMode.TRAINING_MUSHAF }
                ),
                ModeCardData(
                    mode = RecitationMode.REVIEW_PAST_ERRORS,
                    icon = Icons.Filled.History,
                    testTag = "mode_review_card",
                    onClick = onStartReviewErrors
                )
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                modeItems.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { item ->
                            ModeSelectionCard(
                                data = item,
                                enabled = integrityStatus.isVerified,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Non-Muqri Disclaimer & Privacy Note
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "تنبيه",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "التطبيق مساعد للحفظ والمراجعة برواية قالون عن نافع ولا يغني عن المقرئ المتخصص. التسجيل الصوتي معطّل افتراضيًا.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Surah Picker Bottom Sheet
    pickerMode?.let { targetMode ->
        SurahPickerBottomSheet(
            title = targetMode.titleAr,
            surahs = surahs,
            onDismiss = { pickerMode = null },
            onSelectSurah = { surahNo ->
                pickerMode = null
                onStartSurahMode(surahNo, targetMode)
            }
        )
    }

    // Juz / Hizb Picker Bottom Sheet
    if (showJuzHizbSheet) {
        JuzHizbPickerBottomSheet(
            onDismiss = { showJuzHizbSheet = false },
            onSelectJozz = { jozz ->
                showJuzHizbSheet = false
                onStartJozz(jozz)
            },
            onSelectHizb = { hizb ->
                showJuzHizbSheet = false
                onStartHizb(hizb)
            }
        )
    }
}

private data class ModeCardData(
    val mode: RecitationMode,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun ModeSelectionCard(
    data: ModeCardData,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val semantic = LocalTasmeeColors.current
    Card(
        modifier = modifier
            .heightIn(min = 136.dp)
            .testTag(data.testTag)
            .clickable(enabled = enabled, onClick = data.onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, semantic.subtleBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = data.icon,
                    contentDescription = data.mode.titleAr,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(22.dp)
                )
            }
            Text(
                text = data.mode.titleAr,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = data.mode.descAr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SurahPickerBottomSheet(
    title: String,
    surahs: List<SurahInfo>,
    onDismiss: () -> Unit,
    onSelectSurah: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, surahs) {
        if (query.isBlank()) surahs
        else surahs.filter {
            it.nameAr.contains(query, ignoreCase = true) ||
                it.nameEn.contains(query, ignoreCase = true) ||
                it.surahNo.toString() == query.trim()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "$title — اختر السورة",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("surah_search_input"),
                placeholder = { Text("ابحث باسم السورة أو رقمها...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "بحث") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.surahNo }) { surah ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("surah_item_${surah.surahNo}")
                            .clickable { onSelectSurah(surah.surahNo) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${surah.surahNo}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "سورة ${surah.nameAr}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "عدد الآيات: ${surah.ayahCount} • الجزء ${surah.startJozz}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "صفحة ${surah.startPage}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JuzHizbPickerBottomSheet(
    onDismiss: () -> Unit,
    onSelectJozz: (Int) -> Unit,
    onSelectHizb: (Int) -> Unit
) {
    var selectHizbTab by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "تسميع جزء أو حزب",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !selectHizbTab,
                    onClick = { selectHizbTab = false },
                    label = { Text("الأجزاء (٣٠)") }
                )
                FilterChip(
                    selected = selectHizbTab,
                    onClick = { selectHizbTab = true },
                    label = { Text("الأحزاب (٦٠)") }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val itemsList = if (selectHizbTab) (1..60).toList() else (1..30).toList()
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(itemsList) { num ->
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(if (selectHizbTab) "hizb_item_$num" else "juz_item_$num")
                            .clickable {
                                if (selectHizbTab) onSelectHizb(num) else onSelectJozz(num)
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (selectHizbTab) "حزب $num" else "جزء $num",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
