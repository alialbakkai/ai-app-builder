package com.example.agents

import com.example.domain.model.AsrHypothesis
import com.example.domain.model.ErrorType
import com.example.domain.model.QuranAyah
import com.example.domain.model.QuranWord
import com.example.domain.model.RecitationState
import com.example.domain.model.StructuredErrorEvent
import com.example.domain.model.WordVisualStatus
import kotlin.math.max
import kotlin.math.min

/**
 * Agent 04 — Recitation Tracker & Agent 05 — Error Detection Engine
 * Deterministic sequence alignment, Arabic phonetic matching, weighted edit distance,
 * Qaloon-aware variant matching, and multi-type error classification.
 */
class RecitationTrackerAndErrorEngine(
    private val guardian: QuranDataGuardian
) {

    data class AlignmentResult(
        val nextWordIndex: Int, // 0-based index into current ayah words
        val ayahCompleted: Boolean,
        val detectedTargetAyah: QuranAyah? = null,
        val wordUpdates: Map<String, WordVisualStatus> = emptyMap(),
        val newEvents: List<StructuredErrorEvent> = emptyList(),
        val resolvedErrorTokenIds: Set<String> = emptySet(),
        val recommendedState: RecitationState,
        val requiresCloudVerification: Boolean = false,
        val candidateWindow: List<QuranAyah> = emptyList(),
        val latencyMs: Long = 0L
    )

    /**
     * Auto-locates Surah and Ayah for Free Recitation mode using 2-gram index + sequence scoring.
     */
    fun detectStartingLocation(hypothesis: AsrHypothesis): QuranAyah? {
        val spoken = hypothesis.normalizedWords
        if (spoken.isEmpty()) return null

        val candidates = guardian.lookupBigramCandidates(spoken)
        if (candidates.isNotEmpty()) {
            var bestAyah: QuranAyah? = null
            var bestScore = -1.0
            for ((surahNo, ayahNo) in candidates) {
                val ayah = guardian.getAyah(surahNo, ayahNo) ?: continue
                val score = phraseOverlapScore(spoken, ayah.words)
                if (score > bestScore) {
                    bestScore = score
                    bestAyah = ayah
                }
            }
            if (bestAyah != null && bestScore >= 0.45) {
                return bestAyah
            }
        }

        // Fallback single-word scan on Al-Fatihah and Juz Amma first for responsiveness
        if (spoken.size == 1) {
            val w = spoken.first()
            val commonSurahs = listOf(1) + (78..114).toList()
            for (s in commonSurahs) {
                for (ayah in guardian.getAyahsForSurah(s)) {
                    val firstWord = ayah.words.firstOrNull() ?: continue
                    if (isWordMatch(w, firstWord) >= 0.88) {
                        return ayah
                    }
                }
            }
        }
        return null
    }

    /**
     * Aligns streaming/final ASR hypothesis against the expected ayah and sliding window.
     * Supports self-correction, skipped ayahs, repetition, reordering, omission, addition,
     * substitution, wrong jumps, Mutashabihat, and low-confidence uncertainty handling.
     */
    fun alignHypothesis(
        hypothesis: AsrHypothesis,
        currentAyah: QuranAyah,
        currentWordCursor: Int,
        activeAyahPool: List<QuranAyah>,
        existingStatuses: Map<String, WordVisualStatus>,
        waitingCorrectionTokenId: String?
    ): AlignmentResult {
        val startedAt = System.currentTimeMillis()
        val spoken = hypothesis.normalizedWords
        if (spoken.isEmpty()) {
            return AlignmentResult(
                nextWordIndex = currentWordCursor,
                ayahCompleted = false,
                recommendedState = RecitationState.LISTENING,
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }

        val words = currentAyah.words
        if (words.isEmpty()) {
            return AlignmentResult(
                nextWordIndex = 0,
                ayahCompleted = true,
                recommendedState = RecitationState.CORRECT_RECITATION,
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }

        // Low-confidence acoustic guard: never falsely accuse the reciter
        if (hypothesis.confidence in 0.01f..0.44f && !hypothesis.isFinal) {
            val suspectWord = words.getOrNull(currentWordCursor)
            val updates = if (suspectWord != null) {
                mapOf(suspectWord.referenceTokenId to WordVisualStatus.SUSPECTED_UNCERTAIN)
            } else emptyMap()
            return AlignmentResult(
                nextWordIndex = currentWordCursor,
                ayahCompleted = false,
                wordUpdates = updates,
                recommendedState = RecitationState.SUSPECTED_ERROR,
                requiresCloudVerification = true,
                candidateWindow = guardian.getContextWindow(currentAyah.surahNo, currentAyah.ayahNo),
                latencyMs = System.currentTimeMillis() - startedAt
            )
        }

        // 1. Check if the reciter is self-correcting an expected error word
        if (waitingCorrectionTokenId != null) {
            val errorIdx = words.indexOfFirst { it.referenceTokenId == waitingCorrectionTokenId }
            if (errorIdx >= 0) {
                val targetWord = words[errorIdx]
                val matchScore = spoken.maxOfOrNull { isWordMatch(it, targetWord) } ?: 0.0
                if (matchScore >= 0.78) {
                    val updates = linkedMapOf(targetWord.referenceTokenId to WordVisualStatus.CORRECT)
                    var advance = errorIdx + 1
                    // Continue aligning any remaining words spoken in the same phrase
                    val matchAtIdx = spoken.indexOfFirst { isWordMatch(it, targetWord) >= 0.78 }
                    if (matchAtIdx >= 0 && matchAtIdx + 1 < spoken.size) {
                        var sIdx = matchAtIdx + 1
                        while (sIdx < spoken.size && advance < words.size) {
                            if (isWordMatch(spoken[sIdx], words[advance]) >= 0.76) {
                                updates[words[advance].referenceTokenId] = WordVisualStatus.CORRECT
                                advance++
                                sIdx++
                            } else break
                        }
                    }
                    return AlignmentResult(
                        nextWordIndex = advance,
                        ayahCompleted = advance >= words.size,
                        wordUpdates = updates,
                        resolvedErrorTokenIds = setOf(waitingCorrectionTokenId),
                        recommendedState = RecitationState.RESUMING,
                        latencyMs = System.currentTimeMillis() - startedAt
                    )
                }
            }
        }

        // 2. Check for intentional Ayah repetition (repeating from the start of the current ayah)
        if (currentWordCursor >= 2 && spoken.size >= 2) {
            val startOverlap = prefixMatchCount(spoken, words)
            if (startOverlap >= 2) {
                val updates = LinkedHashMap<String, WordVisualStatus>()
                for (i in 0 until startOverlap.coerceAtMost(words.size)) {
                    updates[words[i].referenceTokenId] = WordVisualStatus.CORRECT
                }
                return AlignmentResult(
                    nextWordIndex = startOverlap.coerceAtMost(words.size),
                    ayahCompleted = startOverlap >= words.size,
                    wordUpdates = updates,
                    recommendedState = RecitationState.CORRECT_RECITATION,
                    latencyMs = System.currentTimeMillis() - startedAt
                )
            }
        }

        // 3. Sequential + local lookahead alignment from currentWordCursor
        val updates = LinkedHashMap<String, WordVisualStatus>()
        val events = mutableListOf<StructuredErrorEvent>()
        var cursor = currentWordCursor.coerceIn(0, words.size)
        var sIdx = 0
        var hasError = false
        var hasSuspect = false
        var needCloud = false

        while (sIdx < spoken.size && cursor < words.size) {
            val spokenWord = spoken[sIdx]
            val expectedWord = words[cursor]
            val directSim = isWordMatch(spokenWord, expectedWord)

            if (directSim >= 0.76) {
                updates[expectedWord.referenceTokenId] = WordVisualStatus.CORRECT
                cursor++
                sIdx++
                continue
            }

            // Check if two spoken words combine into one expected word (e.g., "يا ايها" vs "يايها")
            if (sIdx + 1 < spoken.size) {
                val merged = spoken[sIdx] + spoken[sIdx + 1]
                if (isWordMatch(merged, expectedWord) >= 0.80) {
                    updates[expectedWord.referenceTokenId] = WordVisualStatus.CORRECT
                    cursor++
                    sIdx += 2
                    continue
                }
            }

            // Lookahead 1..2 words in expected ayah -> possible OMISSION
            val omittedMatchOffset = (1..2).firstOrNull { offset ->
                cursor + offset < words.size && isWordMatch(spokenWord, words[cursor + offset]) >= 0.80
            }
            if (omittedMatchOffset != null) {
                if (hypothesis.isFinal) {
                    // Check for word reordering (current spoken word matches next, next spoken matches current)
                    val isReordered = sIdx + 1 < spoken.size &&
                        omittedMatchOffset == 1 &&
                        isWordMatch(spoken[sIdx + 1], expectedWord) >= 0.78
                    if (isReordered) {
                        updates[expectedWord.referenceTokenId] = WordVisualStatus.CONFIRMED_ERROR
                        events.add(
                            buildEvent(
                                word = expectedWord,
                                type = ErrorType.WORD_REORDER,
                                confidence = 0.84,
                                status = "confirmed",
                                hypothesis = hypothesis,
                                spokenText = "${spoken[sIdx]} ${spoken[sIdx + 1]}"
                            )
                        )
                        hasError = true
                        cursor += 2
                        sIdx += 2
                        continue
                    }

                    for (o in 0 until omittedMatchOffset) {
                        val skipped = words[cursor + o]
                        updates[skipped.referenceTokenId] = WordVisualStatus.CONFIRMED_ERROR
                        events.add(
                            buildEvent(
                                word = skipped,
                                type = ErrorType.OMISSION,
                                confidence = 0.86,
                                status = "confirmed",
                                hypothesis = hypothesis,
                                spokenText = spokenWord
                            )
                        )
                    }
                    val matchedWord = words[cursor + omittedMatchOffset]
                    updates[matchedWord.referenceTokenId] = WordVisualStatus.CORRECT
                    hasError = true
                    cursor += omittedMatchOffset + 1
                    sIdx++
                    continue
                } else {
                    updates[expectedWord.referenceTokenId] = WordVisualStatus.SUSPECTED_UNCERTAIN
                    hasSuspect = true
                    needCloud = true
                    break
                }
            }

            // Lookahead 1 word in spoken transcript -> possible ADDITION or REPETITION
            if (sIdx + 1 < spoken.size && isWordMatch(spoken[sIdx + 1], expectedWord) >= 0.80) {
                val isRepeat = cursor > 0 && isWordMatch(spokenWord, words[cursor - 1]) >= 0.80
                if (hypothesis.isFinal && !isRepeat) {
                    events.add(
                        buildEvent(
                            word = expectedWord,
                            type = ErrorType.ADDITION,
                            confidence = 0.80,
                            status = "confirmed",
                            hypothesis = hypothesis,
                            spokenText = spokenWord
                        )
                    )
                    updates[expectedWord.referenceTokenId] = WordVisualStatus.CONFIRMED_ERROR
                    hasError = true
                } else if (isRepeat) {
                    // Self-repetition is gently tolerated unless excessive
                    updates[expectedWord.referenceTokenId] = WordVisualStatus.CORRECT
                }
                sIdx++
                continue
            }

            // Check if the reciter jumped to a subsequent Ayah (SKIPPED_AYAH) or Mutashabihat!
            if (hypothesis.isFinal && spoken.size - sIdx >= 2) {
                val jumpTarget = detectJumpOrMutashabihat(
                    remainingSpoken = spoken.subList(sIdx, spoken.size),
                    currentAyah = currentAyah,
                    activePool = activeAyahPool
                )
                if (jumpTarget != null) {
                    val (targetAyah, jumpType) = jumpTarget
                    events.add(
                        buildEvent(
                            word = expectedWord,
                            type = jumpType,
                            confidence = 0.85,
                            status = "confirmed",
                            hypothesis = hypothesis,
                            spokenText = spoken.subList(sIdx, spoken.size).joinToString(" ")
                        )
                    )
                    updates[expectedWord.referenceTokenId] = WordVisualStatus.CONFIRMED_ERROR
                    return AlignmentResult(
                        nextWordIndex = cursor,
                        ayahCompleted = false,
                        detectedTargetAyah = targetAyah,
                        wordUpdates = updates,
                        newEvents = events,
                        recommendedState = RecitationState.CONFIRMED_ERROR,
                        requiresCloudVerification = true,
                        candidateWindow = guardian.getContextWindow(currentAyah.surahNo, currentAyah.ayahNo),
                        latencyMs = System.currentTimeMillis() - startedAt
                    )
                }
            }

            // Direct mismatch -> SUBSTITUTION or UNCERTAIN
            if (hypothesis.isFinal && hypothesis.confidence >= 0.55f) {
                updates[expectedWord.referenceTokenId] = WordVisualStatus.CONFIRMED_ERROR
                events.add(
                    buildEvent(
                        word = expectedWord,
                        type = ErrorType.SUBSTITUTION,
                        confidence = max(0.72, hypothesis.confidence.toDouble()),
                        status = "confirmed",
                        hypothesis = hypothesis,
                        spokenText = spokenWord
                    )
                )
                hasError = true
                needCloud = directSim >= 0.45 // borderline phonetic match benefits from cloud check
                break
            } else {
                updates[expectedWord.referenceTokenId] = WordVisualStatus.SUSPECTED_UNCERTAIN
                hasSuspect = true
                needCloud = true
                break
            }
        }

        val state = when {
            hasError -> RecitationState.CONFIRMED_ERROR
            hasSuspect -> RecitationState.SUSPECTED_ERROR
            updates.isNotEmpty() -> RecitationState.CORRECT_RECITATION
            else -> RecitationState.LISTENING
        }

        return AlignmentResult(
            nextWordIndex = cursor,
            ayahCompleted = cursor >= words.size && !hasError && !hasSuspect,
            wordUpdates = updates,
            newEvents = events,
            recommendedState = state,
            requiresCloudVerification = needCloud,
            candidateWindow = if (needCloud) guardian.getContextWindow(currentAyah.surahNo, currentAyah.ayahNo) else emptyList(),
            latencyMs = System.currentTimeMillis() - startedAt
        )
    }

    private fun detectJumpOrMutashabihat(
        remainingSpoken: List<String>,
        currentAyah: QuranAyah,
        activePool: List<QuranAyah>
    ): Pair<QuranAyah, ErrorType>? {
        val currentIdx = activePool.indexOfFirst {
            it.surahNo == currentAyah.surahNo && it.ayahNo == currentAyah.ayahNo
        }
        // Check next 1..3 ayahs in pool -> SKIPPED_AYAH
        if (currentIdx >= 0) {
            for (offset in 1..3) {
                val candidate = activePool.getOrNull(currentIdx + offset) ?: break
                if (phraseOverlapScore(remainingSpoken, candidate.words) >= 0.70) {
                    return candidate to ErrorType.SKIPPED_AYAH
                }
            }
        }
        // Check Mutashabihat across the Quran
        val similar = guardian.findSimilarAyahs(currentAyah, limit = 3)
        for (sim in similar) {
            if (phraseOverlapScore(remainingSpoken, sim.words) >= 0.65) {
                return sim to ErrorType.MUTASHABIHAT_CONFUSION
            }
        }
        // Check arbitrary wrong jump
        val globalCandidates = guardian.lookupBigramCandidates(remainingSpoken)
        for ((s, a) in globalCandidates) {
            if (s == currentAyah.surahNo && a == currentAyah.ayahNo) continue
            val ayah = guardian.getAyah(s, a) ?: continue
            if (phraseOverlapScore(remainingSpoken, ayah.words) >= 0.75) {
                return ayah to ErrorType.WRONG_JUMP
            }
        }
        return null
    }

    private fun prefixMatchCount(spoken: List<String>, words: List<QuranWord>): Int {
        var count = 0
        val limit = min(spoken.size, words.size)
        for (i in 0 until limit) {
            if (isWordMatch(spoken[i], words[i]) >= 0.78) count++ else break
        }
        return count
    }

    private fun phraseOverlapScore(spoken: List<String>, targetWords: List<QuranWord>): Double {
        if (spoken.isEmpty() || targetWords.isEmpty()) return 0.0
        var matched = 0
        var tCursor = 0
        for (sw in spoken) {
            for (j in tCursor until min(tCursor + 3, targetWords.size)) {
                if (isWordMatch(sw, targetWords[j]) >= 0.78) {
                    matched++
                    tCursor = j + 1
                    break
                }
            }
        }
        return matched.toDouble() / spoken.size.toDouble()
    }

    /**
     * Weighted edit distance + Arabic phonetic similarity against Qaloon reference variants.
     */
    fun isWordMatch(spokenNorm: String, refWord: QuranWord): Double {
        if (spokenNorm.isBlank()) return 0.0
        if (spokenNorm in refWord.acceptedQaloonVariants) return 1.0

        var best = 0.0
        for (variant in refWord.acceptedQaloonVariants) {
            val sim = weightedArabicSimilarity(spokenNorm, variant)
            if (sim > best) best = sim
        }
        return best
    }

    private fun weightedArabicSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0

        val n = a.length
        val m = b.length
        val dp = Array(n + 1) { DoubleArray(m + 1) }
        for (i in 0..n) dp[i][0] = i.toDouble()
        for (j in 0..m) dp[0][j] = j.toDouble()

        for (i in 1..n) {
            for (j in 1..m) {
                val subCost = substitutionWeight(a[i - 1], b[j - 1])
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1.0, dp[i][j - 1] + 1.0),
                    dp[i - 1][j - 1] + subCost
                )
            }
        }
        val dist = dp[n][m]
        return (1.0 - dist / maxLen.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun substitutionWeight(c1: Char, c2: Char): Double {
        if (c1 == c2) return 0.0
        // Common ASR phonetic confusions in Arabic (still flagged if different, lower weight than unrelated letters)
        val phoneticPairs = setOf(
            setOf('ت', 'ط'), setOf('د', 'ض'), setOf('ذ', 'ز'), setOf('ذ', 'ظ'),
            setOf('ظ', 'ض'), setOf('س', 'ص'), setOf('س', 'ث'), setOf('ق', 'ك'),
            setOf('ح', 'ه'), setOf('ع', 'ا'), setOf('ي', 'ا'), setOf('و', 'ا')
        )
        val pair = setOf(c1, c2)
        return if (pair in phoneticPairs) 0.55 else 1.0
    }

    private fun buildEvent(
        word: QuranWord,
        type: ErrorType,
        confidence: Double,
        status: String,
        hypothesis: AsrHypothesis,
        spokenText: String
    ): StructuredErrorEvent = StructuredErrorEvent(
        surahId = word.surahId,
        ayahId = word.ayahId,
        wordIndex = word.wordIndex,
        errorType = type,
        confidence = confidence,
        audioStartMs = hypothesis.audioStartMs,
        audioEndMs = hypothesis.audioEndMs,
        status = status,
        referenceTokenId = word.referenceTokenId,
        spokenHypothesis = spokenText
    )
}
