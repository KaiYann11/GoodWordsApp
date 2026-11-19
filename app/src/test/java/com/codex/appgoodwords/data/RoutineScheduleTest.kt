package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 루틴의 요일과 시각(사용자가 정함, 2026-11-17).
 *
 * 기본은 매일·상시입니다. 특히 **어느 날에도 보이지 않는 루틴이 생기지 않는지** 봅니다 — 요일이 하나도 없는 값은
 * 매일로 읽습니다. 그렇지 않으면 그 루틴은 고칠 수도 지울 수도 없게 숨습니다.
 */
class RoutineScheduleTest {
    @Test
    fun aRoutineIsEveryDayAndAnytimeUnlessToldOtherwise() {
        val routine = RoutineEntity(title = "물 한 컵")

        assertEquals(RoutineSchedule.EVERY_DAY, routine.weekdays)
        assertNull(routine.timeOfDay)
        DayOfWeek.values().forEach { assertTrue(it.name, RoutineSchedule.isOn(routine.weekdays, it)) }
    }

    @Test
    fun noDayAtAllReadsAsEveryDay() {
        assertEquals(RoutineSchedule.EVERY_DAY, RoutineSchedule.normalized(0))
        // 쓰지 않는 비트만 있어도 마찬가지입니다.
        assertEquals(RoutineSchedule.EVERY_DAY, RoutineSchedule.normalized(1 shl 9))
        assertTrue(RoutineSchedule.isOn(0, DayOfWeek.WEDNESDAY))
    }

    @Test
    fun chosenDaysAreTheOnlyDays() {
        val monWedFri = RoutineSchedule.maskOf(listOf(1, 3, 5))

        assertTrue(RoutineSchedule.isOn(monWedFri, DayOfWeek.MONDAY))
        assertFalse(RoutineSchedule.isOn(monWedFri, DayOfWeek.TUESDAY))
        assertTrue(RoutineSchedule.isOn(monWedFri, DayOfWeek.FRIDAY))
        assertFalse(RoutineSchedule.isOn(monWedFri, DayOfWeek.SUNDAY))
        // 2026-11-17은 화요일입니다.
        assertFalse(RoutineSchedule.isOn(RoutineEntity(title = "헬스장", weekdays = monWedFri), LocalDate.of(2026, 11, 17)))
        assertEquals(listOf(1, 3, 5), RoutineSchedule.isoDaysOf(monWedFri))
    }

    @Test
    fun turningOffTheLastDayLeavesNothingForTheSaveButtonToRefuse() {
        val onlySunday = RoutineSchedule.maskOf(listOf(7))

        assertEquals(0, RoutineSchedule.toggled(onlySunday, DayOfWeek.SUNDAY))
        assertEquals(RoutineSchedule.maskOf(listOf(1, 7)), RoutineSchedule.toggled(onlySunday, DayOfWeek.MONDAY))
    }

    @Test
    fun theLabelUsesNamesForTheUsualWeeksAndRunsFromSunday() {
        assertEquals("매일", RoutineSchedule.daysLabel(RoutineSchedule.EVERY_DAY))
        assertEquals("평일", RoutineSchedule.daysLabel(RoutineSchedule.WEEKDAYS))
        assertEquals("주말", RoutineSchedule.daysLabel(RoutineSchedule.WEEKEND))
        assertEquals("월·수·금", RoutineSchedule.daysLabel(RoutineSchedule.maskOf(listOf(1, 3, 5))))
        // 루틴 탭의 요일 알약처럼 월요일부터입니다(사용자가 정함, 2026-11-19 — 웹 routineScheduleLabel과 같게).
        assertEquals("월·일", RoutineSchedule.daysLabel(RoutineSchedule.maskOf(listOf(1, 7))))
        assertEquals("화·일", RoutineSchedule.daysLabel(RoutineSchedule.maskOf(listOf(7, 2))))
    }

    @Test
    fun theClockIsTwoDigitsAndAnytimeIsBlank() {
        assertEquals("07:05", RoutineSchedule.clockText(7 * 60 + 5))
        assertEquals("19:00", RoutineSchedule.clockText(19 * 60))
        assertEquals("", RoutineSchedule.clockText(null))
        assertEquals("", RoutineSchedule.clockText(24 * 60))
    }

    @Test
    fun onlyARealClockIsATime() {
        assertEquals(7 * 60 + 5, RoutineSchedule.parseClock("7:05"))
        assertEquals(23 * 60 + 59, RoutineSchedule.parseClock(" 23:59 "))
        assertNull(RoutineSchedule.parseClock(""))
        assertNull(RoutineSchedule.parseClock(null))
        assertNull(RoutineSchedule.parseClock("25:00"))
        assertNull(RoutineSchedule.parseClock("7:5"))
        assertNull(RoutineSchedule.parseClock("저녁"))
        assertNull(RoutineSchedule.normalizedTime(24 * 60))
        assertEquals(0, RoutineSchedule.normalizedTime(0))
    }
}
