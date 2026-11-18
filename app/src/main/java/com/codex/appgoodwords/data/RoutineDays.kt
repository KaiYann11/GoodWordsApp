package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 요일 알약이 가리키는 날과, 지난날에 적는 체크(사용자가 정함, 2026-11-18).
 *
 * 알약은 일~토 차례 그대로이고, 저마다 **그 요일이 마지막으로 있었던 날**을 가리킵니다 — 오늘이거나 앞 엿새 안입니다.
 * 오늘보다 뒤 요일(수요일이면 목·금·토)은 지난주 날입니다. 앞날을 가리키는 알약은 없으므로, 고른 날에는 늘 체크를
 * 적을 수 있습니다. 고른 날의 카드·거르개·진행은 그날 체크를 따르고, 수행 +1은 그날로 적힙니다.
 *
 * 지난 시각으로 적은 체크는 "바뀐 것만" 보내는 동기화([SyncDelta])에 잡히지 않습니다. 적을 때 다음 동기화를 통째로
 * 보내게 해야 서버에 갑니다([SettingsStore.requestFullPush]).
 */
object RoutineDays {
    /** 오늘을 빼고 거슬러 갈 수 있는 날 수. 일곱 알약이 오늘과 앞 엿새를 하나씩 맡습니다. */
    const val PAST_DAYS = 6L

    /** [day]가 마지막으로 있었던 날. 오늘이 [day]면 오늘입니다. */
    fun latest(day: DayOfWeek, today: LocalDate): LocalDate =
        today.minusDays(((today.dayOfWeek.value - day.value + 7) % 7).toLong())

    /**
     * [date]에 적을 체크의 시각. 오늘이면 지금 그대로입니다.
     *
     * 지난날이면 그날의 지금 시각을 빌립니다 — 실제로 몇 시에 했는지는 알 수 없습니다. 두 번 눌러도 서로 다른 시각이 되고,
     * 날짜는 그날을 벗어나지 않습니다.
     */
    fun checkTime(date: LocalDate, now: Long, zoneId: ZoneId): Long {
        val clock = Instant.ofEpochMilli(now).atZone(zoneId)
        if (clock.toLocalDate() == date) return now
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return date.atTime(clock.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli().coerceIn(start, end)
    }

    /** [from]~[to] 날마다 루틴별 체크 수. 체크가 없는 날은 담지 않습니다. */
    fun countsByDate(
        checks: List<RoutineCheckEntity>,
        from: LocalDate,
        to: LocalDate,
        zoneId: ZoneId
    ): Map<LocalDate, Map<Long, Int>> {
        if (to.isBefore(from)) return emptyMap()
        val start = from.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return checks
            .filter { it.checkedAt in start until end }
            .groupBy { Instant.ofEpochMilli(it.checkedAt).atZone(zoneId).toLocalDate() }
            .mapValues { (_, dayChecks) -> dayChecks.groupingBy { it.routineId }.eachCount() }
    }

    /** "16일(월)". 진행 카드·스낵바·메모 창이 같은 말로 그날을 부릅니다. */
    fun label(date: LocalDate): String = "${date.dayOfMonth}일(${RoutineSchedule.shortLabel(date.dayOfWeek)})"

    /** 오늘이면 "오늘", 아니면 [label]. */
    fun spoken(date: LocalDate, today: LocalDate): String = if (date == today) "오늘" else label(date)
}

/** 체크 한 번. [syncId]로 되돌리고, [count]는 그날 그 루틴을 몇 번 했는지입니다. */
data class RoutineCheckResult(
    val syncId: String,
    val date: LocalDate,
    val count: Int
)
