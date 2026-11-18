package com.codex.appgoodwords.data

import java.time.LocalDate
import java.util.Locale

/**
 * 어제와 견주는 것(사용자가 정함, 2026-11-19). 차례가 곧 카드와 "두 날 나란히"의 차례입니다 — 오늘의 걸음과 같은 앞쪽(루틴·글귀·
 * 할 일·일기·독서), 그다음 걸음과 걷기.
 *
 * **기분과 점수는 견주지 않습니다.** 기분은 나아져야 할 성적이 아니고(하루 점수에도 넣지 않습니다), 점수 차이는 축을 바꾸면
 * 지난날도 바뀌는 숫자라 견줄 것이 못 됩니다. 한 일만 셉니다.
 */
enum class ComparedKind(
    val label: String,
    /** 수를 부를 때의 단위 — "루틴 3번", "1번 더". */
    val unit: String,
    /** 둘 다 한 날 "어제처럼 오늘도 …"에 이어 붙이는 말과 끝맺는 말. */
    val alsoJoin: String,
    val alsoEnd: String
) {
    ROUTINE("루틴", "번", "루틴을 밟고", "루틴을 밟았습니다"),
    QUOTE("글귀", "개", "글귀를 읽고", "글귀를 읽었습니다"),
    TODO("할 일", "개", "할 일을 끝내고", "할 일을 끝냈습니다"),
    DIARY("일기", "편", "일기를 쓰고", "일기를 썼습니다"),
    READING("독서", "권", "책을 읽고", "책을 읽었습니다"),
    STEPS("걸음", "걸음", "걷고", "걸었습니다"),
    WALK("걷기", "번", "걷기를 기록하고", "걷기를 기록했습니다")
}

/** 한 가지의 두 날 수. 걸음은 Health Connect를 잇지 않았으면 그날 수가 없습니다(null). */
data class ComparedCount(
    val kind: ComparedKind,
    val today: Int,
    val yesterday: Int
) {
    /** 카드에 보이는 줄 — 오늘 한 것이고 어제보다 많거나 같은 것. */
    val isAheadOrEven: Boolean
        get() = today > 0 && today >= yesterday

    /** 둘 다 했지만 오늘이 적은 것. 앞선 것이 하나도 없을 때 숫자 없이 "어제처럼 오늘도"로만 말합니다. */
    val isShared: Boolean
        get() = today in 1 until yesterday
}

/**
 * 어제의 나와 비교 — **오늘 지금까지와 어제 하루 전체**입니다(사용자가 정함, 2026-11-19. 내가 권한 "어제 같은 시각까지"가 아님).
 *
 * 카드는 **한 일만 짚습니다**(사용자가 고름): 오늘이 어제보다 많거나 같은 것만 줄로 보이고, 적은 것은 숫자 없이 빠집니다.
 * 앞선 것이 하나도 없으면 둘 다 한 것을 "어제처럼 오늘도 …" 한 줄로 말합니다. 두 날의 수를 모두 보는 것은 누를 때만입니다
 * ("두 날 나란히 보기").
 *
 * 셈은 하루 점수와 같은 [DayScoreCalculator.digest]에서 가져옵니다 — 따로 세면 카드와 그날 요약이 다른 수를 말합니다.
 * 저장하지 않고 그때그때 셉니다.
 */
data class DayComparison(
    val today: LocalDate,
    /** 두 날 가운데 한 날이라도 한 것. [ComparedKind] 차례입니다. */
    val rows: List<ComparedCount>
) {
    val aheadOrEven: List<ComparedCount>
        get() = rows.filter { it.isAheadOrEven }

    val shared: List<ComparedCount>
        get() = rows.filter { it.isShared }

    /**
     * 카드를 둘지. 오늘 한 것이 없거나 어제가 통째로 비었으면 두지 않습니다 — 견줄 것이 없는 날에 빈 카드나 "어제는
     * 없었음"만 늘어선 카드가 자리를 차지하면, 못 한 날을 확인하는 자리가 됩니다.
     */
    val shown: Boolean
        get() = rows.any { it.today > 0 } && rows.any { it.yesterday > 0 }
}

object DayComparisons {
    /** 카드에 펼치는 줄의 수. 나머지는 "두 날 나란히"에서 봅니다. */
    const val CARD_LINES = 4

