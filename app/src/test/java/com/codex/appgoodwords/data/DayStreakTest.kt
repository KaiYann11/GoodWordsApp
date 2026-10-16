package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 이어 온 날수를 세는 한 곳.
 *
 * 하루의 걸음·돌아보기 통계·루틴이 모두 이 규칙을 씁니다. 여기가 흔들리면 세 화면이
 * 같은 날을 두고 서로 다른 날수를 말하게 됩니다.
 */
class DayStreakTest {
    private val today = LocalDate.of(2026, 10, 16)

    private fun days(vararg dates: String) = dates.map(LocalDate::parse).toSet()

    @Test fun nothingYetIsZeroNotOne() {
        assertEquals(0, DayStreak.current(emptySet(), today))
        assertEquals(0, DayStreak.best(emptySet()))
    }

    @Test fun aMorningWithNothingDoneKeepsYesterdaysRun() {
        // 아침에 열었을 때 0일로 보이면 어제까지 쌓은 것이 사라진 것처럼 느껴집니다.
        assertEquals(3, DayStreak.current(days("2026-10-13", "2026-10-14", "2026-10-15"), today))
    }

    @Test fun oneWholeEmptyDayEndsTheRun() {
        // 어제가 비어 있으면 끊긴 것입니다. 그제까지 이어졌어도 세지 않습니다.
        assertEquals(0, DayStreak.current(days("2026-10-13", "2026-10-14"), today))
    }

    @Test fun theBestRunSurvivesTheGap() {
        val dates = days("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-16")

        assertEquals(1, DayStreak.current(dates, today))
        assertEquals(4, DayStreak.best(dates))
    }
}
