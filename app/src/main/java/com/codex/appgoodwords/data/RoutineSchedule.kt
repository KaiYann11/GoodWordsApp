package com.codex.appgoodwords.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/**
 * 루틴을 어느 요일에, 언제 하는지(사용자가 정함, 2026-11-17).
 *
 * - **요일**은 일곱 칸 비트입니다([RoutineEntity.weekdays]). 월요일이 맨 아래 비트입니다(`DayOfWeek.value`와 같은 차례).
 *   기본은 매일이고, 요일이 생기기 전의 루틴도 매일로 봅니다. 하나도 고르지 않은 값은 매일로 읽습니다 — 어느 날에도
 *   보이지 않는 루틴은 지울 수도 고칠 수도 없게 숨어 버립니다.
 * - **시각**은 자정부터의 분입니다([RoutineEntity.timeOfDay]). 없으면 상시 — 하루 중 아무 때나 하는 루틴입니다.
 *
 * 동기화 JSON에서는 요일을 ISO 번호 목록(`[1,3,5]`, 월=1 … 일=7)으로, 시각을 `HH:mm`(상시는 빈 글자)으로 씁니다.
 * 서버 `normalizeRoutine`·웹이 같은 꼴을 읽습니다.
 */
object RoutineSchedule {
    /** 일곱 요일 모두. */
    const val EVERY_DAY = 0b111_1111

    /** 월~금. */
    val WEEKDAYS: Int = maskOf((1..5))

    /** 토·일. */
    val WEEKEND: Int = maskOf(listOf(6, 7))

    /** 하루의 분. */
    private const val MINUTES_PER_DAY = 24 * 60

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    /** 고른 요일이 없거나 쓰지 않는 비트만 있으면 매일입니다. */
    fun normalized(mask: Int): Int = (mask and EVERY_DAY).takeIf { it != 0 } ?: EVERY_DAY

    fun isOn(mask: Int, day: DayOfWeek): Boolean = normalized(mask) and bit(day) != 0

    /** 그 루틴이 [date]에 하는 것인지. */
    fun isOn(routine: RoutineEntity, date: LocalDate): Boolean = isOn(routine.weekdays, date.dayOfWeek)

    /** 고치기 창의 요일 칸을 누를 때. 마지막 하나를 끄면 0이 됩니다 — 저장 단추가 막습니다. */
    fun toggled(mask: Int, day: DayOfWeek): Int = (mask and EVERY_DAY) xor bit(day)

    /** ISO 요일 번호(월=1 … 일=7). 동기화 JSON의 꼴입니다. */
    fun isoDaysOf(mask: Int): List<Int> = (1..7).filter { normalized(mask) and (1 shl (it - 1)) != 0 }

    /** ISO 요일 번호에서. 모르는 번호는 버리고, 남는 것이 없으면 0입니다(읽는 쪽이 매일로 봅니다). */
    fun maskOf(isoDays: Iterable<Int>): Int = isoDays.filter { it in 1..7 }.fold(0) { mask, day -> mask or (1 shl (day - 1)) }

    /**
     * 카드·통계에 적을 요일. 매일·평일·주말은 이름으로, 나머지는 월요일부터 늘어놓습니다(루틴 탭의 요일 알약과 같은 차례,
     * 2026-11-19부터). 월·일 루틴은 "월·일"입니다.
     */
    fun daysLabel(mask: Int): String = when (normalized(mask)) {
        EVERY_DAY -> "매일"
        WEEKDAYS -> "평일"
        WEEKEND -> "주말"
        else -> RoutineStatsCalculator.WEEK_ORDER.filter { isOn(mask, it) }.joinToString("·") { shortLabel(it) }
    }

    /** 요일 한 글자. */
    fun shortLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        DayOfWeek.SUNDAY -> "일"
    }

    /** `HH:mm`. 상시(null)면 빈 글자입니다. */
    fun clockText(minutes: Int?): String {
        val value = minutes?.takeIf { it in 0 until MINUTES_PER_DAY } ?: return ""
        return String.format(Locale.ROOT, "%02d:%02d", value / 60, value % 60)
    }

    /** `HH:mm`이나 `H:mm`을 분으로. 꼴이 틀리거나 비었으면 상시(null)입니다. */
    fun parseClock(text: String?): Int? {
        val match = CLOCK.matchEntire(text?.trim().orEmpty()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** 저장할 시각. 하루를 벗어난 값은 상시로 봅니다. */
    fun normalizedTime(minutes: Int?): Int? = minutes?.takeIf { it in 0 until MINUTES_PER_DAY }

    private val CLOCK = Regex("""(\d{1,2}):(\d{2})""")
}
