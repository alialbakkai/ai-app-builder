package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agents.AudioAndSpeechController
import com.example.agents.LearningAnalystAgent
import com.example.agents.QuranDataGuardian
import com.example.agents.RecitationTrackerAndErrorEngine
import com.example.agents.VerificationAgent
import com.example.audio.QaloonAudioPlayer
import com.example.context.RecitationContextManager
import com.example.data.local.AyahReviewScheduleEntity
import com.example.data.local.RecitationErrorEntity
import com.example.data.local.RecitationSessionEntity
import com.example.data.local.TasmeeDatabase
import com.example.data.local.TasmeeRepository
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.domain.model.AsrHypothesis
import com.example.domain.model.IntegrityStatus
import com.example.domain.model.QaloonRecitersCatalog
import com.example.domain.model.QuranAyah
import com.example.domain.model.QuranWord
import com.example.domain.model.RecitationMode
import com.example.domain.model.RecitationState
import com.example.domain.model.SessionPerformanceMetrics
import com.example.domain.model.StructuredErrorEvent
import com.example.domain.model.SurahInfo
import com.example.domain.model.WordVisualStatus
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecitationUiState(
    val mode: RecitationMode = RecitationMode.SPECIFIC_SURAH,
    val scopeTitle: String = "سورة الفاتحة",
    val state: RecitationState = RecitationState.IDLE,
    val ayahs: List<QuranAyah> = emptyList(),
    val currentAyahIndex: Int = 0,
    val currentWordCursor: Int = 0,
    val wordStatuses: Map<String, WordVisualStatus> = emptyMap(),
    val activeCorrectionWord: QuranWord? = null,
    val activeErrorEvent: StructuredErrorEvent? = null,
    val sessionEvents: List<StructuredErrorEvent> = emptyList(),
    val isFreeLocationLocked: Boolean = true,
    val statusBannerMessage: String? = null,
    val lastTranscriptSnippet: String = "",
    val isOnline: Boolean = true,
    val isCloudVerifying: Boolean = false,
    val lastSavedSessionId: Long? = null,
    val sessionStartedAt: Long = 0L
)

data class SessionReportDetail(
    val session: RecitationSessionEntity,
    val errors: List<RecitationErrorEntity>,
    val metrics: SessionPerformanceMetrics
)

class TasmeeViewModel(application: Application) : AndroidViewModel(application) {

    private val guardian = QuranDataGuardian()
    private val contextManager = RecitationContextManager()
    private val trackerEngine = RecitationTrackerAndErrorEngine(guardian)
    private val verificationAgent = VerificationAgent(guardian, contextManager)

    private val database = TasmeeDatabase.getInstance(application)
    private val repository = TasmeeRepository(database.tasmeeDao())
    private val prefsRepository = UserPreferencesRepository(application)
    private val learningAnalyst = LearningAnalystAgent(repository)

    private val audioController = AudioAndSpeechController(application, viewModelScope)
    val audioPlayer = QaloonAudioPlayer()

    private val _integrityStatus = MutableStateFlow(guardian.integrityStatus)
    val integrityStatus: StateFlow<IntegrityStatus> = _integrityStatus.asStateFlow()

    private val _surahs = MutableStateFlow<List<SurahInfo>>(emptyList())
    val surahs: StateFlow<List<SurahInfo>> = _surahs.asStateFlow()

    private val _recitationUi = MutableStateFlow(RecitationUiState())
    val recitationUi: StateFlow<RecitationUiState> = _recitationUi.asStateFlow()

    private val _selectedReport = MutableStateFlow<SessionReportDetail?>(null)
    val selectedReport: StateFlow<SessionReportDetail?> = _selectedReport.asStateFlow()

    val preferences: StateFlow<UserPreferences> = prefsRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val sessionsHistory: StateFlow<List<RecitationSessionEntity>> = repository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentErrors: StateFlow<List<RecitationErrorEntity>> = repository.recentErrors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueReviews: StateFlow<List<AyahReviewScheduleEntity>> = repository.observeDueReviews()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReviewSchedules: StateFlow<List<AyahReviewScheduleEntity>> = repository.allReviewSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val micRmsLevel: StateFlow<Float> = audioController.rmsLevel
    val isSpeechDetected: StateFlow<Boolean> = audioController.isSpeechDetected
    val audioEngineMode: StateFlow<AudioAndSpeechController.EngineMode> = audioController.engineMode
    val audioDiagnostic: StateFlow<String?> = audioController.diagnosticMessage
    val audioPlaybackState: StateFlow<QaloonAudioPlayer.PlaybackState> = audioPlayer.playbackState

