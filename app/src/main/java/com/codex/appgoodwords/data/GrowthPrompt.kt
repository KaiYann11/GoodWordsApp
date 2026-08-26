package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * AI에게 건넬 내 기록 묶음.
 *
 * 원본을 그대로 보내지 않습니다. 스무 날치 일기와 수백 건의 체크를 다 실으면 값도 비싸고,
 * 무엇보다 필요 이상으로 많은 것이 밖으로 나갑니다. 여기서 한 번 줄이고 나갑니다.
 */
data class GrowthDigest(
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val routineLines: List<String>,
    val todoLines: List<String>,
    val diaryLines: List<String>,
    val quoteLines: List<String>,
    val bookLines: List<String>,
    /** 일기를 쓰지 않은 날의 기분까지. 없으면 빈 목록입니다. */
    val moodLines: List<String> = emptyList(),
    /**
     * 지난번에 권해 받은 것.
     *
     * 없으면 빈 목록입니다. **[isEmpty]에는 세지 않습니다.** 이것만 있고 이 기간에 한 일이
     * 없으면 돌아볼 것이 없는 것입니다. 지난 조언만 들고 다시 물으면 같은 말이 돌아옵니다.
     */
    val previousAdviceLines: List<String> = emptyList(),
    /** 그 조언을 받은 날. 언제 한 말인지 알아야 "그 뒤로 어땠는지"를 셀 수 있습니다. */
    val previousAdviceOn: LocalDate? = null,
    val previousAdvicePeriod: ReportPeriod? = null
) {
    /** 아무것도 없으면 물어볼 것이 없습니다. 값만 나가고 빈 글이 돌아옵니다. */
    val isEmpty: Boolean
        get() = routineLines.isEmpty() &&
            todoLines.isEmpty() &&
            diaryLines.isEmpty() &&
            quoteLines.isEmpty() &&
            bookLines.isEmpty() &&
            moodLines.isEmpty()
}

/**
 * 복사해 둔 물음이 어느 구간의 것인지.
 *
 * 손으로 물어보는 길에서 씁니다. 앱에서 물음을 복사해 채팅창에 붙여넣고 답을 받아 오는 동안
 * 앱을 떠나 있게 되는데, 돌아와 답을 적을 때 "언제부터 언제까지를 보고 쓴 글인지"가 있어야
 * 합니다. 복사한 날짜와 기간만 남겨 두면 그때의 구간을 그대로 다시 셈할 수 있습니다.
 */
data class PendingGrowthPrompt(
    val period: ReportPeriod,
    val copiedOn: LocalDate
) {
    val from: LocalDate
        get() = copiedOn.minusDays((period.days - 1).toLong())

    val to: LocalDate
        get() = copiedOn
}

/**
 * 기록을 추려 물음을 씁니다.
 *
 * **일기 본문은 기본으로 나가지 않습니다.** 가장 사적인 글이라, 설정에서 따로 켠 사람에게만
 * 실립니다([includeDiaryBody]). 켜지 않으면 날짜·기분·날씨·글자 수만 나갑니다.
 *
 * 화면 없이 검증할 수 있도록 순수 함수로 둡니다. 무엇이 나가는지는 사용자가 보내기 전에
 * 눈으로 확인할 수 있어야 하는데, 그 미리 보기도 같은 함수를 씁니다.
 */
object GrowthPrompt {
    /** 한 갈래에서 실을 줄 수. 넘치면 값이 오르고 답도 두루뭉술해집니다. */
    private const val MAX_LINES = 30

    /** 일기 본문을 실을 때의 한 편당 글자 수. */
    private const val DIARY_BODY_LIMIT = 400

    /**
     * 이보다 오래된 돌아보기는 "지난번"이라 부르지 않습니다.
     *
     * 석 달 전에 권한 것을 두고 "그 뒤로 어떠셨나요"라고 물으면, 이미 지나간 이야기에
     * 이번 기간의 답이 끌려갑니다.
     */
    private const val PREVIOUS_ADVICE_MAX_DAYS = 90L

