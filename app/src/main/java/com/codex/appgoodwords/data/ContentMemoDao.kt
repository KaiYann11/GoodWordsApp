package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentMemoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: ContentMemoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memos: List<ContentMemoEntity>)

    @Query("SELECT * FROM content_memos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ContentMemoEntity>>

    @Query("SELECT * FROM content_memos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ContentMemoEntity?

    @Query("SELECT * FROM content_memos")
    suspend fun getAll(): List<ContentMemoEntity>

    @Query("SELECT * FROM content_memos WHERE contentItemId = :contentItemId")
    suspend fun getByContentItemId(contentItemId: Long): List<ContentMemoEntity>

    @Query("DELETE FROM content_memos WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM content_memos WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM content_memos")
    suspend fun clearAll()
}
