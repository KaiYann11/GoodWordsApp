package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<RoutineEntity>)

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoutineEntity?

    @Query("SELECT * FROM routines WHERE reminderEnabled = 1 ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomReminderRoutine(): RoutineEntity?

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM routines")
    suspend fun clearAll()
}
