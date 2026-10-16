package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 요일 한 칸.
 *
 * **횟수가 아니라 날수를 셉니다.** 하루에 세 번 눌렀다고 그 월요일이 세 번이 되지는 않습니다.
 * 횟수로 세면 하루 몰아 한 요일이 꾸준히 밟은 요일을 덮어씁니다.
 */
data class RoutineWeekdayCount(
    val dayOfWeek: DayOfWeek,
    val days: Int
)

/**
 * 루틴 하나를 지금까지 어떻게 밟아 왔는지.
 *
 * **저장하지 않고 체크 기록에서 그때그때 셉니다**([RoutineCheckEntity]). 연속 날수·점수와 같은
 * 이유입니다 — 저장해 두면 기록을 고치거나 다른 기기와 합쳤을 때 숫자와 기록이 어긋납니다.
 * 체크는 처음부터 날짜와 함께 쌓여 왔으므로, 이 숫자들은 지난 기록까지 거슬러 그대로 나옵니다.
 */
data class RoutineStatsReport(
    /** 지금까지 수행한 횟수. 같은 날 여러 번 밟았으면 각각 셉니다. */
    val totalChecks: Int,
    /** 수행한 날수. 하루에 여러 번 밟아도 하루입니다. */
    val doneDays: Int,
    /** 오늘까지 이어 온 날수([DayStreak.current]). */
    val currentStreakDays: Int,
    /** 가장 길게 이어 온 날수. */
    val bestStreakDays: Int,
    /** 처음 밟은 날. 한 번도 없으면 null입니다. */
    val firstDoneDate: LocalDate?,
    /** 마지막으로 밟은 날. 한 번도 없으면 null입니다. */
    val lastDoneDate: LocalDate?,
    /** 일요일부터 토요일까지 일곱 칸. 달력(`RoutineCalendarCard`)과 차례가 같습니다. */
    val weekdays: List<RoutineWeekdayCount>,
    /**
     * 가장 많이 밟은 요일. 없으면 null입니다.
     *
     * 으뜸이 둘 이상이면 고르지 않습니다 — 월·화·수가 나란히 셋이면 그중 하나를 집어
     * "월요일에 가장 많이 한다"고 말하는 것은 사실이 아닙니다.
     */
    val topWeekday: DayOfWeek?,
    /** 밟은 날 전부. 최근 몇 주를 격자로 그릴 때 씁니다. */
    val doneDates: Set<LocalDate>
) {
    val isEmpty: Boolean
        get() = totalChecks == 0

    /** 요일 막대의 높이를 맞추는 기준. 아무것도 없으면 0입니다. */
    val busiestWeekdayDays: Int
        get() = weekdays.maxOfOrNull { it.days } ?: 0
}

/**
 * 루틴마다 수행 횟수·연속 날수·요일을 셉니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다([BookProgressCalculator]와 같은 자리).
 */
object RoutineStatsCalculator {

    /**
     * "가장 많이 한 요일"이라 부르려면 그 요일에 적어도 이만큼은 밟아야 합니다.
     *
     * 한 번뿐인 요일을 으뜸이라 부르면, 처음 밟은 날이 그대로 "내 요일"이 되어 버립니다.
     */
    const val TOP_WEEKDAY_MIN_DAYS = 2

    /** 일요일이 맨 앞입니다. 달력 격자(`RoutineCalendarCard`)와 차례를 맞춥니다. */
    val WEEK_ORDER: List<DayOfWeek> = listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY
    )

    fun build(
        routine: RoutineEntity,
        checks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): RoutineStatsReport = buildAll(listOf(routine), checks, today, zoneId).getValue(routine.id)

    /**
     * 목록 화면이 쓰는 자리. 루틴마다 따로 훑으면 루틴 수 × 체크 수만큼 걸립니다.
     *
     * 돌려주는 map에는 **루틴마다 한 칸씩 반드시 있습니다.** 한 번도 안 밟은 루틴이 빠지면
     * 화면이 그 자리에서 null을 다시 다뤄야 합니다.
     */
    fun buildAll(
        routines: List<RoutineEntity>,
        checks: List<RoutineCheckEntity>,
        today: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<Long, RoutineStatsReport> {
        val bySyncId = routines.filter { it.syncId.isNotBlank() }.associateBy { it.syncId }
        val byId = routines.associateBy { it.id }

        val datesByRoutine = mutableMapOf<Long, MutableList<LocalDate>>()
        for (check in checks) {
            // 기기 간 식별자는 syncId뿐입니다. 숫자 id는 기기마다 따로 증가해서, 그것으로 묶으면
            // 다른 기기에서 온 남의 루틴 기록이 섞입니다. 아직 한 번도 동기화하지 않은 기기에는
            // syncId가 비어 있는 옛 체크가 있을 수 있어, 그때만 숫자 id로 찾습니다.
            val routine = if (check.routineSyncId.isNotBlank()) {
                bySyncId[check.routineSyncId]
            } else {
                byId[check.routineId]
            } ?: continue
            datesByRoutine.getOrPut(routine.id) { mutableListOf() } += toDate(check.checkedAt, zoneId)
        }

        return routines.associate { routine ->
            routine.id to report(datesByRoutine[routine.id].orEmpty(), today)
        }
    }

    private fun report(dates: List<LocalDate>, today: LocalDate): RoutineStatsReport {
        val days = dates.toSet()
        val weekdayDays = days.groupingBy { it.dayOfWeek }.eachCount()
        val weekdays = WEEK_ORDER.map { day ->
            RoutineWeekdayCount(dayOfWeek = day, days = weekdayDays[day] ?: 0)
        }
        val most = weekdays.maxOfOrNull { it.days } ?: 0
        val leaders = weekdays.filter { it.days == most }

        return RoutineStatsReport(
            totalChecks = dates.size,
            doneDays = days.size,
            currentStreakDays = DayStreak.current(days, today),
            bestStreakDays = DayStreak.best(days),
            firstDoneDate = days.minOrNull(),
            lastDoneDate = days.maxOrNull(),
            weekdays = weekdays,
            topWeekday = leaders.singleOrNull()?.dayOfWeek?.takeIf { most >= TOP_WEEKDAY_MIN_DAYS },
            doneDates = days
        )
    }

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
