package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TasmeeDao {

    @Query("SELECT * FROM recitation_sessions ORDER BY endedAt DESC")
    fun observeAllSessions(): Flow<List<RecitationSessionEntity>>

    @Query("SELECT * FROM recitation_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): RecitationSessionEntity?

    @Query("SELECT * FROM recitation_errors WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getErrorsForSession(sessionId: Long): List<RecitationErrorEntity>

    @Query("SELECT * FROM recitation_errors ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentErrors(): Flow<List<RecitationErrorEntity>>

    @Query("SELECT * FROM ayah_review_schedules ORDER BY nextReviewAt ASC")
    fun observeAllReviewSchedules(): Flow<List<AyahReviewScheduleEntity>>

    @Query("SELECT * FROM ayah_review_schedules WHERE nextReviewAt <= :nowMs ORDER BY totalErrorsCount DESC, nextReviewAt ASC")
    fun observeDueReviews(nowMs: Long): Flow<List<AyahReviewScheduleEntity>>

    @Query("SELECT * FROM ayah_review_schedules WHERE ayahKey = :ayahKey LIMIT 1")
    suspend fun getReviewSchedule(ayahKey: String): AyahReviewScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecitationSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrors(errors: List<RecitationErrorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReviewSchedule(schedule: AyahReviewScheduleEntity)

    @Query("DELETE FROM recitation_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("DELETE FROM recitation_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM recitation_errors")
    suspend fun deleteAllErrors()

    @Query("DELETE FROM ayah_review_schedules")
    suspend fun deleteAllSchedules()
}
