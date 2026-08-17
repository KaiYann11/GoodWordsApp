package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY dueDate DESC, createdAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoEntity>)

    @Query("SELECT * FROM todos ORDER BY dueDate DESC, createdAt ASC")
    suspend fun getAll(): List<TodoEntity>

    @Query("SELECT * FROM todos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TodoEntity?

    /** 알람을 다시 걸 대상. 기기를 껐다 켜면 예약이 모두 사라지므로 여기서 다시 읽습니다. */
    @Query("SELECT * FROM todos WHERE remindAt IS NOT NULL AND doneAt IS NULL")
    suspend fun getPendingReminders(): List<TodoEntity>

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM todos WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM todos")
    suspend fun clearAll()
}
