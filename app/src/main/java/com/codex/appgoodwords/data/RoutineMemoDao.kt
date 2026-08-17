package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineMemoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memo: RoutineMemoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memos: List<RoutineMemoEntity>)

    @Query("SELECT * FROM routine_memos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RoutineMemoEntity>>

    @Query("SELECT * FROM routine_memos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoutineMemoEntity?

    @Query("SELECT * FROM routine_memos")
    suspend fun getAll(): List<RoutineMemoEntity>

    @Query("SELECT * FROM routine_memos WHERE routineId = :routineId")
    suspend fun getByRoutineId(routineId: Long): List<RoutineMemoEntity>

    @Query("DELETE FROM routine_memos WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM routine_memos WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM routine_memos")
    suspend fun clearAll()
}
