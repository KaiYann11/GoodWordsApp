package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    // 하루에 밟는 차례대로 보여 준다. 번호가 겹치면 만든 지 오래된 쪽이 앞이다(RoutineOrder.sorted와 같은 규칙).
    @Query("SELECT * FROM routines ORDER BY orderIndex ASC, createdAt ASC, syncId ASC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<RoutineEntity>)

    @Query("SELECT * FROM routines ORDER BY orderIndex ASC, createdAt ASC, syncId ASC")
    suspend fun getAll(): List<RoutineEntity>

    /** 아직 루틴이 하나도 없으면 -1. 그래야 첫 루틴이 0번을 받는다. */
    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM routines")
    suspend fun maxOrderIndex(): Int

    @Query("UPDATE routines SET orderIndex = :orderIndex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOrder(id: Long, orderIndex: Int, updatedAt: Long)

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoutineEntity?

    @Query("SELECT * FROM routines WHERE reminderEnabled = 1 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomReminderRoutine(): RoutineEntity?

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM routines WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM routines")
    suspend fun clearAll()
}
