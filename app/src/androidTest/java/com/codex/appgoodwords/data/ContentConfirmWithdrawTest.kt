package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 오늘 적은 읽음을 거둘 때 삭제 표식이 남는지 봅니다.
 *
 * 읽음은 덧붙기만 하는 기록이라, 표식 없이 지우면 이미 서버에 올라간 읽음이 서버와 웹에 그대로 남습니다.
 * 1.24.1까지 읽음을 다시 눌러 거둘 때가 그랬습니다. 홈 글귀를 민 것의 되돌리기도 같은 길로 거둡니다.
 */
class ContentConfirmWithdrawTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(
            database = database,
            contentItemDao = database.contentItemDao(),
            exposureEventDao = database.exposureEventDao(),
            routineDao = database.routineDao(),
            routineCheckDao = database.routineCheckDao(),
            routineMemoDao = database.routineMemoDao(),
            contentMemoDao = database.contentMemoDao(),
            linkMetadataFetcher = LinkMetadataFetcher(),
            deletionDao = database.deletionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun quote(body: String): Long =
        database.contentItemDao().insert(ContentItemEntity(type = ContentType.QUOTE, title = "", body = body))

    private suspend fun confirmedEvents(): List<ExposureEventEntity> =
        database.exposureEventDao().getAll().filter { it.eventType == ExposureEventType.CONFIRMED }

    private suspend fun eventTombstones(): Set<String> = database.deletionDao().getAll()
        .filter { it.entityType == SyncEntityType.EXPOSURE_EVENT }
        .map { it.syncId }
        .toSet()

    @Test
    fun takingBackTodaysReadLeavesATombstone() = runBlocking {
        val id = quote("작은 일을 꾸준히")
        assertTrue(repository.markContentConfirmed(id, ExposureTrigger.DETAIL_CHECK))
        val read = confirmedEvents().single()

        assertTrue(repository.withdrawTodayConfirmed(id))

        assertTrue(confirmedEvents().isEmpty())
        assertEquals(setOf(read.syncId), eventTombstones())
        assertTrue(repository.getTodayConfirmedIds().isEmpty())
    }

    @Test
    fun theReadButtonTakingItBackLeavesATombstoneToo() = runBlocking {
        val id = quote("작은 일을 꾸준히")
        assertTrue(repository.toggleContentConfirmed(id, ExposureTrigger.DETAIL_CHECK))
        val read = confirmedEvents().single()

        assertFalse(repository.toggleContentConfirmed(id, ExposureTrigger.DETAIL_CHECK))

        assertTrue(confirmedEvents().isEmpty())
        assertEquals(setOf(read.syncId), eventTombstones())
    }

    @Test
    fun nothingToTakeBackChangesNothing() = runBlocking {
        val id = quote("작은 일을 꾸준히")

        assertFalse(repository.withdrawTodayConfirmed(id))

        assertTrue(eventTombstones().isEmpty())
    }

    @Test
    fun onlyTodaysReadOfThatQuoteIsTakenBack() = runBlocking {
        val id = quote("작은 일을 꾸준히")
        val other = quote("오늘 할 일을 오늘")
        repository.markContentConfirmed(id, ExposureTrigger.DETAIL_CHECK)
        repository.markContentConfirmed(other, ExposureTrigger.DETAIL_CHECK)
        // 어제 읽은 것은 지난 기록입니다. 오늘 읽음을 거둔다고 지워지면 안 됩니다.
        val yesterday = ExposureEventEntity(
            contentItemId = id,
            contentTitle = "작은 일을 꾸준히",
            contentType = ContentType.QUOTE,
            eventType = ExposureEventType.CONFIRMED,
            trigger = ExposureTrigger.DETAIL_CHECK,
            occurredAt = repository.todayRangeMillis().first - 60_000L
        )
        database.exposureEventDao().insert(yesterday)

        assertTrue(repository.withdrawTodayConfirmed(id))

        assertEquals(setOf(other), repository.getTodayConfirmedIds())
        val left = confirmedEvents()
        assertEquals(listOf(yesterday.syncId), left.filter { it.contentItemId == id }.map { it.syncId })
        assertEquals(1, left.count { it.contentItemId == other })
        assertEquals(1, eventTombstones().size)
    }
}
