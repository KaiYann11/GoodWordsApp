package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GrowthReportDao {
    /** 새로 받은 것이 위입니다. 지난 글은 아래로 밀립니다. */
    @Query("SELECT * FROM growth_reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GrowthReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: GrowthReportEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<GrowthReportEntity>)

    @Query("SELECT * FROM growth_reports ORDER BY createdAt DESC")
    suspend fun getAll(): List<GrowthReportEntity>

    @Query("SELECT * FROM growth_reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GrowthReportEntity?

    /** 주기가 돌아왔는지 볼 때 씁니다. 마지막으로 받은 시각만 있으면 됩니다. */
    @Query("SELECT * FROM growth_reports ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): GrowthReportEntity?

    @Query("DELETE FROM growth_reports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM growth_reports WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM growth_reports")
    suspend fun clearAll()
}
