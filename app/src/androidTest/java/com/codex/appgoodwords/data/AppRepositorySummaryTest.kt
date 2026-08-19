package com.codex.appgoodwords.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppRepositorySummaryTest {
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
            linkMetadataFetcher = LinkMetadataFetcher()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun buildTodaySummary_groupsOnlyTodaysShownAndConfirmedEvents() = runBlocking {
        val zoneId = ZoneId.systemDefault()
        val todayNoon = LocalDate.now(zoneId)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val yesterdayNoon = LocalDate.now(zoneId)
            .minusDays(1)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        database.exposureEventDao().insertAll(
            listOf(
                event(title = "Focus", type = ExposureEventType.SHOWN, occurredAt = todayNoon),
                event(title = "Focus", type = ExposureEventType.SHOWN, occurredAt = todayNoon + 1),
                event(title = "Focus", type = ExposureEventType.CONFIRMED, occurredAt = todayNoon + 2),
                event(title = "Discipline", type = ExposureEventType.CONFIRMED, occurredAt = todayNoon + 3),
                event(title = "Old", type = ExposureEventType.SHOWN, occurredAt = yesterdayNoon),
                event(title = "Old", type = ExposureEventType.CONFIRMED, occurredAt = yesterdayNoon + 1),
                event(title = "Surface only", type = ExposureEventType.SURFACED, occurredAt = todayNoon + 4)
            )
        )

        val summary = repository.buildTodaySummary()

        assertEquals(2, summary.totalShown)
        assertEquals(2, summary.totalConfirmed)
        assertEquals(listOf(DailySummaryLine(title = "Focus", count = 2)), summary.shownItems)
        assertEquals(
            mapOf("Focus" to 1, "Discipline" to 1),
            summary.confirmedItems.associate { it.title to it.count }
        )
        assertTrue(summary.shownItems.none { it.title == "Old" || it.title == "Surface only" })
        assertTrue(summary.confirmedItems.none { it.title == "Old" || it.title == "Surface only" })
    }

    @Test
    fun saveRoutineMemoAlsoMarksTheRoutineDone() = runBlocking {
        val routineId = database.routineDao().insert(RoutineEntity(title = "물 마시기"))

        val memoId = repository.saveRoutineMemo(routineId, "한 컵 마심")

        val routine = database.routineDao().getById(routineId)!!
        val memo = database.routineMemoDao().getAll().single()
        val check = database.routineCheckDao().getAll().single()
        assertEquals(memoId, memo.id)
        assertEquals(routineId, memo.routineId)
        assertEquals(routine.syncId, memo.routineSyncId)
        assertEquals(routineId, check.routineId)
        assertEquals(routine.syncId, check.routineSyncId)
        assertEquals(routine.title, check.routineTitle)
        assertEquals(1, repository.getTodayRoutineCheckCount(routineId))
    }

    @Test
    fun blankRoutineMemoDoesNotCreateAMemoOrCheck() = runBlocking {
        val routineId = database.routineDao().insert(RoutineEntity(title = "물 마시기"))

        val result = runCatching { repository.saveRoutineMemo(routineId, "   ") }

        assertTrue(result.isFailure)
        assertTrue(database.routineMemoDao().getAll().isEmpty())
        assertTrue(database.routineCheckDao().getAll().isEmpty())
    }

    @Test
    fun failedRoutineCheckRollsBackTheMemo() = runBlocking {
        val routineId = database.routineDao().insert(RoutineEntity(title = "물 마시기"))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_routine_check " +
                "BEFORE INSERT ON routine_checks " +
                "BEGIN SELECT RAISE(ABORT, 'forced failure'); END"
        )

        val result = runCatching { repository.saveRoutineMemo(routineId, "한 컵 마심") }

        assertTrue(result.isFailure)
        assertTrue(database.routineMemoDao().getAll().isEmpty())
        assertTrue(database.routineCheckDao().getAll().isEmpty())
    }

    private fun event(
        title: String,
        type: ExposureEventType,
        occurredAt: Long
    ): ExposureEventEntity {
        return ExposureEventEntity(
            contentItemId = title.hashCode().toLong(),
            contentTitle = title,
            contentType = ContentType.QUOTE,
            eventType = type,
            trigger = ExposureTrigger.MANUAL_REFRESH,
            occurredAt = occurredAt
        )
    }
}
