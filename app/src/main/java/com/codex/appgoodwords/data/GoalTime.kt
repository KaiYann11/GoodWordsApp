package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 목표에 들인 시간을 세고 적는 규칙(사용자가 정함, 2026-11-08). 목표 화면·목표 카드·AI가 같은 셈을 쓰게 한곳에 둡니다.
 *
 * **쌓은 것만 셉니다.** 목표 시간이나 몇 %, "부족" 같은 것은 없습니다 — 목표 탭은 기한이나 숫자로 재지 않고
 * 따라가는 곳이고(2026-10-29), 이번에 더한 것도 "누적한 시간"입니다. 순수 함수라 기기 없이 검증합니다.
 */
object GoalTime {
    /** 한 번에 남길 수 있는 가장 긴 시간. 타이머를 끄지 않고 잊었어도 하루를 넘겨 쌓이지 않게 합니다. */
    const val MAX_MINUTES = 24 * 60

    /** 고르기만 하면 되는 시간. 끝나고 나서 대개 이쯤입니다. */
    val QUICK_MINUTES = listOf(15, 30, 60, 120)

    /** `45분`, `1시간`, `1시간 30분`, `120시간 30분`. */
    fun format(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return when {
            hours == 0 -> "${rest}분"
            rest == 0 -> "${hours}시간"
            else -> "${hours}시간 ${rest}분"
        }
    }

    fun total(logs: List<GoalTimeLogEntity>): Int = logs.sumOf { it.minutes }

    /** 최근 [GoalTracking.RECENT_DAYS]일(오늘 포함)에 들인 시간. 루틴의 "최근 4주"와 같은 폭입니다. */
    fun recent(logs: List<GoalTimeLogEntity>, today: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Int {
        val from = today.minusDays(GoalTracking.RECENT_DAYS - 1L)
        return logs.filter { Instant.ofEpochMilli(it.loggedAt).atZone(zoneId).toLocalDate() in from..today }
            .sumOf { it.minutes }
    }

    /** 직접 적은 시간·분. 둘 다 비거나 0이면, 또는 하루를 넘으면 null입니다. */
    fun parse(hoursText: String, minutesText: String): Int? {
        val hours = hoursText.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        val minutes = minutesText.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
        if (hours < 0 || minutes < 0) return null
        val total = hours.toLong() * 60 + minutes
        return total.takeIf { it in 1..MAX_MINUTES.toLong() }?.toInt()
    }

    /**
     * 타이머로 잰 시간(분). 1분이 안 돼도 1분, 하루를 넘으면 하루로 깎습니다 — 남기기 전에 고칠 수 있습니다.
     * 분은 올려 셉니다(42분 10초는 43분). 잰 것보다 적게 적히면 들인 시간이 줄어 보입니다.
     */
    fun elapsedMinutes(startedAt: Long, endedAt: Long): Int {
        val millis = (endedAt - startedAt).coerceAtLeast(0L)
        val minutes = (millis + 59_999L) / 60_000L
        return minutes.coerceIn(1L, MAX_MINUTES.toLong()).toInt()
    }

    /** 재는 중인 시간 `0:42:10`. */
    fun clock(elapsedMillis: Long): String {
        val seconds = (elapsedMillis.coerceAtLeast(0L)) / 1_000L
        return "%d:%02d:%02d".format(seconds / 3_600, (seconds / 60) % 60, seconds % 60)
    }
}

/**
 * 재고 있는(또는 막 끝낸) 타이머. **이 기기에만 둡니다** — 재는 것은 이 휴대폰에서 하는 일이고, 남기기를 눌러야
 * 기록([GoalTimeLogEntity])이 되어 동기화됩니다. 한 번에 하나입니다.
 *
 * 시작한 때를 적어 두고 그때그때 빼서 셉니다. 그래서 앱을 닫아도, 휴대폰을 꺼도 계속 잽니다.
 */
data class GoalTimer(
    val goalSyncId: String,
    val startedAt: Long,
    /** 끝낸 때. null이면 재는 중입니다. 끝낸 뒤에도 남기기 전까지는 남겨 둡니다 — 화면을 떠나도 잰 시간을 잃지 않게. */
    val stoppedAt: Long? = null
) {
    val running: Boolean
        get() = stoppedAt == null

    /** 끝냈으면 잰 시간(분), 재는 중이면 null. */
    val measuredMinutes: Int?
        get() = stoppedAt?.let { GoalTime.elapsedMinutes(startedAt, it) }
}
