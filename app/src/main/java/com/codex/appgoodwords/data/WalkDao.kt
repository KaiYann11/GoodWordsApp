package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkDao {
    /** 최근 것이 위입니다. 걷는 중인 것도 담깁니다 — 화면이 지금 걷는 길을 그립니다. */
    @Query("SELECT * FROM walks ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WalkEntity>>

    @Query("SELECT * FROM walks ORDER BY startedAt DESC")
    suspend fun getAll(): List<WalkEntity>

    /** 다 걸은 것만. 동기화와 내보내기가 씁니다 — 걷는 중인 길은 아직 기록이 아닙니다. */
    @Query("SELECT * FROM walks WHERE endedAt > 0 ORDER BY startedAt DESC")
    suspend fun getFinished(): List<WalkEntity>

    @Query("SELECT * FROM walks WHERE endedAt = 0 ORDER BY startedAt DESC")
    suspend fun getUnfinished(): List<WalkEntity>

    @Query("SELECT * FROM walks WHERE id = :id")
    suspend fun getById(id: Long): WalkEntity?

    @Query("SELECT * FROM walks WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): WalkEntity?

    /**
     * 걷는 동안 길을 이어 적습니다. **번호가 아니라 syncId로 찾습니다** — 걷는 사이에 병합이 기기 데이터를 통째로 바꾸면
     * 번호가 다시 매겨지고([AppDataImporter.importSnapshot]), 번호로 덮어쓰면 엉뚱한 걷기를 덮습니다.
     */
    @Query("UPDATE walks SET path = :path, distanceMeters = :distanceMeters, updatedAt = :updatedAt WHERE syncId = :syncId AND endedAt = 0")
    suspend fun updateProgress(syncId: String, path: String, distanceMeters: Int, updatedAt: Long): Int

    @Query(
        "UPDATE walks SET endedAt = :endedAt, distanceMeters = :distanceMeters, steps = :steps, path = :path, " +
            "updatedAt = :updatedAt WHERE syncId = :syncId"
    )
    suspend fun finish(syncId: String, endedAt: Long, distanceMeters: Int, steps: Int, path: String, updatedAt: Long): Int

    @Query("UPDATE walks SET steps = :steps, updatedAt = :updatedAt WHERE syncId = :syncId")
    suspend fun updateSteps(syncId: String, steps: Int, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(walk: WalkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(walks: List<WalkEntity>)

    @Query("DELETE FROM walks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM walks WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM walks")
    suspend fun clearAll()
}
