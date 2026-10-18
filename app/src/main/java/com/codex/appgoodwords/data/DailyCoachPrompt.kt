package com.codex.appgoodwords.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * 오늘 하루치 기록 묶음.
 *
 * 돌아보기([GrowthDigest])는 지난 이레나 한 달을 봅니다. 이것은 **오늘만** 봅니다.
 * 더 멀리 보면 돌아보기가 하나 더 생기는 셈이고, 같은 기록을 두고 두 자리가 서로 다른
 * 말을 하게 됩니다. 오늘의 코치가 아는 것은 "오늘 무엇을 했나"까지입니다.
 *
 * 무엇을 했는지는 [DayDigest]에서 그대로 가져옵니다. 여기서 따로 세면 그날 카드에 적힌 것과
 * 코치가 보는 것이 어긋납니다.
 */
data class DailyCoachDigest(
    val date: LocalDate,
    /** 지금 몇 시인지. 이른 아침에 남은 것을 재촉하지 않도록 함께 보냅니다. */
    val at: LocalTime,
    /** 오늘 한 것. 갈래마다 한 줄입니다. */
    val doneLines: List<String>,
    /**
     * 아직 안 밟은 루틴.
     *
     * **못 한 것을 세려고 보내는 것이 아닙니다.** 권할 하나를 고르는 데만 씁니다
     * (일러 주는 말에 그렇게 적어 둡니다). 그래서 앞의 몇 개만 보냅니다 — 하루에 다 밟는
     * 목록이 아니고, 서른 줄을 보내면 그 자체가 못다 한 일 목록이 됩니다.
     */
    val restRoutines: List<String>,
    /** "걸음 2/3" 꼴. 고른 걸음을 얼마나 밟았는지입니다. */
    val stepLine: String,
    val streakDays: Int,
    /** 오늘의 기분. 찍어 둔 것도 일기에 딸린 것도 없으면 빈 값입니다([DayMood]). */
    val moodLabel: String,
    /**
     * 이미 담아 둔 글귀.
     *
     * 추천을 받으려는 것이 아니라 **빼 달라고** 보냅니다([GrowthDigest.ownedQuoteLines]와 같은 이유).
     */
    val ownedQuoteLines: List<String>
) {
    /** 오늘 한 것이 하나도 없으면 물어볼 거리가 없습니다. */
    val isEmpty: Boolean
        get() = doneLines.isEmpty()

    /**
     * 오늘 기록의 지문.
     *
     * **시계는 넣지 않습니다.** 넣으면 지문이 매분 달라져 "같은 기록으로 두 번 묻지 않는다"는
     * 규칙이 통째로 무너집니다. 아직 안 밟은 루틴도 넣지 않습니다 — 루틴을 하나 새로 만든 것은
     * 오늘 한 일이 아닙니다.
     */
    val fingerprint: String
        get() = if (isEmpty) "" else buildString {
            append(date)
            doneLines.forEach { append('|').append(it) }
            append('#').append(stepLine)
        }.hashCode().toString(16)
}

/**
 * 오늘 하루를 보고 건넬 한마디를 물어봅니다.
 *
 * 화면 없이 검증할 수 있도록 순수 함수로 둡니다. 무엇이 나가는지는 설정에서 그대로
 * 미리 볼 수 있어야 하는데([chatPrompt]), 그 미리 보기도 같은 함수를 씁니다.
 */
object DailyCoachPrompt {
    /** 권할 하나를 고르는 데 쓸 만큼만. 사용자가 정한 차례대로 앞에서 셉니다. */
    private const val MAX_REST_ROUTINES = 8

    /** 빼 달라고 보내는 목록. 하루에 여러 번 나가는 물음이라 돌아보기보다 짧게 싣습니다. */
    private const val MAX_OWNED_QUOTES = 20

    /** 한 갈래에 적을 이름 수. 루틴을 열 개 밟은 날 그 줄만 길어집니다. */
    private const val MAX_NAMES = 8

