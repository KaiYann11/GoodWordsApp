package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineCheckDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(check: RoutineCheckEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checks: List<RoutineCheckEntity>)

    @Query("SELECT * FROM routine_checks ORDER BY checkedAt DESC")
    fun observeAll(): Flow<List<RoutineCheckEntity>>

    @Query(
        "SELECT routineId, COUNT(*) AS count FROM routine_checks " +
            "WHERE checkedAt BETWEEN :start AND :end " +
            "GROUP BY routineId"
    )
    fun observeCountsBetween(start: Long, end: Long): Flow<List<RoutineCheckCount>>

    @Query(
        "SELECT COUNT(*) FROM routine_checks " +
            "WHERE routineId = :routineId AND checkedAt BETWEEN :start AND :end"
    )
    suspend fun countForRange(routineId: Long, start: Long, end: Long): Int

    @Query("DELETE FROM routine_checks")
    suspend fun clearAll()
}
