package com.codex.appgoodwords.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/**
 * 어제와 오늘 화면의 칸(사용자가 고름, 2026-11-27 — 항목 카드 두 칸). 칸마다 왼쪽이 어제 하루, 오른쪽이 오늘 지금까지이고
 * 자세한 것이 늘 펼쳐져 있습니다. 차례는 하루의 걸음 차례를 따르되 기분을 글귀 다음에 둡니다 — 한 일 옆에 어땠는지가 놓입니다.
 *
 * **기분은 견주지 않습니다**(1.25.0부터의 생각을 이어 감 — 물을 때 밝힘). 두 날의 흐름을 나란히 두기만 하고, 앞섰다는 빛도
 * 수의 차이도 없습니다.
 */
enum class CompareSection(val label: String) {
    ROUTINE("루틴"),
    QUOTE("글귀"),
    MOOD("기분"),
    STEPS("걸음"),
    TODO("할 일"),
    DIARY("일기"),
    READING("독서")
}

/** 칸 안의 한 줄 — 앞 표시(✓·기분 얼굴·걷기), 글, 뒤에 붙는 것(×2·08:10). */
data class CompareLine(val lead: String, val text: String, val trail: String = "")

/** 한 날의 칸. [headline]은 큰 수("4번", "6,240걸음")이고, 기분이나 한 것이 없는 날은 비어 있습니다. */
data class CompareColumn(val headline: String, val lines: List<CompareLine>) {
    val isEmpty: Boolean
        get() = headline.isEmpty() && lines.isEmpty()
}

data class CompareCard(
    val section: CompareSection,
    /** 머리글 옆의 한 줄 — "어제 4번 · 오늘 5번". 기분은 "견주지 않고 나란히"입니다. */
    val summary: String,
    val yesterday: CompareColumn,
    val today: CompareColumn,
    /** 오늘이 어제보다 많은 만큼 — 머리글 옆의 빛 칩 "+1". 같거나 적으면 빈 값이고, 기분은 늘 빈 값입니다. */
    val gain: String
)

