package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries ORDER BY entryDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diaries WHERE entryDate = :entryDate ORDER BY createdAt DESC")
    suspend fun getByDate(entryDate: String): List<DiaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diary: DiaryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(diaries: List<DiaryEntity>)

    @Query("SELECT * FROM diaries ORDER BY entryDate DESC, createdAt DESC")
    suspend fun getAll(): List<DiaryEntity>

    @Query("SELECT * FROM diaries WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DiaryEntity?

    @Query("DELETE FROM diaries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM diaries")
    suspend fun clearAll()
}
