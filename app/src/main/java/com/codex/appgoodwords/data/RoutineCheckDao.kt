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

    @Query("SELECT * FROM routine_checks")
    suspend fun getAll(): List<RoutineCheckEntity>

    /** [start] 뒤의 체크. 루틴 알람([RoutineAlarms])은 오늘 체크했는지만 보므로 지난 기록을 다 읽지 않습니다. */
    @Query("SELECT * FROM routine_checks WHERE checkedAt >= :start")
    suspend fun getSince(start: Long): List<RoutineCheckEntity>

    @Query("SELECT * FROM routine_checks WHERE checkedAt >= :start")
    fun observeSince(start: Long): Flow<List<RoutineCheckEntity>>

    @Query("SELECT * FROM routine_checks WHERE routineId = :routineId")
    suspend fun getByRoutineId(routineId: Long): List<RoutineCheckEntity>

    @Query("DELETE FROM routine_checks WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    /** 체크 하나를 지웁니다. 지운 줄 수(0이나 1). */
    @Query("DELETE FROM routine_checks WHERE syncId = :syncId")
    suspend fun deleteBySyncId(syncId: String): Int

    @Query("DELETE FROM routine_checks")
    suspend fun clearAll()
}
