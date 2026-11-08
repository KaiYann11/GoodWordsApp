package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalTimeLogDao {
    /** 최근 것이 위입니다. */
    @Query("SELECT * FROM goal_time_logs ORDER BY loggedAt DESC, id DESC")
    fun observeAll(): Flow<List<GoalTimeLogEntity>>

    @Query("SELECT * FROM goal_time_logs ORDER BY loggedAt DESC, id DESC")
    suspend fun getAll(): List<GoalTimeLogEntity>

    @Query("SELECT * FROM goal_time_logs WHERE id = :id")
    suspend fun getById(id: Long): GoalTimeLogEntity?

    @Query("SELECT * FROM goal_time_logs WHERE goalSyncId = :goalSyncId ORDER BY loggedAt DESC, id DESC")
    suspend fun getByGoalSyncId(goalSyncId: String): List<GoalTimeLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: GoalTimeLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<GoalTimeLogEntity>)

    @Query("DELETE FROM goal_time_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM goal_time_logs WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM goal_time_logs")
    suspend fun clearAll()
}
