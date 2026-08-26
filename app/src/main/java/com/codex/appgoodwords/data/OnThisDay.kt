package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/** 지난 이맘때 남긴 것 한 조각. */
data class OnThisDayMemory(
    val kind: SearchKind,
    val id: Long,
    val date: LocalDate,
    val title: String,
    val body: String,
    /** 몇 해 전인지. 1이면 작년입니다. */
    val yearsAgo: Int
) {
    /** "작년 오늘", "3년 전 오늘"처럼 읽히는 말. */
    val whenText: String
        get() = if (yearsAgo == 1) "작년 오늘" else "${yearsAgo}년 전 오늘"
}

/**
 * 지난 해 오늘 남긴 일기와 담아 둔 글귀를 찾아 줍니다.
 *
 * 일기와 글귀는 쌓일수록 값이 커지는 기록인데, 지금까지 화면은 최근 이레와 이번 달만
 * 보여 주었습니다. 오래 쓴 사람에게만 생기는 되돌림이 없었습니다.
 *
 * **오늘 하루만 봅니다.** 앞뒤로 며칠을 넓히면 매일 무언가 걸려서 특별하지 않게 됩니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object OnThisDay {
    /** 이보다 오래된 것은 찾지 않습니다. 십 년 전 글까지 뒤질 일은 드뭅니다. */
    private const val MAX_YEARS_AGO = 10

    /** 한 번에 보여 줄 조각 수. 많으면 홈이 목록이 됩니다. */
    const val MAX_MEMORIES = 3

    fun find(
        today: LocalDate,
        diaries: List<DiaryEntity>,
        items: List<ContentItemEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<OnThisDayMemory> {
        val fromDiaries = diaries.mapNotNull { diary ->
            val date = parseDate(diary.entryDate) ?: return@mapNotNull null
            val yearsAgo = yearsAgoOf(date, today) ?: return@mapNotNull null
            OnThisDayMemory(
                kind = SearchKind.DIARY,
                id = diary.id,
                date = date,
                title = diary.title.ifBlank { "그날의 일기" },
                body = diary.body,
                yearsAgo = yearsAgo
            )
        }

        val fromItems = items.mapNotNull { item ->
            val date = toDate(item.createdAt, zoneId)
            val yearsAgo = yearsAgoOf(date, today) ?: return@mapNotNull null
            OnThisDayMemory(
                kind = SearchKind.QUOTE,
                id = item.id,
                date = date,
                title = item.title.ifBlank { "그날 담은 글귀" },
                body = item.body,
                yearsAgo = yearsAgo
            )
        }

        // 가까운 해가 먼저입니다. 작년 것이 십 년 전 것보다 와닿습니다.
        // 같은 해라면 일기를 앞에 둡니다. 그날 직접 쓴 글이 담아 둔 글귀보다 제 것입니다.
        return (fromDiaries + fromItems)
            .sortedWith(
                compareBy<OnThisDayMemory> { it.yearsAgo }
                    .thenBy { if (it.kind == SearchKind.DIARY) 0 else 1 }
                    .thenBy { it.id }
            )
            .take(MAX_MEMORIES)
    }

    /**
     * 오늘과 달·일이 같은 해에만 값이 있습니다.
     *
     * 2월 29일에 남긴 것은 평년에는 걸리지 않습니다. 2월 28일로 당겨 붙이면 그날 남긴 다른
     * 기록과 섞여서, 사용자가 보기에는 없는 날의 기억이 튀어나온 것이 됩니다.
     */
    private fun yearsAgoOf(date: LocalDate, today: LocalDate): Int? {
        if (date.monthValue != today.monthValue || date.dayOfMonth != today.dayOfMonth) return null
        val years = Period.between(date, today).years
        return years.takeIf { it in 1..MAX_YEARS_AGO }
    }

    private fun parseDate(value: String): LocalDate? =
        value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun toDate(millis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}