    fun digest(
        period: ReportPeriod,
        today: LocalDate,
        routines: List<RoutineEntity>,
        routineChecks: List<RoutineCheckEntity>,
        todos: List<TodoEntity>,
        diaries: List<DiaryEntity>,
        items: List<ContentItemEntity>,
        events: List<ExposureEventEntity>,
        books: List<BookEntity>,
        moodLogs: List<MoodLogEntity> = emptyList(),
        includeDiaryBody: Boolean,
        /** 바로 앞에 받은 돌아보기. 없으면 null. */
        previousReport: GrowthReportEntity? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): GrowthDigest {
        val from = today.minusDays((period.days - 1).toLong())
        val inRange = { millis: Long -> toDate(millis, zoneId).let { !it.isBefore(from) && !it.isAfter(today) } }

        val checksByRoutine = routineChecks.filter { inRange(it.checkedAt) }.groupingBy { it.routineId }.eachCount()
        val routineLines = RoutineOrder.sorted(routines).take(MAX_LINES).map { routine ->
            val count = checksByRoutine[routine.id] ?: 0
            "${routine.title}: ${count}회" + if (routine.category.isNotBlank()) " (${routine.category})" else ""
        }

        val todoLines = todos
            .filter { todo ->
                val due = runCatching { LocalDate.parse(todo.dueDate) }.getOrNull()
                due != null && !due.isBefore(from) && !due.isAfter(today)
            }
            .sortedBy { it.dueDate }
            .take(MAX_LINES)
            .map { todo -> "${todo.dueDate} ${todo.title}: ${if (todo.isDone) "완료" else "미완료"}" }

        val diaryLines = diaries
            .filter { diary ->
                val date = runCatching { LocalDate.parse(diary.entryDate) }.getOrNull()
                date != null && !date.isBefore(from) && !date.isAfter(today)
            }
            .sortedBy { it.entryDate }
            .take(MAX_LINES)
            .map { diary -> diaryLine(diary, includeDiaryBody) }

        // 읽은 글귀는 이력에서 봅니다. 담아 두기만 하고 안 읽은 것까지 세면 실제와 달라집니다.
        val itemById = items.associateBy { it.id }
        val quoteLines = events
            .filter { it.eventType == ExposureEventType.CONFIRMED && inRange(it.occurredAt) }
            .sortedByDescending { it.occurredAt }
            .distinctBy { it.contentItemSyncId.ifBlank { it.contentTitle } }
            .take(MAX_LINES)
            .map { event ->
                val category = itemById[event.contentItemId]?.category.orEmpty()
                event.contentTitle.ifBlank { "제목 없는 글귀" } + if (category.isNotBlank()) " ($category)" else ""
            }

        val bookLines = books
            .sortedByDescending { it.updatedAt }
            .take(MAX_LINES)
            .map { book ->
                val state = if (book.status == BookStatus.FINISHED.name) "완독" else "읽는 중 ${book.currentPage}쪽"
                "${book.title}: $state"
            }

        // 일기를 안 쓴 날의 기분입니다. 일기가 있는 날은 그 줄에 이미 붙어 있어 두 번 싣지 않습니다.
        val diaryDates = diaries.mapNotNull { runCatching { LocalDate.parse(it.entryDate) }.getOrNull() }.toSet()
        val moodLines = DayMood.byDate(logs = moodLogs, diaries = diaries)
            .filterKeys { it !in diaryDates && !it.isBefore(from) && !it.isAfter(today) }
            .toSortedMap()
            .map { (date, mood) -> "$date ${mood.label}" }
            .take(MAX_LINES)

        val previousOn = previousReport
            ?.let { toDate(it.createdAt, zoneId) }
            ?.takeIf { it.plusDays(PREVIOUS_ADVICE_MAX_DAYS) >= today }

        return GrowthDigest(
            period = period,
            from = from,
            to = today,
            routineLines = routineLines,
            todoLines = todoLines,
            diaryLines = diaryLines,
            quoteLines = quoteLines,
            bookLines = bookLines,
            moodLines = moodLines,
            previousAdviceLines = if (previousOn == null) emptyList() else previousAdvice(previousReport),
            previousAdviceOn = previousOn,
            previousAdvicePeriod = previousOn?.let { ReportPeriod.of(previousReport?.period) }
        )
    }

