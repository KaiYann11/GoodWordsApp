package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deletion: DeletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deletions: List<DeletionEntity>)

    @Query("SELECT * FROM deletions ORDER BY deletedAt DESC")
    suspend fun getAll(): List<DeletionEntity>

    @Query("DELETE FROM deletions")
    suspend fun clearAll()

    /** 표식이 계속 쌓이지 않도록 오래된 것은 정리한다. */
    @Query("DELETE FROM deletions WHERE deletedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
