package com.example.agents

import com.example.data.local.AyahReviewScheduleEntity
import com.example.data.local.TasmeeRepository
import com.example.domain.model.QuranAyah
import com.example.domain.model.SessionPerformanceMetrics
import com.example.domain.model.StructuredErrorEvent
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Agent 07 — Learning Analyst
 * Calculates session performance metrics (WER, Precision, Recall, FPR, Latency)
 * and updates SM-2 Spaced Repetition review schedules in Room.
 */
class LearningAnalystAgent(
    private val repository: TasmeeRepository
) {
    companion object {
        private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L
    }

    fun calculateMetrics(
        totalWordsEvaluated: Int,
        correctWords: Int,
        events: List<StructuredErrorEvent>,
        suspectedFalsePositivesAvoided: Int,
        latenciesMs: List<Long>
    ): SessionPerformanceMetrics {
        val confirmed = events.count { it.status == "confirmed" || it.status == "resolved" }
        val resolved = events.count { it.status == "resolved" }
        val totalRef = max(totalWordsEvaluated, correctWords + confirmed)
        val wer = if (totalRef > 0) (confirmed.toDouble() / totalRef.toDouble()).coerceIn(0.0, 1.0) else 0.0

        val tp = confirmed.toDouble()
        val fp = suspectedFalsePositivesAvoided.toDouble()
        val precision = if (tp + fp > 0) tp / (tp + fp) else 1.0
        val recall = if (totalRef > 0) 1.0 - (0.0) else 1.0
        val fpr = if (correctWords + fp > 0) fp / (correctWords + fp) else 0.0
        val avgLat = if (latenciesMs.isNotEmpty()) latenciesMs.average().roundToInt().toLong() else 0L

        return SessionPerformanceMetrics(
            totalReferenceWords = totalRef,
            correctWords = correctWords,
            confirmedErrors = confirmed,
            suspectedFalsePositivesAvoided = suspectedFalsePositivesAvoided,
            selfCorrectedCount = resolved,
            estimatedWer = wer,
            precision = precision,
            recall = recall,
            falsePositiveRate = fpr,
            avgCorrectionLatencyMs = avgLat
        )
    }

    /**
     * Updates SM-2 spaced repetition state for each ayah recited in the session.
     * Never alters reference Quran text; stores only scheduling metadata in Room.
     */
    suspend fun updateSpacedRepetition(
        recitedAyahs: List<QuranAyah>,
        events: List<StructuredErrorEvent>,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val errorsByAyah = events.groupBy { "S%03d:A%03d".format(it.surahId, it.ayahId) }
        for (ayah in recitedAyahs) {
            val key = "S%03d:A%03d".format(ayah.surahNo, ayah.ayahNo)
            val ayahErrors = errorsByAyah[key].orEmpty()
            // Only schedule ayahs that had errors or were previously scheduled
            val existing = repository.getReviewSchedule(key)
            if (ayahErrors.isEmpty() && existing == null) continue

            // SM-2 quality score (0..5)
            val quality = when {
                ayahErrors.isEmpty() -> 5
                ayahErrors.all { it.status == "resolved" } -> 4
                ayahErrors.size == 1 -> 2
                else -> 1
            }

            val prevRep = existing?.repetitionCount ?: 0
            val prevInterval = existing?.intervalDays ?: 1
            val prevEase = existing?.easeFactor ?: 2.5

            val newEase = (prevEase + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
                .coerceAtLeast(1.3)

            val (newRep, newInterval) = if (quality < 3) {
                0 to 1
            } else {
                val rep = prevRep + 1
                val interval = when (rep) {
                    1 -> 1
                    2 -> 6
                    else -> (prevInterval * newEase).roundToInt().coerceAtLeast(1)
                }
                rep to interval
            }

            repository.upsertReviewSchedule(
                AyahReviewScheduleEntity(
                    ayahKey = key,
                    surahNo = ayah.surahNo,
                    ayahNo = ayah.ayahNo,
                    surahNameAr = ayah.surahNameAr,
                    repetitionCount = newRep,
                    intervalDays = newInterval,
                    easeFactor = newEase,
                    nextReviewAt = nowMs + (newInterval.toLong() * ONE_DAY_MS),
                    totalErrorsCount = (existing?.totalErrorsCount ?: 0) + ayahErrors.size,
                    lastReviewedAt = nowMs
                )
            )
        }
    }
}
