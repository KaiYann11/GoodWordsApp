package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodLogDao {
    /** 최근 것이 위입니다. */
    @Query("SELECT * FROM mood_logs ORDER BY entryDate DESC")
    fun observeAll(): Flow<List<MoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MoodLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<MoodLogEntity>)

    @Query("SELECT * FROM mood_logs ORDER BY entryDate DESC")
    suspend fun getAll(): List<MoodLogEntity>

    /** 하루에 하나라서, 그날 것이 있으면 고칩니다. 새로 넣으면 그날 기분이 둘이 됩니다. */
    @Query("SELECT * FROM mood_logs WHERE entryDate = :entryDate ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getByDate(entryDate: String): MoodLogEntity?

    @Query("DELETE FROM mood_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mood_logs WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM mood_logs")
    suspend fun clearAll()
}
