package com.codex.appgoodwords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.AppGoodWordsApplication
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 루틴의 요일·시각이 실제 DB에서 지켜지는지 봅니다(사용자가 정함, 2026-11-17).
 *
 * 저장은 고치기 창이 보낸 것으로 루틴을 통째로 새로 적습니다(`AppRepository.saveRoutine`). 고치기 창이 요일·시각을 빠뜨리면
 * 이름만 고쳐도 매일·상시로 돌아갑니다 — 오류 없이요.
 */
class RoutineScheduleRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val container = (context as AppGoodWordsApplication).container
    private val repository = container.repository
    private val database = container.database

    /** 2026-11-16은 월요일, 2026-11-22는 일요일입니다. */
    private val monday = LocalDate.of(2026, 11, 16)
    private val sunday = LocalDate.of(2026, 11, 22)

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

    @Test
    fun editingKeepsTheDaysAndTimeAndANewRoutineIsEveryDayAndAnytime() = runBlocking {
        val monWedFri = RoutineSchedule.maskOf(listOf(1, 3, 5))
        repository.saveRoutine(RoutineDraft(title = "헬스장", weekdays = monWedFri, timeOfDay = 19 * 60))
        val saved = database.routineDao().getAll().single()
        assertEquals(monWedFri, saved.weekdays)
        assertEquals(19 * 60, saved.timeOfDay)

        // 이름만 고쳐도 요일·시각은 그대로입니다.
        repository.saveRoutine(RoutineDraft.fromRoutine(saved).copy(title = "헬스장 가기"))
        val renamed = database.routineDao().getById(saved.id)!!
        assertEquals("헬스장 가기", renamed.title)
        assertEquals(monWedFri, renamed.weekdays)
        assertEquals(19 * 60, renamed.timeOfDay)

        // 상시로 바꾸면 시각이 빠집니다.
        repository.saveRoutine(RoutineDraft.fromRoutine(renamed).copy(timeOfDay = null))
        assertNull(database.routineDao().getById(saved.id)!!.timeOfDay)

        // 새 루틴은 매일·상시입니다(사용자가 정한 기본값).
        repository.saveRoutine(RoutineDraft(title = "물 한 컵"))
        val fresh = database.routineDao().getAll().single { it.title == "물 한 컵" }
        assertEquals(RoutineSchedule.EVERY_DAY, fresh.weekdays)
        assertNull(fresh.timeOfDay)
    }

    @Test
    fun aRoutineWithNoDayOrAnImpossibleTimeIsSavedAsEveryDayAndAnytime() = runBlocking {
        // 어느 날에도 보이지 않는 루틴은 고칠 수도 지울 수도 없게 숨습니다. 창이 막지만 저장도 한 번 더 막습니다.
        repository.saveRoutine(RoutineDraft(title = "숨은 루틴", weekdays = 0, timeOfDay = 30 * 60))
        val saved = database.routineDao().getAll().single()

        assertEquals(RoutineSchedule.EVERY_DAY, saved.weekdays)
        assertNull(saved.timeOfDay)
    }

    @Test
    fun thePeriodicReminderOnlyPicksARoutineForToday() = runBlocking {
        database.routineDao().insertAll(
            listOf(
                RoutineEntity(syncId = "weekday", title = "출근 준비", weekdays = RoutineSchedule.WEEKDAYS),
                RoutineEntity(syncId = "muted", title = "알림 끈 루틴", reminderEnabled = false)
            )
        )

        // 일요일에는 평일 루틴을 알리지 않습니다. 알림을 끈 루틴도 그대로 빠집니다.
        assertNull(repository.getRandomReminderRoutine(sunday))
        assertEquals("출근 준비", repository.getRandomReminderRoutine(monday)?.title)

        // 요일이 0으로 남은 값(옛 기기·손댄 파일)은 매일로 봅니다.
        database.routineDao().clearAll()
        database.routineDao().insert(RoutineEntity(syncId = "zero", title = "요일 없는 루틴", weekdays = 0))
        assertEquals("요일 없는 루틴", repository.getRandomReminderRoutine(sunday)?.title)
    }

    @Test
    fun mergingKeepsTheKeptRoutinesDaysAndTime() = runBlocking {
        val monWedFri = RoutineSchedule.maskOf(listOf(1, 3, 5))
        database.routineDao().insertAll(
            listOf(
                RoutineEntity(id = 1, syncId = "keep", title = "헬스장", weekdays = monWedFri, timeOfDay = 19 * 60, orderIndex = 0),
                RoutineEntity(id = 2, syncId = "gone", title = "운동", orderIndex = 1)
            )
        )

        repository.mergeRoutines(keepId = 1, mergeId = 2, title = "헬스장", note = "")

        val kept = database.routineDao().getAll().single()
        assertEquals(monWedFri, kept.weekdays)
        assertEquals(19 * 60, kept.timeOfDay)
    }
}
