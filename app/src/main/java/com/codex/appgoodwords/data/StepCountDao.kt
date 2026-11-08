package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepCountDao {
    /** 최근 날이 위입니다. */
    @Query("SELECT * FROM step_counts ORDER BY date DESC")
    fun observeAll(): Flow<List<StepCountEntity>>

    @Query("SELECT * FROM step_counts ORDER BY date DESC")
    suspend fun getAll(): List<StepCountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(count: StepCountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(counts: List<StepCountEntity>)

    @Query("DELETE FROM step_counts WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM step_counts")
    suspend fun clearAll()
}