object DayCompareDetails {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)

    /**
     * 두 날 가운데 한 날이라도 무엇이 있는 칸만, [CompareSection] 차례로. 일기가 잠겨 있으면([diaryLocked]) 제목 대신
     * "잠긴 일기 N편"입니다 — 그날 요약과 같게.
     */
    fun cards(pair: DayPair, diaryLocked: Boolean = false, zoneId: ZoneId = ZoneId.systemDefault()): List<CompareCard> =
        CompareSection.entries.mapNotNull { section ->
            val yesterday = column(section, pair.yesterday, diaryLocked, zoneId)
            val today = column(section, pair.today, diaryLocked, zoneId)
            if (yesterday.isEmpty && today.isEmpty) return@mapNotNull null
            val before = count(section, pair.yesterday)
            val now = count(section, pair.today)
            CompareCard(
                section = section,
                // 걸음 수를 읽지 못한 두 날(Health Connect를 잇지 않음)은 걷기 기록 수로 말합니다. "어제 — · 오늘 —"는 말이 아닙니다.
                summary = if (section == CompareSection.STEPS && before == null && now == null) {
                    "걷기 어제 ${pair.yesterday.walks.size}번 · 오늘 ${pair.today.walks.size}번"
                } else {
                    summary(section, before, now)
                },
                yesterday = yesterday,
                today = today,
                gain = if (section != CompareSection.MOOD && before != null && now != null && now > before) {
                    "+${grouped(now - before)}"
                } else {
                    ""
                }
            )
        }

    /** 견주는 수. 걸음은 Health Connect를 잇지 않은 날 null입니다. 기분은 견주지 않아 늘 null입니다. */
    private fun count(section: CompareSection, digest: DayDigest): Int? = when (section) {
        CompareSection.ROUTINE -> digest.score.routineChecks
        CompareSection.QUOTE -> digest.score.quotesRead
        CompareSection.MOOD -> null
        CompareSection.STEPS -> digest.score.walkSteps
        CompareSection.TODO -> digest.score.todosDone
        CompareSection.DIARY -> digest.score.diaries
        CompareSection.READING -> digest.score.booksRead
    }

    private fun summary(section: CompareSection, before: Int?, now: Int?): String {
        // 기분은 성적이 아니라 견주지 않습니다. 두 날을 나란히 두기만 한다는 것을 그 자리에서 말합니다.
        if (section == CompareSection.MOOD) return "견주지 않고 나란히"
        fun cell(value: Int?): String = when {
            value == null -> "—"
            section == CompareSection.STEPS -> grouped(value)
            else -> "$value${unit(section)}"
        }
        return "어제 ${cell(before)} · 오늘 ${cell(now)}"
    }

    private fun unit(section: CompareSection): String = when (section) {
        CompareSection.ROUTINE, CompareSection.MOOD -> "번"
        CompareSection.QUOTE, CompareSection.TODO -> "개"
        CompareSection.DIARY -> "편"
        CompareSection.READING -> "권"
        CompareSection.STEPS -> "걸음"
    }

    private fun column(section: CompareSection, digest: DayDigest, diaryLocked: Boolean, zoneId: ZoneId): CompareColumn {
        val score = digest.score
        return when (section) {
            CompareSection.ROUTINE -> CompareColumn(
                headline = counted(score.routineChecks, section),
                lines = digest.routineCounts.ifEmpty { digest.routineTitles.associateWith { 1 } }
                    .map { (title, times) -> CompareLine("✓", title, if (times > 1) "×$times" else "") }
            )
            CompareSection.QUOTE -> CompareColumn(
                headline = counted(score.quotesRead, section),
                lines = digest.quoteTitles.map { CompareLine("❝", it) }
            )
            CompareSection.MOOD -> CompareColumn(headline = "", lines = moodLines(digest, zoneId))
            CompareSection.STEPS -> CompareColumn(
                headline = score.walkSteps?.let { "${grouped(it)}걸음" } ?: "",
                lines = digest.walks.map { walk ->
                    CompareLine("🚶", "${distance(walk.distanceMeters)} · ${minutes(walk.durationMillis)}분", time(walk.startedAt, zoneId))
                }
            )
            CompareSection.TODO -> CompareColumn(
                headline = counted(score.todosDone, section),
                lines = digest.todoTitles.map { CompareLine("✓", it) }
            )
            CompareSection.DIARY -> CompareColumn(
                headline = counted(score.diaries, section),
                lines = if (diaryLocked) {
                    if (score.diaries > 0) listOf(CompareLine("🔒", "잠긴 일기 ${score.diaries}편")) else emptyList()
                } else {
                    digest.diaryTitles.map { CompareLine("✎", it) }
                }
            )
            CompareSection.READING -> CompareColumn(
                headline = counted(score.booksRead, section),
                lines = digest.bookTitles.map { CompareLine("📖", it) }
            )
        }
    }

    /** 찍어 둔 기분의 흐름. 찍은 것이 없고 일기에만 기분이 있으면 그 하나를 "일기"로 둡니다. */
    private fun moodLines(digest: DayDigest, zoneId: ZoneId): List<CompareLine> {
        val logged = digest.moodLogs.mapNotNull { log ->
            log.moodOption?.let { mood -> CompareLine(mood.emoji, mood.label, time(log.createdAt, zoneId)) }
        }
        if (logged.isNotEmpty()) return logged
        return listOfNotNull(digest.score.mood?.let { mood -> CompareLine(mood.emoji, mood.label, "일기") })
    }

    /** 한 것이 없으면 빈 값입니다 — 화면이 "없음"·"아직"으로 적습니다. */
    private fun counted(value: Int, section: CompareSection): String =
        if (value <= 0) "" else "$value${unit(section)}"

    private fun time(epochMillis: Long, zoneId: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(clock)

    private fun distance(meters: Int): String =
        if (meters >= 1_000) String.format(Locale.KOREA, "%.1fkm", meters / 1_000.0) else "${meters}m"

    private fun minutes(millis: Long): Long = (millis / 60_000.0).roundToLong().coerceAtLeast(1L)

    private fun grouped(value: Int): String = String.format(Locale.KOREA, "%,d", value)
}
