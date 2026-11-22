package com.example.data.local

import kotlinx.coroutines.flow.Flow

class TasmeeRepository(private val dao: TasmeeDao) {

    val sessions: Flow<List<RecitationSessionEntity>> = dao.observeAllSessions()
    val recentErrors: Flow<List<RecitationErrorEntity>> = dao.observeRecentErrors()
    val allReviewSchedules: Flow<List<AyahReviewScheduleEntity>> = dao.observeAllReviewSchedules()

    fun observeDueReviews(nowMs: Long = System.currentTimeMillis()): Flow<List<AyahReviewScheduleEntity>> =
        dao.observeDueReviews(nowMs)

    suspend fun saveSessionWithErrors(
        session: RecitationSessionEntity,
        errors: List<RecitationErrorEntity>
    ): Long {
        val sessionId = dao.insertSession(session)
        if (errors.isNotEmpty()) {
            dao.insertErrors(errors.map { it.copy(sessionId = sessionId) })
        }
        return sessionId
    }

    suspend fun getSessionById(sessionId: Long): RecitationSessionEntity? =
        dao.getSessionById(sessionId)

    suspend fun getErrorsForSession(sessionId: Long): List<RecitationErrorEntity> =
        dao.getErrorsForSession(sessionId)

    suspend fun getReviewSchedule(ayahKey: String): AyahReviewScheduleEntity? =
        dao.getReviewSchedule(ayahKey)

    suspend fun upsertReviewSchedule(schedule: AyahReviewScheduleEntity) =
        dao.upsertReviewSchedule(schedule)

    suspend fun deleteSession(sessionId: Long) =
        dao.deleteSessionById(sessionId)

    suspend fun wipeAllHistory() {
        dao.deleteAllErrors()
        dao.deleteAllSessions()
        dao.deleteAllSchedules()
    }
}
