package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordLinkDao {
    /** 최근에 이은 것이 위입니다. */
    @Query("SELECT * FROM record_links ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<RecordLinkEntity>>

    @Query("SELECT * FROM record_links ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<RecordLinkEntity>

    @Query("SELECT * FROM record_links WHERE id = :id")
    suspend fun getById(id: Long): RecordLinkEntity?

    /** 이 기록에 닿은 줄. 어느 쪽 끝이든 봅니다 — 줄에는 방향이 없습니다. */
    @Query("SELECT * FROM record_links WHERE fromSyncId = :syncId OR toSyncId = :syncId ORDER BY createdAt DESC, id DESC")
    suspend fun getTouching(syncId: String): List<RecordLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: RecordLinkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<RecordLinkEntity>)

    @Query("DELETE FROM record_links WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM record_links WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM record_links")
    suspend fun clearAll()
}
