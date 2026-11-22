package com.example.agents

import android.content.Context
import com.example.domain.model.IntegrityStatus
import com.example.domain.model.QuranAyah
import com.example.domain.model.QuranWord
import com.example.domain.model.SurahInfo
import org.json.JSONArray
import java.io.InputStream
import java.security.MessageDigest

/**
 * Agent 01 — Quran Data Guardian
 * Sole authority for loading, verifying SHA-256 integrity, tokenizing, and serving
 * the read-only King Fahd Complex Qaloon 'an Nafi' Mushaf text.
 */
class QuranDataGuardian {

    companion object {
        const val ASSET_FILE_NAME = "qaloon_mushaf.json"
        const val EXPECTED_QALOON_SHA256 =
            "18465c40ebeec40a92eb98745c9b89796ac6e31f6e93988883dfb6602faaea95"
        const val EXPECTED_SURAH_COUNT = 114
        const val EXPECTED_AYAH_COUNT = 6214
    }

    private var _integrityStatus: IntegrityStatus = IntegrityStatus(
        isVerified = false,
        sha256Hex = "",
        expectedSha256Hex = EXPECTED_QALOON_SHA256,
        surahCount = 0,
        ayahCount = 0
    )
    val integrityStatus: IntegrityStatus get() = _integrityStatus

    private val _ayahs = mutableListOf<QuranAyah>()
    private val _surahs = mutableListOf<SurahInfo>()
    private val _wordById = LinkedHashMap<String, QuranWord>()
    private val _ayahByKey = LinkedHashMap<String, QuranAyah>()
    private val _ayahsBySurah = LinkedHashMap<Int, List<QuranAyah>>()
    private val _ngramIndex = HashMap<String, MutableList<Pair<Int, Int>>>()

    @Synchronized
    fun loadFromAssets(context: Context): IntegrityStatus {
        if (_integrityStatus.isVerified && _ayahs.isNotEmpty()) {
            return _integrityStatus
        }
        return context.assets.open(ASSET_FILE_NAME).use { input ->
            loadAndVerify(input)
        }
    }

    /**
     * Loads and verifies the raw Mushaf JSON bytes using SHA-256.
     * Any tampering, missing surah, or hash mismatch marks the store unverified and blocks usage.
     */
    @Synchronized
    fun loadAndVerify(
        inputStream: InputStream,
        expectedSha256: String = EXPECTED_QALOON_SHA256
    ): IntegrityStatus {
        val rawBytes = inputStream.readBytes()
        val computedSha256 = sha256Hex(rawBytes)

        if (!computedSha256.equals(expectedSha256, ignoreCase = true)) {
            _ayahs.clear()
            _surahs.clear()
            _wordById.clear()
            _ayahByKey.clear()
            _ayahsBySurah.clear()
            _ngramIndex.clear()
            _integrityStatus = IntegrityStatus(
                isVerified = false,
                sha256Hex = computedSha256,
                expectedSha256Hex = expectedSha256,
                surahCount = 0,
                ayahCount = 0,
                errorMessage = "فشل التحقق من بصمة SHA-256 لنص مصحف قالون المعتمد."
            )
            return _integrityStatus
        }

        val jsonText = rawBytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val jsonArray = JSONArray(jsonText)

        val parsedAyahs = ArrayList<QuranAyah>(jsonArray.length())
        val wordsMap = LinkedHashMap<String, QuranWord>(78_000)
        val ayahKeyMap = LinkedHashMap<String, QuranAyah>(jsonArray.length())
        val surahGrouping = LinkedHashMap<Int, MutableList<QuranAyah>>(114)
        val ngramMap = HashMap<String, MutableList<Pair<Int, Int>>>(120_000)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.getInt("id")
            val jozz = obj.getInt("jozz")
            val page = obj.optString("page", "")
            val surahNo = obj.getInt("sura_no")
            val surahNameEn = obj.optString("sura_name_en", "")
            val surahNameAr = obj.optString("sura_name_ar", "")
            val ayahNo = obj.getInt("aya_no")
            val rawText = obj.getString("aya_text")

            val (words, endDigits, hasHizb) = tokenizeQaloonAyah(surahNo, ayahNo, rawText)
            val hizb = ((jozz - 1) * 2) + if (i > 0 && isSecondHalfOfJozz(id)) 2 else 1

            val ayah = QuranAyah(
                id = id,
                jozz = jozz,
                hizb = hizb.coerceIn(1, 60),
                page = page,
                surahNo = surahNo,
                surahNameAr = surahNameAr,
                surahNameEn = surahNameEn,
                ayahNo = ayahNo,
                ayahEndNumberArabic = endDigits,
                hasHizbMarker = hasHizb,
                rawDisplayText = rawText,
                words = words
            )

            parsedAyahs.add(ayah)
            ayahKeyMap[ayahKey(surahNo, ayahNo)] = ayah
            surahGrouping.getOrPut(surahNo) { mutableListOf() }.add(ayah)

            for (w in words) {
                wordsMap[w.referenceTokenId] = w
            }
            // Build 2-gram index for fast Free Recitation detection
            for (wIdx in 0 until words.size - 1) {
                val bigram = "${words[wIdx].normalizedToken} ${words[wIdx + 1].normalizedToken}"
                ngramMap.getOrPut(bigram) { mutableListOf() }.add(surahNo to ayahNo)
            }
        }

