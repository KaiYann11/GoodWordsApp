package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodLogDao {
    /** 최근 것이 위입니다. */
    @Query("SELECT * FROM mood_logs ORDER BY entryDate DESC, updatedAt DESC, createdAt DESC, syncId DESC")
    fun observeAll(): Flow<List<MoodLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MoodLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<MoodLogEntity>)

    @Query("SELECT * FROM mood_logs ORDER BY entryDate DESC, updatedAt DESC, createdAt DESC, syncId DESC")
    suspend fun getAll(): List<MoodLogEntity>

    /** 하루의 흐름을 보존합니다. 마지막 기록을 고르는 규칙은 DayMood가 맡습니다. */
    @Query("SELECT * FROM mood_logs WHERE entryDate = :entryDate")
    suspend fun getOnDate(entryDate: String): List<MoodLogEntity>

    /** 동기화로 숫자 id가 바뀌어도 지우려던 바로 그 기록을 찾습니다. */
    @Query("SELECT * FROM mood_logs WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): MoodLogEntity?

    @Query("DELETE FROM mood_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mood_logs WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM mood_logs")
    suspend fun clearAll()
}