    /** "어제처럼 오늘도"에 이어 말하는 수. 길어지면 한 줄이 아니게 됩니다. */
    private const val SHARED_KINDS = 3

    fun of(today: DayDigest, yesterday: DayDigest): DayComparison {
        val rows = ComparedKind.entries.map { kind ->
            ComparedCount(kind = kind, today = countOf(kind, today), yesterday = countOf(kind, yesterday))
        }.filter { it.today > 0 || it.yesterday > 0 }
        return DayComparison(today = today.score.date, rows = rows)
    }

    private fun countOf(kind: ComparedKind, digest: DayDigest): Int = when (kind) {
        ComparedKind.ROUTINE -> digest.score.routineChecks
        ComparedKind.QUOTE -> digest.score.quotesRead
        ComparedKind.TODO -> digest.score.todosDone
        ComparedKind.DIARY -> digest.score.diaries
        // 독서는 쪽수가 아니라 편 책의 수입니다(하루 점수와 같게). 쪽수는 그날 기록이 덮어써 견줄 수 없습니다.
        ComparedKind.READING -> digest.score.booksRead
        ComparedKind.STEPS -> digest.score.walkSteps ?: 0
        ComparedKind.WALK -> digest.walks.size
    }

    /** 수를 부르는 말 — "루틴 3번", "할 일 1개 끝냄", "6,240걸음". */
    fun amount(kind: ComparedKind, count: Int): String = when (kind) {
        ComparedKind.TODO -> "할 일 ${count}개 끝냄"
        ComparedKind.READING -> "책 ${count}권"
        ComparedKind.STEPS -> "${grouped(count)}걸음"
        else -> "${kind.label} ${count}${kind.unit}"
    }

    /** 카드의 한 줄 — "루틴 3번 — 1번 더" · "글귀 2개 — 어제만큼" · "할 일 1개 끝냄 — 어제는 없었음". */
    fun line(row: ComparedCount): String {
        val against = when {
            row.yesterday == 0 -> "어제는 없었음"
            row.today == row.yesterday -> "어제만큼"
            else -> "${grouped(row.today - row.yesterday)}${row.kind.unit} 더"
        }
        return "${amount(row.kind, row.today)} — $against"
    }

    /** 앞선 것이 없는 날의 한 줄 — "어제처럼 오늘도 루틴을 밟고 글귀를 읽었습니다." 둘 다 한 것이 없으면 null. */
    fun sharedLine(comparison: DayComparison): String? {
        val kinds = comparison.shared.map { it.kind }.distinctBy { if (it == ComparedKind.WALK) ComparedKind.STEPS else it }
            .take(SHARED_KINDS)
        if (kinds.isEmpty()) return null
        val joined = kinds.dropLast(1).joinToString(" ") { it.alsoJoin }
        return "어제처럼 오늘도 " + listOf(joined, kinds.last().alsoEnd).filter { it.isNotBlank() }.joinToString(" ") + "."
    }

    /** "두 날 나란히"의 한 칸 — 걸음은 자릿수를 끊고, 나머지는 단위를 붙입니다. 안 한 날은 0으로 적습니다. */
    fun cell(kind: ComparedKind, count: Int): String =
        if (kind == ComparedKind.STEPS) grouped(count) else "$count${kind.unit}"

    /**
     * AI 하루 돌아보기에 싣는 줄(사용자가 정함, 2026-11-19 — 하루 돌아보기에 싣기). 두 날의 숫자만 갑니다 — 이름·글은 그날
     * 기록 칸에 이미 있고, 일기 제목(`displayTitle`은 본문 첫머리일 수 있음)은 여기서 보내지 않습니다.
     * 카드와 달리 적은 것도 함께 갑니다. 다그치지 말라는 규칙은 [GrowthPrompt.systemPrompt]에 있습니다.
     */
    fun promptLines(comparison: DayComparison): List<String> =
        comparison.rows.map { row ->
            "${row.kind.label}: 어제 ${cell(row.kind, row.yesterday)} · 오늘 ${cell(row.kind, row.today)}"
        }

    private fun grouped(value: Int): String = String.format(Locale.KOREA, "%,d", value)
}