    private val sessionLatencies = mutableListOf<Long>()
    private var suspectedFalsePositivesAvoided = 0
    private var totalWordsEvaluated = 0

    init {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                guardian.loadFromAssets(getApplication())
            }
            _integrityStatus.value = status
            contextManager.updateStaticIntegrity(status)
            if (status.isVerified) {
                _surahs.value = guardian.getAllSurahs()
                prepareSurahSession(surahNo = 1, mode = RecitationMode.TRAINING_MUSHAF)
            }
        }

        viewModelScope.launch {
            combine(
                sessionsHistory,
                dueReviews,
                allReviewSchedules
            ) { sessions, due, all ->
                RecitationContextManager.CumulativeSummary(
                    totalSessionsCount = sessions.size,
                    totalConfirmedErrors = sessions.sumOf { it.confirmedErrorsCount },
                    dueReviewsCount = due.size,
                    masteredSurahsCount = all.count { it.repetitionCount >= 3 }
                )
            }.collectLatest { summary ->
                contextManager.updateCumulativeSummary(summary)
            }
        }

        viewModelScope.launch {
            audioController.hypotheses.collect { hypothesis ->
                processIncomingHypothesis(hypothesis)
            }
        }

        viewModelScope.launch {
            audioController.audioSnippets.collect { wavBytes ->
                if (!preferences.value.cloudVerificationEnabled || !checkIsOnline()) {
                    _recitationUi.update {
                        it.copy(
                            statusBannerMessage = "الوضع المحلي: فعّل التحقق السحابي أو اتصال الإنترنت للتفريغ الصوتي الهجين."
                        )
                    }
                    return@collect
                }
                val ui = _recitationUi.value
                val ayah = ui.ayahs.getOrNull(ui.currentAyahIndex)
                val window = if (ayah != null) {
                    guardian.getContextWindow(ayah.surahNo, ayah.ayahNo)
                } else {
                    guardian.getAyahsForSurah(1).take(3)
                }
                _recitationUi.update { it.copy(isCloudVerifying = true) }
                val res = verificationAgent.transcribeAudioSnippet(wavBytes, window)
                _recitationUi.update { it.copy(isCloudVerifying = false) }
                res.onSuccess { hyp ->
                    if (hyp.normalizedWords.isNotEmpty()) {
                        processIncomingHypothesis(hyp)
                    } else {
                        _recitationUi.update {
                            it.copy(
                                state = RecitationState.SUSPECTED_ERROR,
                                statusBannerMessage = "لم تتضح القراءة، أعد المحاولة بصوت واضح."
                            )
                        }
                    }
                }.onFailure { err ->
                    _recitationUi.update {
                        it.copy(statusBannerMessage = "تعذر التفريغ السحابي: ${err.localizedMessage ?: "خطأ شبكي"}")
                    }
                }
            }
        }
    }

    fun prepareFreeSession() {
        if (!_integrityStatus.value.isVerified) return
        stopListeningOnly()
        sessionLatencies.clear()
        suspectedFalsePositivesAvoided = 0
        totalWordsEvaluated = 0
        val defaultPreview = guardian.getAyahsForSurah(1)
        _recitationUi.value = RecitationUiState(
            mode = RecitationMode.FREE_RECITATION,
            scopeTitle = "التسميع الحر — ابدأ من أي سورة",
            state = RecitationState.IDLE,
            ayahs = defaultPreview,
            currentAyahIndex = 0,
            currentWordCursor = 0,
            isFreeLocationLocked = false,
            isOnline = checkIsOnline(),
            sessionStartedAt = System.currentTimeMillis()
        )
    }

    fun prepareSurahSession(surahNo: Int, mode: RecitationMode) {
        if (!_integrityStatus.value.isVerified) return
        stopListeningOnly()
        sessionLatencies.clear()
        suspectedFalsePositivesAvoided = 0
        totalWordsEvaluated = 0
        val ayahs = guardian.getAyahsForSurah(surahNo)
        val surahName = ayahs.firstOrNull()?.surahNameAr ?: "سورة $surahNo"
        _recitationUi.value = RecitationUiState(
            mode = mode,
            scopeTitle = "سورة $surahName",
            state = RecitationState.IDLE,
            ayahs = ayahs,
            currentAyahIndex = 0,
            currentWordCursor = 0,
            isFreeLocationLocked = true,
            isOnline = checkIsOnline(),
            sessionStartedAt = System.currentTimeMillis()
        )
        syncContextWindow()
    }

    fun prepareJozzSession(jozz: Int) {
        if (!_integrityStatus.value.isVerified) return
        stopListeningOnly()
        sessionLatencies.clear()
        suspectedFalsePositivesAvoided = 0
        totalWordsEvaluated = 0
        val ayahs = guardian.getAyahsForJozz(jozz)
        _recitationUi.value = RecitationUiState(
            mode = RecitationMode.JUZ_OR_HIZB,
            scopeTitle = "الجزء $jozz",
            state = RecitationState.IDLE,
            ayahs = ayahs,
            isFreeLocationLocked = true,
            isOnline = checkIsOnline(),
            sessionStartedAt = System.currentTimeMillis()
        )
        syncContextWindow()
    }

    fun prepareHizbSession(hizb: Int) {
        if (!_integrityStatus.value.isVerified) return
        stopListeningOnly()
        sessionLatencies.clear()
        suspectedFalsePositivesAvoided = 0
        totalWordsEvaluated = 0
        val ayahs = guardian.getAyahsForHizb(hizb)
        _recitationUi.value = RecitationUiState(
            mode = RecitationMode.JUZ_OR_HIZB,
            scopeTitle = "الحزب $hizb",
            state = RecitationState.IDLE,
            ayahs = ayahs,
            isFreeLocationLocked = true,
            isOnline = checkIsOnline(),
            sessionStartedAt = System.currentTimeMillis()
        )
        syncContextWindow()
    }

    fun prepareReviewPastErrorsSession() {
        if (!_integrityStatus.value.isVerified) return
        stopListeningOnly()
        sessionLatencies.clear()
        suspectedFalsePositivesAvoided = 0
        totalWordsEvaluated = 0
        val schedules = allReviewSchedules.value
        val ayahs = schedules.mapNotNull { guardian.getAyah(it.surahNo, it.ayahNo) }
            .ifEmpty { guardian.getAyahsForSurah(1) }
        _recitationUi.value = RecitationUiState(
            mode = RecitationMode.REVIEW_PAST_ERRORS,
            scopeTitle = if (schedules.isNotEmpty()) "مراجعة الآيات المستحقة (${ayahs.size} آية)" else "مراجعة عامة — الفاتحة",
            state = RecitationState.IDLE,
            ayahs = ayahs,
            isFreeLocationLocked = true,
            isOnline = checkIsOnline(),
            sessionStartedAt = System.currentTimeMillis()
        )
        syncContextWindow()
    }

    fun startOrResumeListening() {
        if (!_integrityStatus.value.isVerified) return
        audioPlayer.stop()
        val ui = _recitationUi.value
        val startedAt = if (ui.sessionStartedAt == 0L) System.currentTimeMillis() else ui.sessionStartedAt
        _recitationUi.update {
            it.copy(
                state = RecitationState.LISTENING,
                isOnline = checkIsOnline(),
                statusBannerMessage = null,
                sessionStartedAt = startedAt
            )
        }
        audioController.startListening(preferCloudAudioMode = false)
    }

    fun pauseListening() {
        stopListeningOnly()
        _recitationUi.update {
            it.copy(state = RecitationState.IDLE, statusBannerMessage = "تم الإيقاف المؤقت للتسميع")
        }
    }

    private fun stopListeningOnly() {
        audioController.stopListening()
    }

    fun repeatCurrentAyah() {
        val ui = _recitationUi.value
        val currentAyah = ui.ayahs.getOrNull(ui.currentAyahIndex) ?: return
        val updatedStatuses = ui.wordStatuses.toMutableMap()
        currentAyah.words.forEach { updatedStatuses.remove(it.referenceTokenId) }
        _recitationUi.update {
            it.copy(
                currentWordCursor = 0,
                wordStatuses = updatedStatuses,
                activeCorrectionWord = null,
                activeErrorEvent = null,
                state = if (it.state == RecitationState.IDLE) RecitationState.IDLE else RecitationState.LISTENING,
                statusBannerMessage = "تمت إعادة الآية ${currentAyah.ayahNo}"
            )
        }
    }

    fun revealNextHint() {
        val ui = _recitationUi.value
        val currentAyah = ui.ayahs.getOrNull(ui.currentAyahIndex) ?: return
        val word = currentAyah.words.getOrNull(ui.currentWordCursor) ?: return
        val updatedStatuses = ui.wordStatuses.toMutableMap()
        updatedStatuses[word.referenceTokenId] = WordVisualStatus.HINT_REVEALED
        _recitationUi.update {
            it.copy(
                wordStatuses = updatedStatuses,
                statusBannerMessage = "تلميح: ${word.displayUthmani}"
            )
        }
    }

    fun jumpToAyahIndex(index: Int) {
        val ui = _recitationUi.value
        if (index !in ui.ayahs.indices) return
        _recitationUi.update {
            it.copy(
                currentAyahIndex = index,
                currentWordCursor = 0,
                activeCorrectionWord = null,
                activeErrorEvent = null
            )
        }
        syncContextWindow()
    }

    fun toggleReferenceAudioForCurrentSurah() {
        val currentPlayback = audioPlayer.playbackState.value
        if (currentPlayback is QaloonAudioPlayer.PlaybackState.Playing ||
            currentPlayback is QaloonAudioPlayer.PlaybackState.Loading
        ) {
            audioPlayer.stop()
            return
        }
        if (_recitationUi.value.state != RecitationState.IDLE) {
            pauseListening()
        }
        val ui = _recitationUi.value
        val surahNo = ui.ayahs.getOrNull(ui.currentAyahIndex)?.surahNo ?: 1
        val reciter = QaloonRecitersCatalog.findById(preferences.value.selectedReciterId)
        audioPlayer.playSurah(surahNo, reciter)
    }

    fun processIncomingHypothesis(hypothesis: AsrHypothesis) {
        contextManager.appendTranscript(hypothesis.rawTranscript)
        val ui = _recitationUi.value
        if (ui.ayahs.isEmpty() && ui.isFreeLocationLocked) return

        // Handle Free Recitation auto-detection before locking
        if (ui.mode == RecitationMode.FREE_RECITATION && !ui.isFreeLocationLocked) {
            val detected = trackerEngine.detectStartingLocation(hypothesis)
            if (detected != null) {
                val surahAyahs = guardian.getAyahsForSurah(detected.surahNo)
                val startIdx = surahAyahs.indexOfFirst { it.ayahNo == detected.ayahNo }.coerceAtLeast(0)
                _recitationUi.update {
                    it.copy(
                        ayahs = surahAyahs,
                        scopeTitle = "تسميع حر — سورة ${detected.surahNameAr}",
                        currentAyahIndex = startIdx,
                        currentWordCursor = 0,
                        isFreeLocationLocked = true,
                        state = RecitationState.MATCHING,
                        statusBannerMessage = "تم التعرف على سورة ${detected.surahNameAr} آية ${detected.ayahNo}",
                        lastTranscriptSnippet = hypothesis.rawTranscript
                    )
                }
                syncContextWindow()
            } else {
                _recitationUi.update {
                    it.copy(
                        state = RecitationState.MATCHING,
                        lastTranscriptSnippet = hypothesis.rawTranscript,
                        statusBannerMessage = "جارٍ تحديد السورة والآية من تلاوتك..."
                    )
                }
                return
            }
        }

        val currentUi = _recitationUi.value
        val currentAyah = currentUi.ayahs.getOrNull(currentUi.currentAyahIndex) ?: return

        _recitationUi.update {
            it.copy(state = RecitationState.MATCHING, lastTranscriptSnippet = hypothesis.rawTranscript)
        }

        val result = trackerEngine.alignHypothesis(
            hypothesis = hypothesis,
            currentAyah = currentAyah,
            currentWordCursor = currentUi.currentWordCursor,
            activeAyahPool = currentUi.ayahs,
            existingStatuses = currentUi.wordStatuses,
            waitingCorrectionTokenId = currentUi.activeCorrectionWord?.referenceTokenId
        )

        sessionLatencies.add(result.latencyMs)
        totalWordsEvaluated += result.wordUpdates.size

        val mergedStatuses = currentUi.wordStatuses.toMutableMap().apply {
            putAll(result.wordUpdates)
        }

        // Update resolved self-corrections
        val updatedEvents = currentUi.sessionEvents.map { ev ->
            if (ev.referenceTokenId in result.resolvedErrorTokenIds) {
                ev.copy(status = "resolved")
            } else ev
        } + result.newEvents

        val firstNewEvent = result.newEvents.firstOrNull()
        val correctionWord = if (firstNewEvent != null) {
            guardian.resolveWordById(firstNewEvent.referenceTokenId)
        } else if (result.resolvedErrorTokenIds.isNotEmpty()) {
            null
        } else {
            currentUi.activeCorrectionWord
        }

        var nextAyahIdx = currentUi.currentAyahIndex
        var nextWordCursor = result.nextWordIndex
        var nextState = result.recommendedState
        var bannerMsg: String? = null

        if (firstNewEvent != null) {
            nextState = RecitationState.WAITING_FOR_CORRECTION
            val refWord = guardian.resolveWordById(firstNewEvent.referenceTokenId)
            bannerMsg = "${firstNewEvent.errorType.labelAr} — الصحيح: ${refWord?.displayUthmani ?: ""}"
        } else if (result.resolvedErrorTokenIds.isNotEmpty()) {
            nextState = RecitationState.RESUMING
            bannerMsg = "أحسنت، تم التصحيح. تفضل بالمتابعة"
        } else if (result.recommendedState == RecitationState.SUSPECTED_ERROR) {
            bannerMsg = "لم تتضح القراءة، أعد المحاولة بهدوء"
        }

        if (result.detectedTargetAyah != null) {
            val jumpedIdx = currentUi.ayahs.indexOfFirst {
                it.surahNo == result.detectedTargetAyah.surahNo && it.ayahNo == result.detectedTargetAyah.ayahNo
            }
            if (jumpedIdx >= 0) {
                nextAyahIdx = jumpedIdx
                nextWordCursor = 0
            }
        } else if (result.ayahCompleted) {
            if (currentUi.currentAyahIndex + 1 < currentUi.ayahs.size) {
                nextAyahIdx = currentUi.currentAyahIndex + 1
                nextWordCursor = 0
            } else {
                nextState = RecitationState.COMPLETED
                stopListeningOnly()
            }
        }

        _recitationUi.update {
            it.copy(
                state = nextState,
                currentAyahIndex = nextAyahIdx,
                currentWordCursor = nextWordCursor,
                wordStatuses = mergedStatuses,
                activeCorrectionWord = correctionWord,
                activeErrorEvent = firstNewEvent ?: if (result.resolvedErrorTokenIds.isNotEmpty()) null else it.activeErrorEvent,
                sessionEvents = updatedEvents,
                statusBannerMessage = bannerMsg
            )
        }
        syncContextWindow()

        if (nextState == RecitationState.COMPLETED) {
            finishSessionAndSave()
            return
        }

        // Trigger independent Firebase AI Logic verification when needed
        if (result.requiresCloudVerification &&
            preferences.value.cloudVerificationEnabled &&
            checkIsOnline()
        ) {
            val targetWord = currentAyah.words.getOrNull(result.nextWordIndex) ?: currentAyah.words.last()
            launchCloudVerification(
                suspectedEvent = firstNewEvent,
                hypothesis = hypothesis,
                window = result.candidateWindow.ifEmpty {
                    guardian.getContextWindow(currentAyah.surahNo, currentAyah.ayahNo)
                },
                expectedSurah = currentAyah.surahNo,
                expectedAyah = currentAyah.ayahNo,
                expectedWordIndex = targetWord.wordIndex
            )
        }
    }

    private fun launchCloudVerification(
        suspectedEvent: StructuredErrorEvent?,
        hypothesis: AsrHypothesis,
        window: List<QuranAyah>,
        expectedSurah: Int,
        expectedAyah: Int,
        expectedWordIndex: Int
    ) {
        viewModelScope.launch {
            _recitationUi.update { it.copy(isCloudVerifying = true) }
            val verdict = verificationAgent.verifySuspectedEvent(
                suspectedEvent = suspectedEvent,
                hypothesis = hypothesis,
                window = window,
                expectedSurah = expectedSurah,
                expectedAyah = expectedAyah,
                expectedWordIndex = expectedWordIndex
            )
            _recitationUi.update { current ->
                when (verdict) {
                    is VerificationAgent.VerificationResult.ValidQaloon -> {
                        suspectedFalsePositivesAvoided++
                        val updatedMap = current.wordStatuses.toMutableMap()
                        updatedMap[verdict.referenceTokenId] = WordVisualStatus.CORRECT
                        current.copy(
                            isCloudVerifying = false,
                            wordStatuses = updatedMap,
                            activeCorrectionWord = null,
                            activeErrorEvent = null,
                            state = RecitationState.RESUMING,
                            statusBannerMessage = "تحقق سحابي: وجه صحيح وفق رواية قالون"
                        )
                    }
                    is VerificationAgent.VerificationResult.Uncertain -> {
                        suspectedFalsePositivesAvoided++
                        val updatedMap = current.wordStatuses.toMutableMap()
                        updatedMap[verdict.referenceTokenId] = WordVisualStatus.SUSPECTED_UNCERTAIN
                        current.copy(
                            isCloudVerifying = false,
                            wordStatuses = updatedMap,
                            state = RecitationState.SUSPECTED_ERROR,
                            statusBannerMessage = "لم تتضح القراءة، أعد نطق الكلمة بهدوء"
                        )
                    }
                    is VerificationAgent.VerificationResult.Confirmed -> {
                        val refWord = guardian.resolveWordById(verdict.event.referenceTokenId)
                        val updatedMap = current.wordStatuses.toMutableMap()
                        updatedMap[verdict.event.referenceTokenId] = WordVisualStatus.CONFIRMED_ERROR
                        current.copy(
                            isCloudVerifying = false,
                            wordStatuses = updatedMap,
                            activeCorrectionWord = refWord,
                            activeErrorEvent = verdict.event,
                            state = RecitationState.WAITING_FOR_CORRECTION,
                            statusBannerMessage = "${verdict.event.errorType.labelAr} — الصحيح: ${refWord?.displayUthmani ?: ""}"
                        )
                    }
                    is VerificationAgent.VerificationResult.Unavailable -> {
                        current.copy(
                            isCloudVerifying = false,
                            isOnline = checkIsOnline()
                        )
                    }
                }
            }
        }
    }

    fun finishSessionAndSave(onSaved: ((Long) -> Unit)? = null) {
        stopListeningOnly()
        audioPlayer.stop()
        val ui = _recitationUi.value
        viewModelScope.launch {
            val correctCount = ui.wordStatuses.values.count { it == WordVisualStatus.CORRECT }
            val confirmedErrors = ui.sessionEvents.count { it.status == "confirmed" || it.status == "resolved" }
            val selfCorrected = ui.sessionEvents.count { it.status == "resolved" }
            val totalEvaluated = (correctCount + confirmedErrors).coerceAtLeast(1)
            val accuracy = ((correctCount.toDouble() / totalEvaluated.toDouble()) * 100.0)
                .toInt().coerceIn(0, 100)

            val metrics = learningAnalyst.calculateMetrics(
                totalWordsEvaluated = totalWordsEvaluated,
                correctWords = correctCount,
                events = ui.sessionEvents,
                suspectedFalsePositivesAvoided = suspectedFalsePositivesAvoided,
                latenciesMs = sessionLatencies
            )

            var aiSummaryText = ""
            if (preferences.value.cloudVerificationEnabled && checkIsOnline()) {
                val breakdown = ui.sessionEvents
                    .groupBy { it.errorType.labelAr }
                    .entries.joinToString("، ") { "${it.key}: ${it.value.size}" }
                    .ifBlank { "لا توجد أخطاء مسجلة" }
                aiSummaryText = verificationAgent.generateSessionSummary(
                    surahName = ui.scopeTitle,
                    correctWords = correctCount,
                    confirmedErrors = confirmedErrors,
                    accuracyPercent = accuracy,
                    errorBreakdown = breakdown
                ).getOrDefault("")
            }

            val now = System.currentTimeMillis()
            val started = if (ui.sessionStartedAt > 0L) ui.sessionStartedAt else now
            val primarySurah = ui.ayahs.firstOrNull()?.surahNo ?: 1

            val sessionEntity = RecitationSessionEntity(
                startedAt = started,
                endedAt = now,
                modeName = ui.mode.titleAr,
                scopeTitle = ui.scopeTitle,
                surahNo = primarySurah,
                totalWordsRecited = totalEvaluated,
                correctWords = correctCount,
                confirmedErrorsCount = confirmedErrors,
                selfCorrectedCount = selfCorrected,
                accuracyPercent = accuracy,
                estimatedWer = metrics.estimatedWer,
                avgLatencyMs = metrics.avgCorrectionLatencyMs,
                aiSummary = aiSummaryText
            )

            val errorEntities = ui.sessionEvents.map { ev ->
                RecitationErrorEntity(
                    sessionId = 0L,
                    surahNo = ev.surahId,
                    ayahNo = ev.ayahId,
                    wordIndex = ev.wordIndex,
                    referenceTokenId = ev.referenceTokenId,
                    errorTypeKey = ev.errorType.jsonKey,
                    errorTypeLabelAr = ev.errorType.labelAr,
                    spokenHypothesis = ev.spokenHypothesis,
                    confidence = ev.confidence,
                    resolvedBySelfCorrection = ev.status == "resolved",
                    timestamp = now
                )
            }

            val savedId = repository.saveSessionWithErrors(sessionEntity, errorEntities)
            val recitedSlice = ui.ayahs.take((ui.currentAyahIndex + 1).coerceAtMost(ui.ayahs.size))
            learningAnalyst.updateSpacedRepetition(recitedSlice, ui.sessionEvents, now)

            _selectedReport.value = SessionReportDetail(
                session = sessionEntity.copy(id = savedId),
                errors = errorEntities.map { it.copy(sessionId = savedId) },
                metrics = metrics
            )
            _recitationUi.update {
                it.copy(state = RecitationState.COMPLETED, lastSavedSessionId = savedId)
            }
            onSaved?.invoke(savedId)
        }
    }

    fun loadSessionReport(sessionId: Long) {
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId) ?: return@launch
            val errors = repository.getErrorsForSession(sessionId)
            val metrics = SessionPerformanceMetrics(
                totalReferenceWords = session.totalWordsRecited,
                correctWords = session.correctWords,
                confirmedErrors = session.confirmedErrorsCount,
                selfCorrectedCount = session.selfCorrectedCount,
                estimatedWer = session.estimatedWer,
                avgCorrectionLatencyMs = session.avgLatencyMs
            )
            _selectedReport.value = SessionReportDetail(session, errors, metrics)
        }
    }

    fun resolveDisplayWord(referenceTokenId: String): String =
        guardian.resolveWordById(referenceTokenId)?.displayUthmani ?: ""

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }

    fun wipeAllHistory() {
        viewModelScope.launch { repository.wipeAllHistory() }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { prefsRepository.setThemePreference(theme) }
    }

    fun adjustFontSize(deltaSp: Float) {
        viewModelScope.launch {
            prefsRepository.setFontSizeSp(preferences.value.mushafFontSizeSp + deltaSp)
        }
    }

    fun setAutoScrollEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setAutoScrollEnabled(enabled) }
    }

    fun setCloudVerificationEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setCloudVerificationEnabled(enabled) }
    }

    fun setSelectedReciterId(reciterId: String) {
        viewModelScope.launch { prefsRepository.setSelectedReciterId(reciterId) }
    }

    private fun syncContextWindow() {
        val ui = _recitationUi.value
        val ayah = ui.ayahs.getOrNull(ui.currentAyahIndex) ?: return
        val window = guardian.getContextWindow(ayah.surahNo, ayah.ayahNo)
        contextManager.updateLiveSession { live ->
            live.copy(
                mode = ui.mode,
                state = ui.state,
                currentSurahNo = ayah.surahNo,
                currentAyahNo = ayah.ayahNo,
                nextExpectedWordIndex = ui.currentWordCursor + 1,
                dynamicWindowAyahs = window,
                activeErrors = ui.sessionEvents,
                isPaused = ui.state == RecitationState.IDLE
            )
        }
    }

    private fun checkIsOnline(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        super.onCleared()
        audioController.stopListening()
        audioPlayer.stop()
    }
}
