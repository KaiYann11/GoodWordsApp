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
