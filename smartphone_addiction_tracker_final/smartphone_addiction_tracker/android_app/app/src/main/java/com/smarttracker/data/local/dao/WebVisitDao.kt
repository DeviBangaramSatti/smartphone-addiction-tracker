package com.smarttracker.data.local.dao

import androidx.room.*
import com.smarttracker.data.local.entity.WebVisit
import kotlinx.coroutines.flow.Flow

@Dao
interface WebVisitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: WebVisit)

    @Query("SELECT * FROM web_visits WHERE date = :date ORDER BY visitedAt DESC")
    fun observeByDate(date: String): Flow<List<WebVisit>>

    @Query("SELECT * FROM web_visits WHERE synced = 0")
    suspend fun getUnsynced(): List<WebVisit>

    @Query("UPDATE web_visits SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    /** Top visited domains for analytics */
    @Query("""
        SELECT url, SUM(timeSpentMs) as totalMs 
        FROM web_visits 
        WHERE date = :date 
        GROUP BY url 
        ORDER BY totalMs DESC 
        LIMIT 10
    """)
    suspend fun getTopSitesByDate(date: String): List<SiteAggregate>
}

data class SiteAggregate(val url: String, val totalMs: Long)
