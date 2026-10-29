package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 누름과 삭제가 실제 Room에서도 흐름을 지키는지 봅니다. 사용자 DB 대신 메모리 DB만 씁니다. */
class MoodLogRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private val today = LocalDate.of(2026, 10, 29)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao(),
            moodLogDao = database.moodLogDao()
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun eachTapAppendsExceptTheImmediateRepeat() = runBlocking {
        val first = repository.saveMoodLog(today, DiaryMood.GOOD, now = 10_000L)!!
        val repeated = repository.saveMoodLog(today, DiaryMood.GOOD, now = 12_999L)!!
        val later = repository.saveMoodLog(today, DiaryMood.GOOD, now = 13_000L)!!
        val changed = repository.saveMoodLog(today, DiaryMood.TIRED, now = 13_001L)!!
        val returned = repository.saveMoodLog(today, DiaryMood.GOOD, now = 13_002L)!!

        assertEquals(first, repeated)
        assertNotEquals(first.syncId, later.syncId)
        val stored = DayMood.logsOn(database.moodLogDao().getAll(), today)
        assertEquals(listOf(first, later, changed, returned), stored)
        assertTrue(stored.all { it.createdAt == it.updatedAt })
    }

    @Test
    fun concurrentRepeatTapsAreCheckedAndSavedInOneTransaction() = runBlocking {
        val results = (1..8).map {
            async(Dispatchers.Default) { repository.saveMoodLog(today, DiaryMood.GOOD, now = 10_000L)!! }
        }.awaitAll()

        assertEquals(1, database.moodLogDao().getAll().size)
        assertEquals(1, results.map { it.syncId }.distinct().size)
    }

    @Test
    fun deletingOneTapUsesItsSyncIdAndLeavesATombstone() = runBlocking {
        val first = repository.saveMoodLog(today, DiaryMood.GOOD, now = 10_000L)!!
        val last = repository.saveMoodLog(today, DiaryMood.TIRED, now = 20_000L)!!
        // 가져오기로 기기 안 번호가 바뀐 뒤에도 화면에서 고른 기록만 지워야 합니다.
        database.moodLogDao().clearAll()
        database.moodLogDao().insertAll(listOf(first.copy(id = 91L), last.copy(id = 92L)))
        repository.clearMoodLog(first.syncId)
        repository.clearMoodLog(first.syncId)

        assertEquals(listOf(last.syncId), database.moodLogDao().getAll().map { it.syncId })
        val deletion = database.deletionDao().getAll().single()
        assertEquals(first.syncId, deletion.syncId)
        assertEquals(SyncEntityType.MOOD_LOG, deletion.entityType)
        assertTrue(deletion.deletedAt >= first.updatedAt)
    }

    @Test
    fun aRepeatOnANewDayIsANewRecord() = runBlocking {
        repository.saveMoodLog(today, DiaryMood.GOOD, now = 10_000L)
        repository.saveMoodLog(today.plusDays(1), DiaryMood.GOOD, now = 10_001L)

        assertEquals(2, database.moodLogDao().getAll().size)
    }
}
