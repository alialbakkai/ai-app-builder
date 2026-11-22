package com.example.context

import com.example.domain.model.IntegrityStatus
import com.example.domain.model.QuranAyah
import com.example.domain.model.RecitationMode
import com.example.domain.model.RecitationState
import com.example.domain.model.StructuredErrorEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Context Manager managing Static, Live, and Cumulative recitation context.
 * Enforces read-only Quran reference isolation and dynamic sliding context windows.
 */
class RecitationContextManager {

    data class StaticContext(
        val riwayah: String = "قالون عن نافع",
        val tariq: String = "أبو نشيط عن قالون (الشاطبية)",
        val verseCountSystem: String = "العد المدني",
        val tajweedCheckEnabled: Boolean = false,
        val audioStorageEnabled: Boolean = false,
        val integrityStatus: IntegrityStatus? = null
    )

    data class LiveContext(
        val mode: RecitationMode = RecitationMode.SPECIFIC_SURAH,
        val state: RecitationState = RecitationState.IDLE,
        val currentSurahNo: Int = 1,
        val currentAyahNo: Int = 1,
        val lastConfirmedWordIndex: Int = 0,
        val nextExpectedWordIndex: Int = 1,
        val dynamicWindowAyahs: List<QuranAyah> = emptyList(),
        val recentTranscripts: List<String> = emptyList(),
        val lastConfidence: Float = 0f,
        val activeErrors: List<StructuredErrorEvent> = emptyList(),
        val isPaused: Boolean = false
    )

    data class CumulativeSummary(
        val totalSessionsCount: Int = 0,
        val totalConfirmedErrors: Int = 0,
        val dueReviewsCount: Int = 0,
        val masteredSurahsCount: Int = 0
    )

    private val _staticContext = MutableStateFlow(StaticContext())
    val staticContext: StateFlow<StaticContext> = _staticContext.asStateFlow()

    private val _liveContext = MutableStateFlow(LiveContext())
    val liveContext: StateFlow<LiveContext> = _liveContext.asStateFlow()

    private val _cumulativeSummary = MutableStateFlow(CumulativeSummary())
    val cumulativeSummary: StateFlow<CumulativeSummary> = _cumulativeSummary.asStateFlow()

    fun updateStaticIntegrity(status: IntegrityStatus) {
        _staticContext.update {
            it.copy(
                riwayah = status.riwayah,
                tariq = status.tariq,
                verseCountSystem = status.verseCountSystem,
                integrityStatus = status
            )
        }
    }

    fun updateLiveSession(transform: (LiveContext) -> LiveContext) {
        _liveContext.update(transform)
    }

    fun updateCumulativeSummary(summary: CumulativeSummary) {
        _cumulativeSummary.value = summary
    }

    fun appendTranscript(transcript: String) {
        val sanitized = sanitizeUntrustedInput(transcript)
        if (sanitized.isBlank()) return
        _liveContext.update { ctx ->
            ctx.copy(recentTranscripts = (ctx.recentTranscripts + sanitized).takeLast(5))
        }
    }

    /**
     * Treats external text and ASR hypotheses strictly as inert data, stripping
     * control characters and potential instruction delimiters before AI context usage.
     */
    fun sanitizeUntrustedInput(raw: String): String {
        return raw
            .replace(Regex("[\\p{Cntrl}&&[^\n\t]]"), " ")
            .replace("```", "")
            .replace("<|", "")
            .replace("|>", "")
            .take(400)
            .trim()
    }
}
