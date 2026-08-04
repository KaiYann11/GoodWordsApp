package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExposureEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ExposureEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<ExposureEventEntity>)

    @Query("SELECT * FROM exposure_events ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<ExposureEventEntity>>

    @Query(
        "SELECT * FROM exposure_events " +
            "WHERE occurredAt BETWEEN :start AND :end " +
            "ORDER BY occurredAt DESC"
    )
    suspend fun getEventsBetween(start: Long, end: Long): List<ExposureEventEntity>

    @Query(
        "SELECT COUNT(*) FROM exposure_events " +
            "WHERE contentItemId = :contentItemId " +
            "AND eventType = :eventType " +
            "AND occurredAt BETWEEN :start AND :end"
    )
    suspend fun countEventsForRange(
        contentItemId: Long,
        eventType: ExposureEventType,
        start: Long,
        end: Long
    ): Int

    @Query(
        "SELECT DISTINCT contentItemId FROM exposure_events " +
            "WHERE eventType = :eventType " +
            "AND occurredAt BETWEEN :start AND :end"
    )
    suspend fun getContentIdsForRange(
        eventType: ExposureEventType,
        start: Long,
        end: Long
    ): List<Long>

    @Query(
        "DELETE FROM exposure_events " +
            "WHERE contentItemId = :contentItemId " +
            "AND eventType = :eventType " +
            "AND occurredAt BETWEEN :start AND :end"
    )
    suspend fun deleteEventsForRange(
        contentItemId: Long,
        eventType: ExposureEventType,
        start: Long,
        end: Long
    )

    @Query(
        "DELETE FROM exposure_events " +
            "WHERE eventType = :eventType " +
            "AND occurredAt BETWEEN :start AND :end"
    )
    suspend fun deleteEventsByTypeForRange(
        eventType: ExposureEventType,
        start: Long,
        end: Long
    ): Int

    @Query("SELECT * FROM exposure_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExposureEventEntity?

    @Query("SELECT * FROM exposure_events")
    suspend fun getAll(): List<ExposureEventEntity>

    @Query("DELETE FROM exposure_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Query("DELETE FROM exposure_events")
    suspend fun clearAll()
}
