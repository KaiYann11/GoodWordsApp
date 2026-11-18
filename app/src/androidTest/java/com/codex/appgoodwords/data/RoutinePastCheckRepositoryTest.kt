package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 지난날에 적는 체크와 되돌리기가 실제 DB에서 맞게 남는지 봅니다(사용자가 정함, 2026-11-18).
 *
 * 체크는 날짜 칸이 없고 시각(`checkedAt`)만 있습니다. 그날 안의 시각으로 적혀야 그날의 회수·연속 날수·달력에 잡힙니다.
 */
class RoutinePastCheckRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.repository
    private val database = container.database
    private val zone = ZoneId.of("Asia/Seoul")

    /** 2026-11-18 14:30(서울)을 지금으로 둡니다. 기기 시계와 상관없이 같은 결과가 나와야 합니다. */
    private val now = LocalDateTime.of(2026, 11, 18, 14, 30).atZone(zone).toInstant().toEpochMilli()
    private val today = LocalDate.of(2026, 11, 18)

    @Before
    fun setUp() = runBlocking { clear() }

    @After
    fun tearDown() = runBlocking { clear() }

    private suspend fun clear() {
        database.routineDao().clearAll()
        database.routineCheckDao().clearAll()
        database.routineMemoDao().clearAll()
        database.deletionDao().clearAll()
    }

    private suspend fun routineId(title: String): Long {
        repository.saveRoutine(RoutineDraft(title = title))
        return database.routineDao().getAll().single { it.title == title }.id
    }

    private fun dateOf(millis: Long, zoneId: ZoneId = zone): LocalDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    @Test
    fun aCheckForAPastDayLandsOnThatDayAndCountsThere() = runBlocking {
        val id = routineId("물 한 컵")
        val monday = today.minusDays(2)

        val first = repository.checkRoutineOn(id, monday, now, zone)!!
        val second = repository.checkRoutineOn(id, monday, now + 1_000, zone)!!

        assertEquals(monday, first.date)
        assertEquals(1, first.count)
        assertEquals(2, second.count)
        val checks = database.routineCheckDao().getAll()
        assertEquals(listOf(monday, monday), checks.map { dateOf(it.checkedAt) })
        // 몇 시에 했는지는 알 수 없어 그날의 지금 시각을 빌립니다.
        val firstAt = checks.single { it.syncId == first.syncId }.checkedAt
        assertEquals(LocalDateTime.of(2026, 11, 16, 14, 30), Instant.ofEpochMilli(firstAt).atZone(zone).toLocalDateTime())
        // 오늘 회수에는 들어가지 않습니다.
        val todayCheck = repository.checkRoutineOn(id, today, now, zone)!!
        assertEquals(1, todayCheck.count)
    }

    @Test
    fun aDayThatHasNotComeIsRefused() = runBlocking {
        val id = routineId("물 한 컵")

        val result = runCatching { repository.checkRoutineOn(id, today.plusDays(1), now, zone) }

        assertTrue(result.isFailure)
        assertTrue(database.routineCheckDao().getAll().isEmpty())
    }

    @Test
    fun undoRemovesOnlyThatCheckAndLeavesATombstone() = runBlocking {
        val id = routineId("물 한 컵")
        val wrong = repository.checkRoutineOn(id, today.minusDays(1), now, zone)!!
        val kept = repository.checkRoutineOn(id, today.minusDays(1), now + 1_000, zone)!!

        assertTrue(repository.undoRoutineCheck(wrong.syncId))

        assertEquals(listOf(kept.syncId), database.routineCheckDao().getAll().map { it.syncId })
        // 다른 기기와 서버에서도 지워지려면 삭제 표식이 있어야 합니다.
        val tombstone = database.deletionDao().getAll().single()
        assertEquals(wrong.syncId, tombstone.syncId)
        assertEquals(SyncEntityType.ROUTINE_CHECK, tombstone.entityType)
        // 두 번 눌러도 하나만 지웁니다.
        assertFalse(repository.undoRoutineCheck(wrong.syncId))
        assertEquals(1, database.routineCheckDao().getAll().size)
    }

    @Test
    fun aMemoWrittenWhileLookingAtAPastDayCountsOnThatDay() = runBlocking {
        val systemZone = ZoneId.systemDefault()
        val realToday = LocalDate.now(systemZone)
        val id = routineId("물 한 컵")

        repository.saveRoutineMemo(id, "늦게 적음", realToday.minusDays(3))

        assertEquals(listOf(realToday.minusDays(3)), database.routineCheckDao().getAll().map { dateOf(it.checkedAt, systemZone) })
        // 메모 자체는 쓴 지금의 것입니다.
        assertEquals(realToday, dateOf(database.routineMemoDao().getAll().single().createdAt, systemZone))
    }

    @Test
    fun notificationsAndWidgetStillWriteToday() = runBlocking {
        val systemZone = ZoneId.systemDefault()
        val id = routineId("물 한 컵")
        repository.checkRoutineOn(id, LocalDate.now(systemZone).minusDays(1))

        assertEquals(1, repository.markRoutineDone(id))

        val dates = database.routineCheckDao().getAll().map { dateOf(it.checkedAt, systemZone) }.sorted()
        assertEquals(listOf(LocalDate.now(systemZone).minusDays(1), LocalDate.now(systemZone)), dates)
    }
}
