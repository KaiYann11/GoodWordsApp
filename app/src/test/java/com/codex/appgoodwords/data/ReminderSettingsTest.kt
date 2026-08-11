package com.codex.appgoodwords.data

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 알림을 보낼 시간대인지 판단합니다.
 *
 * 배경 작업은 창 밖이면 아무 말 없이 돌아가므로, 이 판단이 틀리면
 * 알림이 안 오는 이유를 사용자도 로그도 알려 주지 않습니다.
 */
class ReminderSettingsTest {
    @Test
    fun insideAPlainWindow() {
        val settings = window(start = 9 to 0, end = 22 to 0)

        assertTrue(settings.isWithinReminderWindow(LocalTime.of(9, 0)))
        assertTrue(settings.isWithinReminderWindow(LocalTime.of(15, 30)))
        assertTrue(settings.isWithinReminderWindow(LocalTime.of(22, 0)))
    }

    @Test
    fun outsideAPlainWindow() {
        val settings = window(start = 9 to 0, end = 22 to 0)

        assertFalse(settings.isWithinReminderWindow(LocalTime.of(8, 59)))
        assertFalse(settings.isWithinReminderWindow(LocalTime.of(22, 1)))
        assertFalse("새벽에 울리면 안 됩니다.", settings.isWithinReminderWindow(LocalTime.of(3, 0)))
    }

    @Test
    fun aWindowCrossingMidnightCoversBothSides() {
        val settings = window(start = 22 to 0, end = 6 to 0)

        assertTrue(settings.isWithinReminderWindow(LocalTime.of(23, 30)))
        assertTrue(settings.isWithinReminderWindow(LocalTime.of(2, 0)))
        assertFalse(settings.isWithinReminderWindow(LocalTime.of(12, 0)))
    }

    @Test
    fun theSameStartAndEndMeansAllDay() {
        val settings = window(start = 9 to 0, end = 9 to 0)

        assertTrue(settings.isWithinReminderWindow(LocalTime.of(3, 0)))
        assertTrue(settings.isWithinReminderWindow(LocalTime.of(15, 0)))
    }

    @Test
    fun theIntervalNeverGoesBelowWhatWorkManagerAccepts() {
        assertEquals(15, ReminderSettings(intervalMinutes = 1).effectiveIntervalMinutes)
        assertEquals(15, ReminderSettings(intervalMinutes = 0).effectiveIntervalMinutes)
        assertEquals(360, ReminderSettings(intervalMinutes = 360).effectiveIntervalMinutes)
    }

    private fun window(
        start: Pair<Int, Int>,
        end: Pair<Int, Int>
    ) = ReminderSettings(
        preferredHour = start.first,
        preferredMinute = start.second,
        repeatEndHour = end.first,
        repeatEndMinute = end.second
    )
}
