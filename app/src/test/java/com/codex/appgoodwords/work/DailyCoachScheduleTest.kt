package com.codex.appgoodwords.work

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 오늘의 한마디를 배경에서 언제 깨울지.
 *
 * 앱을 켤 때마다 주기를 처음부터 세면, 앱을 자주 여는 날일수록 배경 한마디가 밀려 끝내 오지 않습니다.
 */
class DailyCoachScheduleTest {
    private val minute = 60_000L
    private val now = 1_760_000_000_000L

    @Test
    fun theGapIsCountedFromTheLastAsk() {
        // 30분 주기, 10분 전에 물었으면 20분 뒤에 깨웁니다.
        assertEquals(20 * minute, DailyCoachSchedule.delayMillis(30, lastAskedAt = now - 10 * minute, now = now))
    }

    @Test
    fun anOverdueAskWakesSoonButNotAtOnce() {
        // 이미 지났으면 곧 깨웁니다. 앱이 막 열리며 홈이 직접 묻는 것과 겹치지 않게 1분은 비웁니다.
        assertEquals(DailyCoachSchedule.MIN_DELAY_MILLIS, DailyCoachSchedule.delayMillis(30, now - 3 * 60 * minute, now))
    }

    @Test
    fun nothingAskedYetTodayWakesSoon() {
        assertEquals(DailyCoachSchedule.MIN_DELAY_MILLIS, DailyCoachSchedule.delayMillis(30, lastAskedAt = 0L, now = now))
    }

    @Test
    fun theWaitNeverExceedsOneGap() {
        // 시계가 뒤로 갔거나 저장된 시각이 앞날이어도 주기보다 오래 기다리지 않습니다.
        assertEquals(30 * minute, DailyCoachSchedule.delayMillis(30, lastAskedAt = now + 5 * 60 * minute, now = now))
    }

    @Test
    fun afterAskingTheNextAlarmIsOneGapAway() {
        // 방금 물었으면 한 주기 뒤입니다.
        assertEquals(90 * minute, DailyCoachSchedule.nextDelayMillis(90, lastAskedAt = now, now = now))
    }

    @Test
    fun anAlarmThatCameEarlyWaitsForTheRestOfTheGap() {
        // 홈이 20분 전에 물었으면 그 주기가 찰 때까지만 기다립니다. 처음부터 다시 세면 한마디가 밀립니다.
        assertEquals(70 * minute, DailyCoachSchedule.nextDelayMillis(90, lastAskedAt = now - 20 * minute, now = now))
    }

    @Test
    fun whenNothingWasAskedTheNextLookIsOneGapAwayNotAMinute() {
        // 오늘 한 것이 없어 묻지 않았으면 지금부터 한 주기 뒤에 다시 봅니다. 1분마다 깨어 같은 것을 보지 않습니다.
        assertEquals(90 * minute, DailyCoachSchedule.nextDelayMillis(90, lastAskedAt = 0L, now = now))
        assertEquals(90 * minute, DailyCoachSchedule.nextDelayMillis(90, lastAskedAt = now - 3 * 60 * minute, now = now))
    }

    @Test
    fun aUsedUpDayWaitsUntilMidnight() {
        val zone = ZoneId.of("Asia/Seoul")
        val evening = LocalDateTime.of(2026, 10, 28, 21, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(150 * minute, DailyCoachSchedule.untilTomorrowMillis(evening, zone))
        // 자정 바로 앞이어도 1분은 비웁니다.
        val lastSecond = LocalDateTime.of(2026, 10, 28, 23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        assertEquals(DailyCoachSchedule.MIN_DELAY_MILLIS, DailyCoachSchedule.untilTomorrowMillis(lastSecond, zone))
    }

    @Test
    fun theCardSaysWhenTheNextWordComes() {
        assertEquals(now + 90 * minute, DailyCoachSchedule.nextAskAt(90, limit = 5, askCount = 2, lastAskedAt = now))
        // 오늘 아직 묻지 않았거나, 하루 몫을 다 썼거나, 저절로는 묻지 않게 했으면 말하지 않습니다.
        assertNull(DailyCoachSchedule.nextAskAt(90, limit = 5, askCount = 0, lastAskedAt = 0L))
        assertNull(DailyCoachSchedule.nextAskAt(90, limit = 5, askCount = 5, lastAskedAt = now))
        assertNull(DailyCoachSchedule.nextAskAt(90, limit = 0, askCount = 0, lastAskedAt = now))
    }
}
