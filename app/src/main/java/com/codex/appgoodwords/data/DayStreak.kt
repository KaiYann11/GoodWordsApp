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
    fun current(days: Set<LocalDate>, today: LocalDate): Int {
        if (days.isEmpty()) return 0

        var cursor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        while (cursor in days) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** 지금까지 가장 길게 이어 온 날수. */
    fun best(days: Set<LocalDate>): Int {
        if (days.isEmpty()) return 0

        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (index in 1 until sorted.size) {
            run = if (sorted[index - 1].plusDays(1) == sorted[index]) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }
}
