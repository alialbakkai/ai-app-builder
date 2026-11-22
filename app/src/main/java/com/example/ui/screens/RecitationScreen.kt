package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.QaloonAudioPlayer
import com.example.domain.model.QuranAyah
import com.example.domain.model.QuranWord
import com.example.domain.model.RecitationMode
import com.example.domain.model.RecitationState
import com.example.domain.model.WordVisualStatus
import com.example.ui.theme.LocalTasmeeColors
import com.example.ui.theme.QaloonMushafFontFamily
import com.example.ui.viewmodel.RecitationUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecitationScreen(
    uiState: RecitationUiState,
    micRmsLevel: Float,
    isSpeechDetected: Boolean,
    audioDiagnostic: String?,
    audioPlaybackState: QaloonAudioPlayer.PlaybackState,
    fontSizeSp: Float,
    autoScrollEnabled: Boolean,
    cloudVerificationEnabled: Boolean,
    onBack: () -> Unit,
    onStartOrResumeMic: () -> Unit,
    onPauseMic: () -> Unit,
    onRepeatAyah: () -> Unit,
    onRequestHint: () -> Unit,
    onToggleReferenceAudio: () -> Unit,
    onAdjustFontSize: (Float) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onJumpToAyah: (Int) -> Unit,
    onFinishSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val semantic = LocalTasmeeColors.current
    val listState = rememberLazyListState()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            onStartOrResumeMic()
        }
    }

    BackHandler {
        onPauseMic()
        onBack()
    }

    LaunchedEffect(uiState.currentAyahIndex, autoScrollEnabled) {
        if (autoScrollEnabled && uiState.ayahs.isNotEmpty() && uiState.currentAyahIndex in uiState.ayahs.indices) {
            listState.animateScrollToItem(uiState.currentAyahIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Distraction-free Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            onPauseMic()
                            onBack()
                        },
                        modifier = Modifier.testTag("recitation_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "العودة"
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.scopeTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val currentAyah = uiState.ayahs.getOrNull(uiState.currentAyahIndex)
                        Text(
                            text = if (currentAyah != null) {
                                "${uiState.mode.titleAr} • آية ${currentAyah.ayahNo} • صفحة ${currentAyah.page}"
                            } else {
                                uiState.mode.titleAr
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Connectivity / Hybrid Mode indicator
                    Icon(
                        imageVector = if (uiState.isOnline && cloudVerificationEnabled) {
                            Icons.Filled.CloudDone
                        } else {
                            Icons.Filled.CloudOff
                        },
                        contentDescription = if (uiState.isOnline && cloudVerificationEnabled) {
                            "التحقق السحابي متصل"
                        } else {
                            "الوضع المحلي"
                        },
                        tint = if (uiState.isOnline && cloudVerificationEnabled) {
                            semantic.correctWord
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(20.dp)
                    )

                    OutlinedButton(
                        onClick = onFinishSession,
                        modifier = Modifier.testTag("finish_session_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("إنهاء", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // State Machine & VAD Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StateMachineBadge(state = uiState.state)
                    if (uiState.isCloudVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    val animatedRms by animateFloatAsState(
                        targetValue = if (uiState.state != RecitationState.IDLE) micRmsLevel else 0f,
                        label = "rms"
                    )
                    LinearProgressIndicator(
                        progress = { animatedRms },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isSpeechDetected) semantic.correctWord else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }

        // Microphone Permission Card (shown if permission not granted yet)
        if (!hasMicPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = semantic.uncertainWordContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "إذن الميكروفون مطلوب للتسميع اللحظي",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "يستمع التطبيق لتلاوتك فقط لمطابقتها مع مصحف قالون، ولا يسجل أو يحفظ ملفات صوتية.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.testTag("grant_mic_permission_button")
                    ) {
                        Text("السماح بالميكروفون وبدء التسميع")
                    }
                }
            }
        }

        // Reference Audio Playback Bar
        AnimatedVisibility(visible = audioPlaybackState !is QaloonAudioPlayer.PlaybackState.Idle) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val label = when (audioPlaybackState) {
                        is QaloonAudioPlayer.PlaybackState.Loading ->
                            "جارٍ تحميل تلاوة ${audioPlaybackState.reciterName}..."
                        is QaloonAudioPlayer.PlaybackState.Playing ->
                            "تلاوة مرجعية (قالون): ${audioPlaybackState.reciterName}"
                        is QaloonAudioPlayer.PlaybackState.Error ->
                            audioPlaybackState.message
                        QaloonAudioPlayer.PlaybackState.Idle -> ""
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleReferenceAudio) {
                        Icon(Icons.Filled.Stop, contentDescription = "إيقاف التلاوة المرجعية")
                    }
                }
            }
        }

        // Live Correction / Uncertainty / Diagnostic Banner
        val bannerText = uiState.statusBannerMessage ?: audioDiagnostic
        AnimatedVisibility(visible = !bannerText.isNullOrBlank() || uiState.activeCorrectionWord != null) {
            val isError = uiState.state == RecitationState.CONFIRMED_ERROR ||
                uiState.state == RecitationState.WAITING_FOR_CORRECTION
            val isUncertain = uiState.state == RecitationState.SUSPECTED_ERROR
            val containerColor = when {
                isError -> semantic.errorWordContainer
                isUncertain -> semantic.uncertainWordContainer
                else -> semantic.correctWordContainer
            }
            val iconColor = when {
                isError -> semantic.errorWord
                isUncertain -> semantic.uncertainWord
                else -> semantic.correctWord
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("recitation_feedback_banner"),
                color = containerColor,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, iconColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isError -> Icons.Filled.ErrorOutline
                            isUncertain -> Icons.Filled.HelpOutline
                            else -> Icons.Filled.CheckCircle
                        },
                        contentDescription = null,
                        tint = iconColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        if (bannerText != null) {
                            Text(
                                text = bannerText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (uiState.activeCorrectionWord != null) {
                            Text(
                                text = "الكلمة الصحيحة من المصحف: ${uiState.activeCorrectionWord.displayUthmani}",
                                fontFamily = QaloonMushafFontFamily,
                                fontSize = (fontSizeSp * 0.9f).sp,
                                color = semantic.errorWord
                            )
                        }
                    }
                }
            }
        }

        // Mushaf Reading Canvas
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(
                items = uiState.ayahs,
                key = { _, ayah -> ayah.id }
            ) { idx, ayah ->
                val isCurrentAyah = idx == uiState.currentAyahIndex
                AyahMushafCard(
                    ayah = ayah,
                    isCurrentAyah = isCurrentAyah,
                    currentWordCursor = if (isCurrentAyah) uiState.currentWordCursor else -1,
                    isHiddenMode = uiState.mode == RecitationMode.HIDDEN_MEMORIZATION,
                    wordStatuses = uiState.wordStatuses,
                    fontSizeSp = fontSizeSp,
                    onClickAyah = { onJumpToAyah(idx) }
                )
            }
        }

        // Bottom Ergonomic Control Dock
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Secondary Tool Row (Font Zoom, Auto-Scroll, Repeat, Hint, Reference Audio)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onAdjustFontSize(-2f) },
                        modifier = Modifier.testTag("font_decrease_button")
                    ) {
                        Icon(Icons.Filled.TextDecrease, contentDescription = "تصغير الخط")
                    }
                    IconButton(
                        onClick = { onAdjustFontSize(2f) },
                        modifier = Modifier.testTag("font_increase_button")
                    ) {
                        Icon(Icons.Filled.TextIncrease, contentDescription = "تكبير الخط")
                    }
                    FilledTonalIconButton(
                        onClick = onToggleAutoScroll,
                        modifier = Modifier.testTag("auto_scroll_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerticalAlignCenter,
                            contentDescription = "التمرير التلقائي",
                            tint = if (autoScrollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRepeatAyah,
                        modifier = Modifier.testTag("repeat_ayah_button")
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = "إعادة الآية")
                    }
                    IconButton(
                        onClick = onRequestHint,
                        modifier = Modifier.testTag("hint_button")
                    ) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = "طلب تلميح")
                    }
                    IconButton(
                        onClick = onToggleReferenceAudio,
                        modifier = Modifier.testTag("reference_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "التلاوة المرجعية برواية قالون"
                        )
                    }
                }

                // Primary Mic Start / Pause / Resume Action
                val isListening = uiState.state != RecitationState.IDLE &&
                    uiState.state != RecitationState.COMPLETED
                Button(
                    onClick = {
                        if (!hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (isListening) {
                            onPauseMic()
                        } else {
                            onStartOrResumeMic()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("primary_mic_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Pause else Icons.Filled.Mic,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !hasMicPermission -> "السماح بالميكروفون والبدء"
                            isListening -> "إيقاف مؤقت للتسميع"
                            uiState.sessionStartedAt > 0L && uiState.wordStatuses.isNotEmpty() -> "استئناف التسميع"
                            else -> "بدء التسميع"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun StateMachineBadge(state: RecitationState) {
    val semantic = LocalTasmeeColors.current
    val (bg, fg) = when (state) {
        RecitationState.IDLE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        RecitationState.LISTENING,
        RecitationState.MATCHING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        RecitationState.CORRECT_RECITATION,
        RecitationState.RESUMING,
        RecitationState.COMPLETED -> semantic.correctWordContainer to semantic.correctWord
        RecitationState.SUSPECTED_ERROR -> semantic.uncertainWordContainer to semantic.uncertainWord
        RecitationState.CONFIRMED_ERROR,
        RecitationState.WAITING_FOR_CORRECTION -> semantic.errorWordContainer to semantic.errorWord
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = state.labelAr,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahMushafCard(
    ayah: QuranAyah,
    isCurrentAyah: Boolean,
    currentWordCursor: Int,
    isHiddenMode: Boolean,
    wordStatuses: Map<String, WordVisualStatus>,
    fontSizeSp: Float,
    onClickAyah: () -> Unit
) {
    val semantic = LocalTasmeeColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickAyah),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentAyah) {
                semantic.mushafPageGlow
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            }
        ),
        border = BorderStroke(
            width = if (isCurrentAyah) 1.5.dp else 1.dp,
            color = if (isCurrentAyah) semantic.goldAccent else semantic.subtleBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${ayah.surahNameAr} • الآية ${ayah.ayahEndNumberArabic}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (ayah.hasHizbMarker) {
                    Text(
                        text = "۞ الحزب ${ayah.hizb}",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.goldAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ayah.words.forEachIndexed { wIdx, word ->
                    val status = wordStatuses[word.referenceTokenId] ?: WordVisualStatus.UNREAD
                    val isExpected = isCurrentAyah && wIdx == currentWordCursor
                    QuranWordChip(
                        word = word,
                        status = status,
                        isExpected = isExpected,
                        isHiddenMode = isHiddenMode,
                        fontSizeSp = fontSizeSp
                    )
                }

                // Ayah End Ornament
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(32.dp)
                        .border(1.dp, semantic.goldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ayah.ayahEndNumberArabic,
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.goldAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun QuranWordChip(
    word: QuranWord,
    status: WordVisualStatus,
    isExpected: Boolean,
    isHiddenMode: Boolean,
    fontSizeSp: Float
) {
    val semantic = LocalTasmeeColors.current
    val hideWord = isHiddenMode && status == WordVisualStatus.UNREAD

    val (textColor, bgColor, borderColor) = when (status) {
        WordVisualStatus.CORRECT -> Triple(
            semantic.correctWord,
            semantic.correctWordContainer.copy(alpha = 0.55f),
            Color.Transparent
        )
        WordVisualStatus.CONFIRMED_ERROR -> Triple(
            semantic.errorWord,
            semantic.errorWordContainer,
            semantic.errorWord.copy(alpha = 0.65f)
        )
        WordVisualStatus.SUSPECTED_UNCERTAIN -> Triple(
            semantic.uncertainWord,
            semantic.uncertainWordContainer,
            semantic.uncertainWord.copy(alpha = 0.65f)
        )
        WordVisualStatus.HINT_REVEALED -> Triple(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            semantic.goldAccent
        )
        WordVisualStatus.UNREAD -> Triple(
            if (isExpected) MaterialTheme.colorScheme.onSurface else semantic.unreadWord,
            if (hideWord) semantic.hiddenPillBackground else Color.Transparent,
            if (isExpected) semantic.goldAccent else Color.Transparent
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(8.dp))
                } else Modifier
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (hideWord) {
            Text(
                text = "••••",
                fontSize = (fontSizeSp * 0.8f).sp,
                color = semantic.unreadWord
            )
        } else {
            Text(
                text = word.displayUthmani,
                fontFamily = QaloonMushafFontFamily,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.65f).sp,
                color = textColor,
                textDecoration = if (status == WordVisualStatus.CORRECT) {
                    TextDecoration.Underline
                } else null
            )
        }

        // Non-color accessibility indicators
        when (status) {
            WordVisualStatus.CORRECT -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "كلمة صحيحة",
                tint = semantic.correctWord,
                modifier = Modifier.size(12.dp)
            )
            WordVisualStatus.CONFIRMED_ERROR -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "موضع خطأ",
                tint = semantic.errorWord,
                modifier = Modifier.size(14.dp)
            )
            WordVisualStatus.SUSPECTED_UNCERTAIN -> Icon(
                imageVector = Icons.Filled.HelpOutline,
                contentDescription = "قراءة تحتاج تأكيدًا",
                tint = semantic.uncertainWord,
                modifier = Modifier.size(14.dp)
            )
            else -> Unit
        }
    }
}
