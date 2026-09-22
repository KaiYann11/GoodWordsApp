package com.codex.appgoodwords.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookReadingLogDao {
    /** 최근에 읽은 것이 위입니다. */
    @Query("SELECT * FROM book_reading_logs ORDER BY readDate DESC")
    fun observeAll(): Flow<List<BookReadingLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BookReadingLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<BookReadingLogEntity>)

    @Query("SELECT * FROM book_reading_logs ORDER BY readDate DESC")
    suspend fun getAll(): List<BookReadingLogEntity>

    /** 하루에 책 하나당 하나라서, 그날 것이 있으면 고칩니다. 새로 넣으면 그날 읽은 쪽수가 겹쳐 세집니다. */
    @Query("SELECT * FROM book_reading_logs WHERE bookId = :bookId AND readDate = :readDate ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getByBookAndDate(bookId: Long, readDate: String): BookReadingLogEntity?

    @Query("DELETE FROM book_reading_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM book_reading_logs WHERE syncId IN (:syncIds)")
    suspend fun deleteBySyncIds(syncIds: List<String>)

    @Query("DELETE FROM book_reading_logs")
    suspend fun clearAll()
}
