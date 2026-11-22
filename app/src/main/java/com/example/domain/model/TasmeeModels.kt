package com.example.domain.model

import androidx.compose.runtime.Immutable

/**
 * Immutable reference word from the verified KFGQPC Qaloon 'an Nafi' Mushaf.
 * Display text and normalized comparison text are strictly separated.
 */
@Immutable
data class QuranWord(
    val referenceTokenId: String, // e.g., S001:A001:W001
    val surahId: Int,
    val ayahId: Int,
    val wordIndex: Int,
    val displayUthmani: String,
    val normalizedToken: String,
    val acceptedQaloonVariants: Set<String>
)

@Immutable
data class QuranAyah(
    val id: Int,
    val jozz: Int,
    val hizb: Int,
    val page: String,
    val surahNo: Int,
    val surahNameAr: String,
    val surahNameEn: String,
    val ayahNo: Int,
    val ayahEndNumberArabic: String,
    val hasHizbMarker: Boolean,
    val rawDisplayText: String,
    val words: List<QuranWord>
)

@Immutable
data class SurahInfo(
    val surahNo: Int,
    val nameAr: String,
    val nameEn: String,
    val ayahCount: Int,
    val startJozz: Int,
    val startPage: String
)

enum class RecitationMode(val titleAr: String, val descAr: String) {
    FREE_RECITATION(
        "التسميع الحر",
        "يتعرف التطبيق تلقائيًا على السورة والآية أثناء تلاوتك"
    ),
    SPECIFIC_SURAH(
        "تسميع سورة محددة",
        "اختر أي سورة من المصحف الشريف برواية قالون"
    ),
    JUZ_OR_HIZB(
        "تسميع جزء أو حزب",
        "مراجعة مركزة لجزء (١–٣٠) أو حزب (١–٦٠)"
    ),
    HIDDEN_MEMORIZATION(
        "التسميع المخفي",
        "إخفاء الكلمات وإظهارها تدريجيًا فور قراءتها"
    ),
    TRAINING_MUSHAF(
        "وضع التدريب",
        "عرض المصحف كاملًا ومتابعة الكلمات لحظيًا"
    ),
    REVIEW_PAST_ERRORS(
        "مراجعة الأخطاء",
        "تسميع الآيات المتكرر فيها الخطأ وفق التكرار المتباعد"
    )
}

/**
 * Explicit 9-state State Machine governing real-time recitation processing.
 */
enum class RecitationState(val labelAr: String) {
    IDLE("جاهز للتسميع"),
    LISTENING("يستمع لتلاوتك..."),
    MATCHING("جارٍ مطابقة القراءة..."),
    CORRECT_RECITATION("تلاوة صحيحة"),
    SUSPECTED_ERROR("لم تتضح القراءة، أعد المحاولة بهدوء"),
    CONFIRMED_ERROR("تم رصد موضع يحتاج تصحيحًا"),
    WAITING_FOR_CORRECTION("بانتظار قراءة الكلمة الصحيحة"),
    RESUMING("تم التصحيح، تفضل بالمتابعة"),
    COMPLETED("أتممت الجلسة بنجاح")
}

enum class ErrorType(val labelAr: String, val jsonKey: String) {
    SUBSTITUTION("استبدال كلمة", "substitution"),
    OMISSION("نسيان كلمة", "omission"),
    ADDITION("إضافة كلمة", "addition"),
    SKIPPED_AYAH("تجاوز آية", "skipped_ayah"),
    MUTASHABIHAT_CONFUSION("خلط في المتشابهات", "mutashabihat_confusion"),
    WORD_REORDER("تغيير ترتيب الكلمات", "word_reorder"),
    REPETITION("تكرار كلمة أو آية", "repetition"),
    WRONG_JUMP("انتقال لموضع غير صحيح", "wrong_jump"),
    UNCERTAIN_SPEECH("قراءة غير واضحة", "uncertain_speech")
}

enum class WordVisualStatus {
    UNREAD,
    CORRECT,
    SUSPECTED_UNCERTAIN,
    CONFIRMED_ERROR,
    HINT_REVEALED
}

/**
 * Structured error event conforming to the system prompt's JSON schema.
 */
data class StructuredErrorEvent(
    val surahId: Int,
    val ayahId: Int,
    val wordIndex: Int,
    val errorType: ErrorType,
    val confidence: Double,
    val audioStartMs: Long,
    val audioEndMs: Long,
    val status: String, // "confirmed" | "uncertain" | "resolved"
    val referenceTokenId: String,
    val spokenHypothesis: String = ""
)

data class IntegrityStatus(
    val isVerified: Boolean,
    val sha256Hex: String,
    val expectedSha256Hex: String,
    val surahCount: Int,
    val ayahCount: Int,
    val riwayah: String = "قالون عن نافع",
    val tariq: String = "أبو نشيط عن قالون (الشاطبية)",
    val verseCountSystem: String = "العد المدني",
    val sourceCitation: String = "مجمع الملك فهد لطباعة المصحف الشريف — إصدار قالون v10",
    val errorMessage: String? = null
)

data class AsrHypothesis(
    val rawTranscript: String,
    val normalizedWords: List<String>,
    val confidence: Float,
    val isFinal: Boolean,
    val audioStartMs: Long,
    val audioEndMs: Long,
    val source: String = "device_asr"
)

data class SessionPerformanceMetrics(
    val totalReferenceWords: Int = 0,
    val correctWords: Int = 0,
    val confirmedErrors: Int = 0,
    val suspectedFalsePositivesAvoided: Int = 0,
    val selfCorrectedCount: Int = 0,
    val estimatedWer: Double = 0.0,
    val precision: Double = 1.0,
    val recall: Double = 1.0,
    val falsePositiveRate: Double = 0.0,
    val avgCorrectionLatencyMs: Long = 0L
)

@Immutable
data class QaloonReciter(
    val id: String,
    val nameAr: String,
    val riwayahLabel: String,
    val serverBaseUrl: String
) {
    fun buildSurahAudioUrl(surahNo: Int): String {
        val padded = surahNo.toString().padStart(3, '0')
        return "${serverBaseUrl.trimEnd('/')}/$padded.mp3"
    }
}

object QaloonRecitersCatalog {
    val reciters = listOf(
        QaloonReciter(
            id = "hudhaifi_qaloon",
            nameAr = "الشيخ علي بن عبدالرحمن الحذيفي",
            riwayahLabel = "قالون عن نافع — مرتل",
            serverBaseUrl = "https://cdn.mp3quran.net/audio/ali-hudhaifi/r2/"
        ),
        QaloonReciter(
            id = "husary_qaloon",
            nameAr = "الشيخ محمود خليل الحصري",
            riwayahLabel = "قالون عن نافع — مرتل",
            serverBaseUrl = "https://cdn.mp3quran.net/audio/mahmoud-husary/r5/"
        ),
        QaloonReciter(
            id = "naehi_qaloon",
            nameAr = "الشيخ وليد النائحي",
            riwayahLabel = "قالون عن نافع من طريق أبي نشيط",
            serverBaseUrl = "https://cdn.mp3quran.net/audio/waleed-naehi/r1/"
        ),
        QaloonReciter(
            id = "dokali_qaloon",
            nameAr = "الشيخ الدوكالي محمد العالم",
            riwayahLabel = "قالون عن نافع — مرتل",
            serverBaseUrl = "https://cdn.mp3quran.net/audio/dokali-alim/r1/"
        )
    )

    fun findById(id: String): QaloonReciter =
        reciters.firstOrNull { it.id == id } ?: reciters.first()
}