    fun digest(
        today: LocalDate,
        at: LocalTime,
        /** 그날 무엇을 했는지. [DayScoreCalculator.digest]가 만든 것 그대로입니다. */
        dayDigest: DayDigest,
        /** 고른 걸음을 어디까지 밟았는지. [DailyLoopCalculator.build]가 만든 것 그대로입니다. */
        progress: DailyProgress,
        routines: List<RoutineEntity>,
        /** 오늘 남은 체크. 어느 루틴이 아직인지 가릴 때만 씁니다. */
        todayChecks: List<RoutineCheckEntity>,
        diaries: List<DiaryEntity>,
        items: List<ContentItemEntity>,
        includeDiaryBody: Boolean
    ): DailyCoachDigest {
        val todayDiaries = diaries.filter { runCatching { LocalDate.parse(it.entryDate) }.getOrNull() == today }

        val doneLines = buildList {
            names("루틴", dayDigest.routineTitles)?.let { add(it) }
            names("할 일", dayDigest.todoTitles)?.let { add(it) }
            names("읽은 글귀", dayDigest.quoteTitles)?.let { add(it) }
            names("독서", dayDigest.bookTitles)?.let { add(it) }
            // 일기는 제목도 본문으로 봅니다. 켜지 않으면 날짜·기분·날씨·글자 수만 나갑니다 —
            // 그 규칙은 GrowthPrompt.diaryLine 한 곳에 있습니다.
            todayDiaries.forEach { add("일기: " + GrowthPrompt.diaryLine(it, includeDiaryBody)) }
            dayDigest.score.mood?.let { add("기분: ${it.label}") }
        }

        // 아직 안 밟은 루틴을 추리는 규칙은 한 곳에 있습니다. 화면의 가벼운 한 줄
        // ([RoutineNudge])과 AI에게 보내는 목록이 다른 루틴을 가리키면 안 됩니다.
        val restRoutines = RoutineNudgeCalculator.restOfToday(routines, todayChecks)
            .map { it.title.trim() }
            .filter { it.isNotBlank() }
            .take(MAX_REST_ROUTINES)

        val ownedQuoteLines = items
            .filter { !it.type.isPersonalRecord }
            .sortedByDescending { it.updatedAt }
            .take(MAX_OWNED_QUOTES)
            .mapNotNull { item ->
                val title = item.title.trim().ifBlank { item.body.trim().take(40) }
                title.takeIf { it.isNotBlank() }
                    ?.plus(if (item.author.isNotBlank()) " (${item.author.trim()})" else "")
            }

        return DailyCoachDigest(
            date = today,
            at = at,
            doneLines = doneLines,
            restRoutines = restRoutines,
            stepLine = "걸음 ${progress.doneCount}/${progress.stepCount}",
            streakDays = progress.streakDays,
            moodLabel = dayDigest.score.mood?.label.orEmpty(),
            ownedQuoteLines = ownedQuoteLines
        )
    }

    /**
     * 지문만 셉니다.
     *
     * 화면은 "다시 물을 때인가"를 늘 들여다보는데, 그때마다 추천에서 뺄 목록까지 챙기는 것은
     * 헛일입니다. 지문에 들어가지 않는 것들([DailyCoachDigest.fingerprint])은 비워서 부릅니다 —
     * 같은 함수를 쓰므로 실제로 물을 때 나오는 지문과 **반드시 같습니다.**
     */
    fun fingerprint(
        today: LocalDate,
        dayDigest: DayDigest,
        progress: DailyProgress,
        diaries: List<DiaryEntity>,
        includeDiaryBody: Boolean
    ): String = digest(
        today = today,
        at = LocalTime.MIDNIGHT,
        dayDigest = dayDigest,
        progress = progress,
        routines = emptyList(),
        todayChecks = emptyList(),
        diaries = diaries,
        items = emptyList(),
        includeDiaryBody = includeDiaryBody
    ).fingerprint