    /**
     * 지난번 조언에서 다시 보낼 것.
     *
     * **다음 걸음과 추천 루틴만 보냅니다.** 잘한 점은 같은 칭찬을 되풀이하게 하고,
     * 가이드는 자유롭게 쓴 글이라 그때 읽은 일기가 묻어날 수 있습니다. 사용자가 그 뒤로
     * 일기 본문 보내기를 껐다면, 껐다는 뜻이 그 글을 통해 조용히 뒤집힙니다.
     * 지난번에 권한 것을 짚는 데는 이 둘이면 충분합니다.
     */
    private fun previousAdvice(report: GrowthReportEntity?): List<String> {
        if (report == null) return emptyList()
        return (report.improvements + report.suggestedRoutines)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_LINES)
    }

    /**
     * 일기 한 줄.
     *
     * 본문을 싣지 않을 때도 글자 수는 남깁니다. 짧게 쓴 날과 길게 쓴 날의 차이는
     * 그 자체로 하나의 신호인데, 본문 없이도 전할 수 있습니다.
     */
    private fun diaryLine(diary: DiaryEntity, includeBody: Boolean): String {
        val tags = listOfNotNull(
            diary.weather.takeIf { it.isNotBlank() },
            diary.mood.takeIf { it.isNotBlank() },
            diary.kind.takeIf { it.isNotBlank() && it != DiaryKind.FREE.name }
        ).joinToString(", ")
        val head = diary.entryDate + if (tags.isNotBlank()) " [$tags]" else ""
        return if (includeBody) {
            val body = (diary.title + " " + diary.body).trim().take(DIARY_BODY_LIMIT)
            "$head $body".trim()
        } else {
            "$head 본문 ${diary.body.length}자"
        }
    }

    /** 모델에게 어떤 사람으로 답하라고 일러 주는 말. */
    fun systemPrompt(): String = buildString {
        appendLine("당신은 사용자의 습관 기록을 함께 돌아보는 한국어 코치입니다.")
        appendLine("규칙:")
        appendLine("- 기록에 있는 사실만 근거로 삼습니다. 없는 일을 지어내지 않습니다.")
        appendLine("- 다그치지 않습니다. 못 한 것을 세지 말고, 다음 한 걸음을 권합니다.")
        appendLine(
            "- [지난번에 권한 것]이 있으면 거기서부터 시작합니다. 그 뒤로 어떻게 되었는지를 " +
                "이번 기록에서 찾아 먼저 말합니다. 지켜졌으면 그렇다고 짚어 주고, 그러지 " +
                "못했으면 다그치는 대신 더 작게 쪼개어 다시 권합니다."
        )
        appendLine("- 지난번과 똑같은 말을 그대로 되풀이하지 않습니다.")
        appendLine("- 존댓말로, 한 문장은 짧게 씁니다.")
        appendLine("- 반드시 아래 형식의 JSON만 출력합니다. 설명이나 코드펜스를 붙이지 않습니다.")
        appendLine("""{"strengths":["..."],"improvements":["..."],"suggestedQuote":{"text":"...","author":"..."},"suggestedRoutines":["..."],"guide":"..."}""")
        appendLine("- strengths와 improvements는 각각 1~3개, suggestedRoutines는 0~3개입니다.")
        appendLine("- suggestedQuote는 오늘 곁에 둘 만한 짧은 글귀 하나입니다. 지어낸 인용이라면 author는 빈 문자열로 둡니다.")
        append("- guide는 다음 기간을 어떻게 보내면 좋을지 두세 문장으로 적습니다.")
    }

    /** 실제로 보낼 내 기록. 설정 화면의 미리 보기도 이 글을 그대로 보여 줍니다. */
    fun userPrompt(digest: GrowthDigest): String = buildString {
        appendLine("기간: ${digest.from} ~ ${digest.to} (${digest.period.label})")
        appendLine()
        // 지난 조언을 먼저 놓습니다. 기록보다 앞에 있어야 "그 뒤로 어땠는지"를 보며 읽습니다.
        if (digest.previousAdviceLines.isNotEmpty()) {
            // 손으로 부른 것은 단위가 없습니다. "직접 부름 돌아보기"는 말이 되지 않습니다.
            val label = digest.previousAdvicePeriod
                ?.takeIf { it != ReportPeriod.MANUAL }
                ?.let { " ${it.label} 돌아보기" }
                .orEmpty()
            appendLine("[지난번에 권한 것] ${digest.previousAdviceOn}$label")
            digest.previousAdviceLines.forEach { appendLine("- $it") }
            appendLine()
        }
        section("루틴 수행", digest.routineLines)
        section("할 일", digest.todoLines)
        section("일기", digest.diaryLines)
        // 일기를 못 쓴 날에도 그날이 어땠는지는 남아 있습니다. 이것이 없으면 바빴던 주가
        // 통째로 비어 보여, "아무것도 안 한 주"로 읽힙니다.
        section("일기 없이 남긴 기분", digest.moodLines)
        section("읽은 글귀", digest.quoteLines)
        section("독서", digest.bookLines)
        if (digest.previousAdviceLines.isNotEmpty()) {
            append("지난번에 권한 것이 그 뒤로 어떻게 되었는지부터 짚고, ")
        }
        append("위 기록을 보고 형식에 맞는 JSON으로만 답해 주세요.")
    }

    /**
     * 채팅창에 그대로 붙여넣을 한 덩어리.
     *
     * 앱이 부를 때는 일러 주는 말([systemPrompt])과 내 기록([userPrompt])을 따로 실어 보냅니다.
     * 사람이 채팅창에 붙여넣을 때는 그 자리가 하나뿐이라 이어 붙입니다. **나가는 내용은 같습니다.**
     * 미리 보기도 이 글을 그대로 보여 줍니다. 보이는 것과 나가는 것이 달라지면,
     * 무엇이 밖으로 나가는지 확인할 방법이 없어집니다.
     */
    fun chatPrompt(digest: GrowthDigest): String =
        systemPrompt() + "\n\n" + userPrompt(digest)

    private fun StringBuilder.section(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        appendLine("[$title]")
        lines.forEach { appendLine("- $it") }
        appendLine()
    }

    private fun toDate(millis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}
