package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recitation_sessions")
data class RecitationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startedAt: Long,
    val endedAt: Long,
    val modeName: String,
    val scopeTitle: String,
    val surahNo: Int,
    val totalWordsRecited: Int,
    val correctWords: Int,
    val confirmedErrorsCount: Int,
    val selfCorrectedCount: Int,
    val accuracyPercent: Int,
    val estimatedWer: Double,
    val avgLatencyMs: Long,
    val aiSummary: String = ""
)

@Entity(
    tableName = "recitation_errors",
    foreignKeys = [
        ForeignKey(
            entity = RecitationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("referenceTokenId")]
)
data class RecitationErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val surahNo: Int,
    val ayahNo: Int,
    val wordIndex: Int,
    val referenceTokenId: String,
    val errorTypeKey: String,
    val errorTypeLabelAr: String,
    val spokenHypothesis: String,
    val confidence: Double,
    val resolvedBySelfCorrection: Boolean,
    val timestamp: Long
)

@Entity(tableName = "ayah_review_schedules")
data class AyahReviewScheduleEntity(
    @PrimaryKey val ayahKey: String, // e.g., "S002:A255"
    val surahNo: Int,
    val ayahNo: Int,
    val surahNameAr: String,
    val repetitionCount: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val nextReviewAt: Long,
    val totalErrorsCount: Int,
    val lastReviewedAt: Long
)
