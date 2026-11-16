package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 이어 온 날수를 세는 한 곳.
 *
 * 이 앱에는 "며칠째 이어 오고 있나"를 말하는 자리가 여럿입니다 — 하루의 걸음
 * ([DailyLoopCalculator]), 돌아보기 통계([StatsCalculator]), 루틴 하나하나
 * ([RoutineStatsCalculator]). 세는 규칙은 모두 같아야 합니다. 화면마다 따로 세면
 * "3일 연속"이라 적힌 카드를 보며 "2일째"라는 숫자를 함께 보게 됩니다.
 *
 * **날수는 저장하지 않고 기록에서 그때그때 셉니다.** 저장해 두면 지난 기록을 고쳤을 때
 * 둘이 어긋나고, 기준을 바꿨을 때 옛 기준으로 쌓인 숫자가 섞입니다.
 */
object DayStreak {

    /**
     * 오늘까지 이어 온 날수.
     *
     * **오늘이 아직 비어 있어도 어제까지 이어졌으면 그 날수를 그대로 보여 줍니다.**
     * 아침에 앱을 열었을 때 0일로 보이면 어제까지 쌓은 것이 사라진 것처럼 느껴집니다.
     * 지킬 것이 있어야 오늘도 하게 됩니다. 하루가 통째로 비어야 끊긴 것으로 봅니다.
     */
    fun current(days: Set<LocalDate>, today: LocalDate): Int = current(days, today, isActive = { true })

    /**
     * 하는 날만 세는 이어 온 날수 — 요일을 정한 루틴(사용자가 정함, 2026-11-17).
     *
     * **쉬는 날은 끊지도 더하지도 않습니다.** 월·수·금 루틴을 월·수·금 밟았으면 3입니다. 날마다 세면 화요일마다 끊겨
     * 늘 1이 됩니다. 쉬는 날에 밟은 것은 날수에 넣지 않습니다 — 넣으면 같은 한 주가 요일을 바꾼 것만으로 다른 숫자가 됩니다.
     * 오늘이 하는 날인데 아직 비어 있으면 앞의 하는 날까지로 셉니다(위와 같은 규칙).
     */
    fun current(days: Set<LocalDate>, today: LocalDate, isActive: (LocalDate) -> Boolean): Int {
        if (days.isEmpty()) return 0

        var cursor = if (isActive(today) && today in days) today else previousActive(today, isActive) ?: return 0
        var streak = 0
        while (cursor in days) {
            streak += 1
            cursor = previousActive(cursor, isActive) ?: break
        }
        return streak
    }

    /** 지금까지 가장 길게 이어 온 날수. */
    fun best(days: Set<LocalDate>): Int = best(days, isActive = { true })

    /** 하는 날만 세는 가장 긴 날수. 쉬는 날은 건너뜁니다([current]와 같은 규칙). */
    fun best(days: Set<LocalDate>, isActive: (LocalDate) -> Boolean): Int {
        val sorted = days.filter(isActive).sorted()
        if (sorted.isEmpty()) return 0

        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (nextActive(sorted[index - 1], isActive) == sorted[index]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    /** 일주일 안에 하는 날이 없으면 null — 요일 규칙은 일주일마다 되풀이되므로 그보다 멀리 볼 것이 없습니다. */
    private fun previousActive(date: LocalDate, isActive: (LocalDate) -> Boolean): LocalDate? =
        (1L..7L).map { date.minusDays(it) }.firstOrNull(isActive)

    private fun nextActive(date: LocalDate, isActive: (LocalDate) -> Boolean): LocalDate? =
        (1L..7L).map { date.plusDays(it) }.firstOrNull(isActive)
}
