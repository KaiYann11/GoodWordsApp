package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 오늘 루틴을 두고 건네는 한 줄.
 *
 * **AI를 부르지 않습니다.** 이미 있는 기록에서 세는 것이라 값이 들지 않고, 연결이 없어도
 * 뜹니다. 오늘의 한마디([DailyCoachTip])가 무거운 쪽이라면 이것은 가벼운 쪽입니다 —
 * 하루에 몇 번이든 다시 셈해도 되는 말입니다.
 */
data class RoutineNudge(
    val kind: Kind,
    /** 짚은 루틴. 다 밟은 날에는 0입니다. */
    val routineId: Long,
    val title: String,
    /** 그 루틴이 이어 온 날수. [Kind.STREAK]일 때만 뜻이 있습니다. */
    val streakDays: Int,
    val message: String
) {
    enum class Kind {
        /** 이어 오던 것이 오늘 아직입니다. 지킬 것이 있는 쪽을 먼저 짚습니다. */
        STREAK,
        /** 차례상 다음 것입니다. */
        NEXT,
        /** 오늘 다 밟았습니다. */
        ALL_DONE
    }

    /** 밟으러 갈 자리가 있는지. 다 밟은 날에는 데려갈 곳이 없습니다. */
    val hasPlaceToGo: Boolean
        get() = kind != Kind.ALL_DONE
}

/**
 * 오늘 무엇을 권할지 고릅니다.
 *
 * **못 한 것을 세지 않습니다.** "3개 남았습니다"라고 적지 않고 **다음 하나만** 말합니다
 * ([DailyProgress.message]와 같은 규칙) — 남은 것을 한꺼번에 보여 주면 할 일 목록처럼 읽혀
 * 오히려 손이 무거워집니다. 달성률도 매기지 않습니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object RoutineNudgeCalculator {
    /**
     * "이어 오는 중"이라고 말하려면 이만큼은 이어져야 합니다.
     *
     * 어제 한 번 밟은 것을 두고 "이어지고 있다"고 하면, 끊길 것이 없는데 지킬 것이 있는 척이
     * 됩니다([RoutineStatsCalculator.TOP_WEEKDAY_MIN_DAYS]와 같은 셈).
     */
    const val STREAK_MIN_DAYS = 2

    /**
     * 오늘 아직 안 밟은 루틴. 사용자가 정한 차례대로입니다.
     *
     * **체크는 `routineSyncId`로 루틴을 찾습니다.** 숫자 id로 묶으면 다른 기기에서 온 남의
     * 기록이 섞입니다. syncId가 비어 있는 옛 체크만 숫자 id로 찾습니다
     * ([RoutineStatsCalculator]와 같은 규칙). 오늘의 한마디가 AI에게 보내는 목록도
     * 이 함수를 씁니다 — 두 곳에서 따로 추리면 화면과 물음이 서로 다른 루틴을 가리킵니다.
     */
    fun restOfToday(
        routines: List<RoutineEntity>,
        todayChecks: List<RoutineCheckEntity>
    ): List<RoutineEntity> {
        val doneSyncIds = todayChecks.mapNotNullTo(mutableSetOf()) { it.routineSyncId.takeIf(String::isNotBlank) }
        // **syncId가 있는 체크는 숫자 id를 보지 않습니다.** 둘 다 보면, 다른 기기에서 온
        // 체크의 숫자 id가 이쪽의 엉뚱한 루틴과 겹쳐 밟지도 않은 것이 밟은 것으로 빠집니다.
        val doneIds = todayChecks
            .filter { it.routineSyncId.isBlank() }
            .mapTo(mutableSetOf()) { it.routineId }
        return RoutineOrder.sorted(routines).filterNot { it.syncId in doneSyncIds || it.id in doneIds }
    }

    /**
     * 오늘 건넬 한 줄. 루틴이 하나도 없으면 null입니다.
     *
     * 적어 둔 루틴이 없는 사람에게 "루틴을 만들어 보세요"라고 하지 않습니다. 그 권유는
     * 루틴 탭이 이미 하고 있고, 홈에서 또 하면 빈 카드가 매일 자리를 차지합니다.
     */
    fun build(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): RoutineNudge? {
        if (routines.isEmpty()) return null

        val todayChecks = checks.filter { toDate(it.checkedAt, zoneId) == today }
        val rest = restOfToday(routines, todayChecks)
        if (rest.isEmpty()) {
            return RoutineNudge(
                kind = RoutineNudge.Kind.ALL_DONE,
                routineId = 0,
                title = "",
                streakDays = 0,
                message = "오늘 루틴을 모두 밟았습니다."
            )
        }

        // 이어 오던 것이 오늘 아직이면 그쪽을 먼저 짚습니다. 새로 시작하는 것보다
        // 이어 온 것을 지키는 쪽이 훨씬 큰 힘이 됩니다(`DailyProgress.message`와 같은 뜻).
        val stats = RoutineStatsCalculator.buildAll(routines, checks, today, zoneId)
        val keeping = rest
            .mapNotNull { routine -> stats[routine.id]?.currentStreakDays?.let { routine to it } }
            .filter { (_, streak) -> streak >= STREAK_MIN_DAYS }
            .maxByOrNull { (_, streak) -> streak }

        if (keeping != null) {
            val (routine, streak) = keeping
            return RoutineNudge(
                kind = RoutineNudge.Kind.STREAK,
                routineId = routine.id,
                title = routine.title,
                streakDays = streak,
                message = "${routine.title}이(가) ${streak}일째 이어지고 있어요. 오늘 한 번이면 이어집니다."
            )
        }

        val next = rest.first()
        return RoutineNudge(
            kind = RoutineNudge.Kind.NEXT,
            routineId = next.id,
            title = next.title,
            streakDays = 0,
            message = "다음 차례는 ${next.title}이에요."
        )
    }

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
