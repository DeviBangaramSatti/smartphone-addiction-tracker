package com.smarttracker.data.local.dao

import androidx.room.*
import com.smarttracker.data.local.entity.UsageLog
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageLogDao {

    // ── Writes ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(log: UsageLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<UsageLog>)

    @Query("UPDATE usage_logs SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    // ── Queries ─────────────────────────────────────────────────────────────

    /** Live stream of today's usage, ordered by time descending */
    @Query("SELECT * FROM usage_logs WHERE date = :date ORDER BY usageDurationMs DESC")
    fun observeByDate(date: String): Flow<List<UsageLog>>

    /** All logs for a date range (for weekly charts) */
    @Query("SELECT * FROM usage_logs WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: String, to: String): List<UsageLog>

    /** Records not yet pushed to Firestore */
    @Query("SELECT * FROM usage_logs WHERE synced = 0")
    suspend fun getUnsynced(): List<UsageLog>

    /** Aggregate daily usage in ms — used for ML feature extraction */
    @Query("SELECT SUM(usageDurationMs) FROM usage_logs WHERE date = :date")
    suspend fun totalDurationMs(date: String): Long?

    /** Total sessions (launchCount sum) for a date */
    @Query("SELECT SUM(launchCount) FROM usage_logs WHERE date = :date")
    suspend fun totalSessions(date: String): Int?
}