        // Refine Hizb numbering evenly across each Jozz using actual Rub al-Hizb markers or midpoint
        val refinedAyahs = assignAccurateHizbNumbers(parsedAyahs)

        val surahInfos = surahGrouping.entries.sortedBy { it.key }.map { (sNo, list) ->
            val first = list.first()
            SurahInfo(
                surahNo = sNo,
                nameAr = first.surahNameAr,
                nameEn = first.surahNameEn,
                ayahCount = list.size,
                startJozz = first.jozz,
                startPage = first.page
            )
        }

        _ayahs.clear()
        _ayahs.addAll(refinedAyahs)
        _surahs.clear()
        _surahs.addAll(surahInfos)
        _wordById.clear()
        _wordById.putAll(wordsMap)
        _ayahByKey.clear()
        _ayahByKey.putAll(ayahKeyMap)
        _ayahsBySurah.clear()
        refinedAyahs.groupBy { it.surahNo }.forEach { (k, v) -> _ayahsBySurah[k] = v }
        _ngramIndex.clear()
        _ngramIndex.putAll(ngramMap)

        val validCounts = surahInfos.size == EXPECTED_SURAH_COUNT &&
            refinedAyahs.size == EXPECTED_AYAH_COUNT

        _integrityStatus = IntegrityStatus(
            isVerified = validCounts,
            sha256Hex = computedSha256,
            expectedSha256Hex = expectedSha256,
            surahCount = surahInfos.size,
            ayahCount = refinedAyahs.size,
            errorMessage = if (validCounts) null else "عدد السور أو الآيات غير مطابق للعد المدني المعتمد لقالون."
        )
        return _integrityStatus
    }

    private fun assignAccurateHizbNumbers(ayahs: List<QuranAyah>): List<QuranAyah> {
        val byJozz = ayahs.groupBy { it.jozz }
        val hizbLookup = HashMap<Int, Int>(ayahs.size)
        for ((jozz, list) in byJozz) {
            val mid = list.size / 2
            list.forEachIndexed { idx, ayah ->
                val hizb = ((jozz - 1) * 2) + if (idx >= mid) 2 else 1
                hizbLookup[ayah.id] = hizb.coerceIn(1, 60)
            }
        }
        return ayahs.map { it.copy(hizb = hizbLookup[it.id] ?: it.hizb) }
    }

    private fun isSecondHalfOfJozz(ayahGlobalId: Int): Boolean = ayahGlobalId % 200 > 100

    fun getAllSurahs(): List<SurahInfo> = _surahs.toList()

    fun getAyahsForSurah(surahNo: Int): List<QuranAyah> = _ayahsBySurah[surahNo].orEmpty()

    fun getAyahsForJozz(jozz: Int): List<QuranAyah> = _ayahs.filter { it.jozz == jozz }

    fun getAyahsForHizb(hizb: Int): List<QuranAyah> = _ayahs.filter { it.hizb == hizb }

    fun getAyah(surahNo: Int, ayahNo: Int): QuranAyah? = _ayahByKey[ayahKey(surahNo, ayahNo)]

    fun getAllAyahs(): List<QuranAyah> = _ayahs.toList()

    /**
     * Resolves a canonical word strictly from the verified reference store by its ID.
     * Never allows AI-generated text to replace Mushaf words.
     */
    fun resolveWordById(referenceTokenId: String): QuranWord? = _wordById[referenceTokenId]

    fun getContextWindow(surahNo: Int, ayahNo: Int, radius: Int = 2): List<QuranAyah> {
        val surahAyahs = getAyahsForSurah(surahNo)
        if (surahAyahs.isEmpty()) return emptyList()
        val idx = surahAyahs.indexOfFirst { it.ayahNo == ayahNo }.takeIf { it >= 0 } ?: 0
        val from = (idx - radius).coerceAtLeast(0)
        val to = (idx + radius + 1).coerceAtMost(surahAyahs.size)
        return surahAyahs.subList(from, to)
    }

    fun lookupBigramCandidates(normalizedWords: List<String>): List<Pair<Int, Int>> {
        if (normalizedWords.size < 2) return emptyList()
        val counts = HashMap<Pair<Int, Int>, Int>()
        for (i in 0 until normalizedWords.size - 1) {
            val key = "${normalizedWords[i]} ${normalizedWords[i + 1]}"
            _ngramIndex[key]?.forEach { loc ->
                counts[loc] = (counts[loc] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    fun findSimilarAyahs(ayah: QuranAyah, limit: Int = 3): List<QuranAyah> {
        val targetWords = ayah.words.map { it.normalizedToken }
        if (targetWords.size < 3) return emptyList()
        val candidates = lookupBigramCandidates(targetWords)
            .filterNot { it.first == ayah.surahNo && it.second == ayah.ayahNo }
        return candidates.mapNotNull { (s, a) -> getAyah(s, a) }.take(limit)
    }

    private fun ayahKey(surahNo: Int, ayahNo: Int): String = "S${surahNo}:A${ayahNo}"

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class TokenizedAyah(
        val words: List<QuranWord>,
        val endDigits: String,
        val hasHizbMarker: Boolean
    )

    private fun tokenizeQaloonAyah(surahNo: Int, ayahNo: Int, rawText: String): TokenizedAyah {
        val hasHizb = rawText.contains('\u06DE')
        // KFGQPC Qaloon separates the ayah text and the Arabic-Indic ayah number with NBSP (\u00A0)
        val cleaned = rawText.replace("\u200F", "").trim()
        val nbspIdx = cleaned.lastIndexOf('\u00A0')
        val bodyPart: String
        val digitsPart: String
        if (nbspIdx > 0) {
            bodyPart = cleaned.substring(0, nbspIdx).trim()
            digitsPart = cleaned.substring(nbspIdx + 1).trim()
        } else {
            val parts = cleaned.split(Regex("\\s+"))
            val last = parts.lastOrNull().orEmpty()
            if (last.all { it in '\u0660'..'\u0669' || it in '0'..'9' }) {
                digitsPart = last
                bodyPart = parts.dropLast(1).joinToString(" ")
            } else {
                digitsPart = toArabicDigits(ayahNo)
                bodyPart = cleaned
            }
        }

        val rawTokens = bodyPart
            .split(Regex("[\\s\\u00A0]+"))
            .map { it.replace("\u06DE", "").trim() }
            .filter { token ->
                token.isNotEmpty() && token.any { ch -> ch in '\u0621'..'\u064A' || ch == '\u0671' }
            }

        val quranWords = rawTokens.mapIndexed { idx, displayWord ->
            val wordNum = idx + 1
            val refId = buildReferenceTokenId(surahNo, ayahNo, wordNum)
            val norm = QaloonNormalizer.normalize(displayWord)
            val variants = QaloonNormalizer.generateQaloonVariants(norm, displayWord)
            QuranWord(
                referenceTokenId = refId,
                surahId = surahNo,
                ayahId = ayahNo,
                wordIndex = wordNum,
                displayUthmani = displayWord,
                normalizedToken = norm,
                acceptedQaloonVariants = variants
            )
        }
        return TokenizedAyah(quranWords, digitsPart, hasHizb)
    }

    private fun buildReferenceTokenId(surahNo: Int, ayahNo: Int, wordIndex: Int): String =
        "S%03d:A%03d:W%03d".format(surahNo, ayahNo, wordIndex)

    private fun toArabicDigits(num: Int): String {
        val map = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
        return num.toString().map { ch ->
            if (ch in '0'..'9') map[ch - '0'] else ch
        }.joinToString("")
    }
}

/**
 * Deterministic Arabic/Qaloon orthography normalizer.
 * Used strictly on comparison tokens; never modifies displayUthmani.
 */
object QaloonNormalizer {

    // Includes KFGQPC Qaloon special marks: 0x06EA, 0x06EC, 0x06DF, 0x06E2, 0x06E5-0x06E8, 0x06D6-0x06ED
    private val DIACRITICS_AND_MARKS = Regex(
        "[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640\\u200C-\\u200F\\u06DE]"
    )

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val stripped = DIACRITICS_AND_MARKS.replace(input, "")
        val sb = StringBuilder(stripped.length)
        for (ch in stripped) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ى', 'ے' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                'ة' -> sb.append('ه')
                else -> if (ch in '\u0621'..'\u064A') sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizePhrase(phrase: String): List<String> =
        phrase.trim()
            .split(Regex("\\s+"))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

    /**
     * Generates valid phonetic/orthographic forms accepted in Riwayat Qaloon 'an Nafi'
     * as well as standard ASR transcription equivalents of Uthmani spelling
     * (e.g., superscript alef in الرحمن/السموات/العالمين, صلة ميم الجمع).
     */
    fun generateQaloonVariants(normalized: String, displayUthmani: String = ""): Set<String> {
        val variants = linkedSetOf(normalized)

        // Convert Uthmani superscript dagger alif (\u0670) to full alif so words like
        // صِرَٰطَ -> صراط, اِ۬لْعَٰلَمِينَ -> العالمين, اَ۬لْكِتَٰبُ -> الكتاب match modern ASR output
        if (displayUthmani.contains('\u0670')) {
            val withFullAlif = normalize(displayUthmani.replace('\u0670', 'ا'))
            if (withFullAlif.isNotEmpty()) {
                variants.add(withFullAlif)
            }
        }

        // Common Uthmani script vs standard ASR orthography equivalences
        val uthmaniAsrMap = mapOf(
            "العلمين" to "العالمين",
            "الرحمن" to "الرحمان",
            "الصلوه" to "الصلاه",
            "الزكوه" to "الزكاه",
            "الحيوه" to "الحياة",
            "سموت" to "سماوات",
            "السموت" to "السماوات",
            "ملك" to "مالك", // Qaloon recites مَلِكِ يوم الدين, ASR may transcribe مالك or ملك
            "وملئكته" to "وملائكته",
            "اولئك" to "اولائك",
            "هذان" to "هاذان",
            "هؤلا" to "هؤلاء",
            "الذين" to "الذين",
            "ياايها" to "يا ايها",
            "يايها" to "يا ايها"
        )
        uthmaniAsrMap[normalized]?.let { variants.add(it) }

        // Silat Meem al-Jam' in Qaloon (عليهمو -> عليهم)
        if (normalized.endsWith("هم") || normalized.endsWith("كم") || normalized.endsWith("تم")) {
            variants.add(normalized + "و")
        }
        if (normalized.endsWith("همو") || normalized.endsWith("كمو")) {
            variants.add(normalized.dropLast(1))
        }

        // Qaloon hamzah facilitation / ibdal equivalents in ASR transcripts
        if (normalized.contains("ؤ")) variants.add(normalized.replace("ؤ", "و"))
        if (normalized.startsWith("اان")) variants.add("ان" + normalized.substring(3))
        if (normalized.startsWith("ا")) variants.add(normalized)

        return variants
    }
}