    /** 모델에게 어떤 사람으로 답하라고 일러 주는 말. */
    fun systemPrompt(): String = buildString {
        appendLine("당신은 사용자의 오늘 하루를 곁에서 보는 한국어 코치입니다.")
        appendLine("하루가 끝난 뒤의 총평이 아니라, 하루가 흘러가는 지금 건네는 한마디를 씁니다.")
        appendLine("규칙:")
        appendLine("- 기록에 있는 사실만 근거로 삼습니다. 없는 일을 지어내지 않습니다.")
        appendLine(
            "- 못 한 것을 세지 않습니다. [아직 안 밟은 루틴]은 다그치는 데 쓰지 말고, " +
                "권할 하나를 고르는 데만 씁니다."
        )
        appendLine("- 지금 시각을 봅니다. 이른 아침이면 재촉하지 않고, 밤이면 새 일을 벌이게 하지 않습니다.")
        appendLine("- 한마디는 두 문장을 넘기지 않습니다. 홈 화면에서 스치듯 읽는 글입니다.")
        appendLine("- 존댓말로 담담하게 씁니다. 느낌표와 이모지는 쓰지 않습니다.")
        appendLine("- 반드시 아래 형식의 JSON만 출력합니다. 설명이나 코드펜스를 붙이지 않습니다.")
        appendLine("""{"message":"...","quote":{"text":"...","author":"..."},"routine":"...","action":"..."}""")
        appendLine("- message는 늘 적습니다. 오늘 한 것 중 하나를 짚어 주세요.")
        appendLine(
            "- quote·routine·action은 **권할 것이 있을 때만** 적고, 없으면 빈 문자열로 둡니다. " +
                "셋을 매번 채우지 마세요."
        )
        appendLine(
            "- quote는 지금 곁에 둘 만한 짧은 글귀 하나입니다. **[이미 담아 둔 것]에 있는 것은 " +
                "권하지 않습니다.** 당신이 알고 있는 것에서 고르세요. 지어낸 인용이라면 " +
                "author는 빈 문자열로 둡니다."
        )
        appendLine(
            "- routine은 새로 만들어 볼 루틴의 이름 한 줄입니다. 이미 있는 루틴은 적지 않습니다. " +
                "어지간하면 비워 두세요 — 루틴은 늘리는 것보다 지키는 것이 어렵습니다."
        )
        append("- action은 오늘 안에 끝낼 수 있는 작은 한 가지입니다.")
    }

    /** 실제로 보낼 내 기록. 설정의 미리 보기도 이 글을 그대로 보여 줍니다. */
    fun userPrompt(digest: DailyCoachDigest): String = buildString {
        val weekday = digest.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        appendLine("오늘: ${digest.date} ($weekday) ${"%02d:%02d".format(digest.at.hour, digest.at.minute)}")
        append(digest.stepLine)
        // 이어 온 날은 오늘을 어떻게 말할지를 바꿉니다. 지킬 것이 있는 사람에게 할 말과
        // 오늘 처음 밟는 사람에게 할 말이 같을 수 없습니다.
        if (digest.streakDays > 0) append(" · ${digest.streakDays}일째 이어 오는 중")
        appendLine()
        appendLine()
        section("오늘 한 것", digest.doneLines)
        section("아직 안 밟은 루틴", digest.restRoutines)
        section("이미 담아 둔 것 (추천에서 빼 주세요)", digest.ownedQuoteLines)
        append("지금 건넬 한마디를 형식에 맞는 JSON으로만 답해 주세요.")
    }

    /**
     * 설정에서 "무엇이 나가는지" 보여 줄 한 덩어리.
     *
     * 앱이 부를 때는 일러 주는 말과 내 기록을 따로 실어 보냅니다. 미리 보기에는 그 자리가
     * 하나뿐이라 이어 붙입니다. **나가는 내용은 같습니다** — 보이는 것과 나가는 것이
     * 달라지면 확인할 방법이 없어집니다([GrowthPrompt.chatPrompt]와 같은 이유).
     */
    fun chatPrompt(digest: DailyCoachDigest): String = systemPrompt() + "\n\n" + userPrompt(digest)

    private fun names(title: String, values: List<String>): String? {
        val kept = values.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(MAX_NAMES)
        return if (kept.isEmpty()) null else "$title: ${kept.joinToString(", ")}"
    }

    private fun StringBuilder.section(title: String, lines: List<String>) {
        if (lines.isEmpty()) return
        appendLine("[$title]")
        lines.forEach { appendLine("- $it") }
        appendLine()
    }
}
