package com.codex.appgoodwords.work

import org.junit.Assert.assertEquals
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
}
