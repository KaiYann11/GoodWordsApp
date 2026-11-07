package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 걸은 날을 가리는 데 드는 기록. 하루 걸음 수(Health Connect에서 받아 적은 것)와 기록한 걷기입니다.
 *
 * 걷는 중인 걷기가 섞여 있어도 됩니다 — 셀 때는 다 걸은 것만 봅니다.
 */
data class WalkRecords(
    val stepCounts: List<StepCountEntity> = emptyList(),
    val walks: List<WalkEntity> = emptyList(),
    /** 이 걸음 수를 넘은 날을 걸은 날로 셉니다(사용자가 정함, 2026-11-07, 기본 5,000 — 설정에서 바꿈). */
    val goalSteps: Int = Walking.DEFAULT_GOAL_STEPS
)

/**
 * 걸은 날을 정하는 곳. 하루의 걸음([DailyStep.WALK])·점수·연속 날수·AI가 모두 여기서 셉니다.
 *
 * **걸은 날은 그날 걸음이 정한 수를 넘었거나, 걷기를 하나라도 기록한 날입니다**(사용자가 정함, 2026-11-07).
 * 걸음 수로만 세면 휴대폰을 두고 걸은 날이 빠지고, 기록으로만 세면 출퇴근으로 많이 걸은 날이 빠집니다.
 * 자리마다 따로 셈하면 홈 카드와 점수가 다른 날을 걸은 날이라고 말하게 됩니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object Walking {
    const val DEFAULT_GOAL_STEPS = 5_000

    /** 설정에서 고르는 값(사용자가 정함, 2026-11-07). */
    val GOAL_CHOICES = listOf(3_000, 5_000, 7_000, 10_000)

    /**
     * 날짜별 걸음 수. 같은 날이 둘이면(다른 기기에서 읽어 아직 합쳐지지 않았으면) 큰 쪽입니다 —
     * 같은 하루를 각자 읽은 것이라 더하면 겹쳐 셉니다.
     */
    fun stepsByDate(records: WalkRecords): Map<LocalDate, Int> = records.stepCounts
        .mapNotNull { count -> parseDate(count.date)?.let { it to count.steps.coerceAtLeast(0) } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, steps) -> steps.max() }

    /** 그날 시작한, 다 걸은 걷기. 이른 것부터입니다. */
    fun walksOn(records: WalkRecords, date: LocalDate, zoneId: ZoneId): List<WalkSummary> = records.walks
        .filter { it.isFinished && toDate(it.startedAt, zoneId) == date }
        .sortedBy { it.startedAt }
        .map { it.summary() }

    /** 걸은 날 전부. 점수의 흐름과 연속 날수가 씁니다. */
    fun walkedDays(records: WalkRecords, zoneId: ZoneId): Set<LocalDate> {
        val goal = records.goalSteps.coerceAtLeast(1)
        val byCount = stepsByDate(records).filterValues { it >= goal }.keys
        val byWalk = records.walks.filter { it.isFinished }.mapTo(mutableSetOf()) { toDate(it.startedAt, zoneId) }
        return byCount + byWalk
    }

    fun walkedOn(records: WalkRecords, date: LocalDate, zoneId: ZoneId): Boolean =
        date in walkedDays(records, zoneId)

    /**
     * 그날 걷기를 숫자로만 적은 한 줄. 예: `6,240걸음 · 기록 2.1km 32분`. 아무것도 없으면 null입니다.
     *
     * **AI에 가는 걷기는 이 줄뿐입니다**(사용자가 정함, 2026-11-07 — 위치는 보내지 않고 숫자만). 길·좌표·지명은
     * 받지도 않습니다([WalkSummary]).
     */
    fun factLine(steps: Int?, walks: List<WalkSummary>): String? {
        val parts = buildList {
            steps?.takeIf { it > 0 }?.let { add(formatSteps(it)) }
            walks.forEach { walk ->
                add("기록 ${formatDistance(walk.distanceMeters)} ${formatDuration(walk.durationMillis)}")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun formatSteps(steps: Int): String = String.format(Locale.KOREA, "%,d걸음", steps)

    /** 1km 아래는 미터로, 위는 소수 한 자리 km로. */
    fun formatDistance(meters: Int): String =
        if (meters < 1_000) "${meters.coerceAtLeast(0)}m"
        else String.format(Locale.KOREA, "%.1fkm", meters / 1_000.0)

    /** `32분`, `1시간 5분`. 1분이 안 되면 `1분`으로 올립니다 — "0분 걸음"은 이상합니다. */
    fun formatDuration(millis: Long): String {
        val minutes = (millis / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0L -> "${rest}분"
            rest == 0L -> "${hours}시간"
            else -> "${hours}시간 ${rest}분"
        }
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun toDate(timestamp: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
}
