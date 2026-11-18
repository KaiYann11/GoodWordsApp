package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * 반복의 종류(사용자가 정함, 2026-11-19) — 매주 · 2주마다 · 매달 · 매년.
 *
 * 매일·요일마다 하는 것은 루틴의 자리라 두지 않았습니다(루틴은 요일·시각·알람이 있습니다). 할 일의 반복은 월세·카드값·격주
 * 분리수거·기념일처럼 "날짜가 있는 한 번"이 되풀이되는 것입니다.
 */
enum class TodoRepeatKind(val label: String) {
    NONE("반복 안 함"),
    WEEKLY("매주"),
    BIWEEKLY("2주마다"),
    MONTHLY("매달"),
    YEARLY("매년");

    companion object {
        /** 고르는 칩의 차례. "반복 안 함"이 맨 앞입니다. */
        val choices: List<TodoRepeatKind> = entries
    }
}

/**
 * 할 일 하나의 반복 규칙. [TodoEntity.repeatRule]에 [code]로 담깁니다.
 *
 * **매달·매년은 처음 고른 날을 들고 있습니다**([day]·[month]). 1월 31일에 매달로 걸면 2월에는 28일(마지막 날)에 오고, 3월에는
 * 다시 31일에 옵니다. 날을 들고 있지 않고 지난 회차의 날짜에서 따오면 2월 28일 뒤로는 28일에 머뭅니다. 매주·2주마다는 요일이
 * 날짜에서 바로 나와 들고 있을 것이 없습니다.
 */
data class TodoRepeat(
    val kind: TodoRepeatKind,
    /** 매달·매년의 날(1~31). */
    val day: Int = 0,
    /** 매년의 달(1~12). */
    val month: Int = 0
) {
    /**
     * 저장하는 꼴. 서버 `todoRepeatRule`·웹 `todoRepeatLabel`이 같은 꼴을 읽습니다.
     * `WEEKLY` · `BIWEEKLY` · `MONTHLY:31` · `YEARLY:02-29`.
     */
    val code: String
        get() = when (kind) {
            TodoRepeatKind.NONE -> ""
            TodoRepeatKind.WEEKLY -> "WEEKLY"
            TodoRepeatKind.BIWEEKLY -> "BIWEEKLY"
            TodoRepeatKind.MONTHLY -> "MONTHLY:$day"
            TodoRepeatKind.YEARLY -> "YEARLY:" + month.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
        }
}

/**
 * 할 일의 반복 셈. 한 곳에서만 셉니다 — 서버(`nextRepeatDate`)가 웹에서 끝낸 할 일의 다음 회차를 같은 규칙으로 만듭니다.
 *
 * **다음 회차는 끝낼 때 생깁니다**(사용자에게 알리고 정함, 2026-11-19). 끝내지 않으면 다음 것이 쌓이지 않습니다 — 놓친 회차가
 * "지난 일"로 줄줄이 쌓이면 못 한 것을 세는 셈이 됩니다. 늦게 끝내면 다음 회차는 오늘이나 그 뒤의 첫 날입니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object TodoRepeats {
    /** 아주 오래 밀린 것을 끝냈을 때도 멈추게 하는 한도. 매주로 백 년쯤입니다. */
    private const val MAX_STEPS = 5_200

    /** 저장된 글을 읽습니다. 모르는 꼴이나 빈 글은 null — 반복하지 않는 할 일로 봅니다. */
    fun parse(code: String?): TodoRepeat? {
        val text = code?.trim().orEmpty()
        if (text.isEmpty()) return null
        return when {
            text == "WEEKLY" -> TodoRepeat(TodoRepeatKind.WEEKLY)
            text == "BIWEEKLY" -> TodoRepeat(TodoRepeatKind.BIWEEKLY)
            text.startsWith("MONTHLY:") -> text.removePrefix("MONTHLY:").toIntOrNull()
                ?.takeIf { it in 1..31 }
                ?.let { TodoRepeat(TodoRepeatKind.MONTHLY, day = it) }
            text.startsWith("YEARLY:") -> {
                val parts = text.removePrefix("YEARLY:").split("-")
                val month = parts.getOrNull(0)?.toIntOrNull()
                val day = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size != 2 || month == null || day == null || month !in 1..12) return null
                // 2월 29일은 받습니다(윤년이 아니면 28일에 옵니다). 2월 30일처럼 어느 해에도 없는 날은 받지 않습니다.
                if (day !in 1..YearMonth.of(2024, month).lengthOfMonth()) return null
                TodoRepeat(TodoRepeatKind.YEARLY, day = day, month = month)
            }
            else -> null
        }
    }

    /** 저장할 꼴로 다듬습니다. 읽을 수 없는 글은 빈 글(반복 안 함)입니다 — 서버 `todoRepeatRule`과 같습니다. */
    fun normalize(code: String?): String = parse(code)?.code.orEmpty()

    /** 그 날짜로 거는 반복. 매달·매년은 그 날짜의 날을 들고 있습니다. */
    fun of(kind: TodoRepeatKind, date: LocalDate): TodoRepeat? = when (kind) {
        TodoRepeatKind.NONE -> null
        TodoRepeatKind.WEEKLY, TodoRepeatKind.BIWEEKLY -> TodoRepeat(kind)
        TodoRepeatKind.MONTHLY -> TodoRepeat(kind, day = date.dayOfMonth)
        TodoRepeatKind.YEARLY -> TodoRepeat(kind, day = date.dayOfMonth, month = date.monthValue)
    }

    /**
     * 고치기·적기에서 고른 종류를 저장할 글로 바꿉니다.
     *
     * 종류와 날짜를 그대로 두고 다른 것만 고쳤으면 **있던 규칙을 그대로 둡니다** — 1월 31일에 건 매달의 2월 28일 회차에서
     * 이름만 고쳤는데 날을 28일로 다시 잡으면 3월부터 28일에 옵니다. 날짜를 옮겼으면 새 날짜로 다시 겁니다.
     */
    fun ruleFor(kind: TodoRepeatKind, dueDate: LocalDate?, existing: TodoEntity?): String {
        if (dueDate == null) return ""
        val current = existing?.let { parse(it.repeatRule) }
        if (existing != null && current != null && current.kind == kind && existing.dueDate == dueDate.toString()) return current.code
        return of(kind, dueDate)?.code.orEmpty()
    }

    /** 고치기 창에 띄울 종류. */
    fun kindOf(code: String?): TodoRepeatKind = parse(code)?.kind ?: TodoRepeatKind.NONE

    /**
     * 다음 회차의 날. [due] 다음의 회차 가운데 [today]이거나 그 뒤인 첫 날입니다.
     *
     * 미리 끝내면 바로 다음 회차(11월 25일의 매달을 20일에 끝내면 12월 25일), 늦게 끝내면 오늘이나 그 뒤의 첫 회차입니다
     * (지난 회차를 다시 만들지 않습니다).
     */
    fun nextDate(repeat: TodoRepeat, due: LocalDate, today: LocalDate): LocalDate {
        var step = 1
        var candidate = occurrence(repeat, due, step)
        while (candidate.isBefore(today) && step < MAX_STEPS) {
            step += 1
            candidate = occurrence(repeat, due, step)
        }
        return candidate
    }

    private fun occurrence(repeat: TodoRepeat, due: LocalDate, step: Int): LocalDate = when (repeat.kind) {
        TodoRepeatKind.NONE -> due
        TodoRepeatKind.WEEKLY -> due.plusWeeks(step.toLong())
        TodoRepeatKind.BIWEEKLY -> due.plusWeeks(2L * step)
        TodoRepeatKind.MONTHLY -> {
            val month = YearMonth.from(due).plusMonths(step.toLong())
            month.atDay(minOf(repeat.day, month.lengthOfMonth()))
        }
        TodoRepeatKind.YEARLY -> {
            val month = YearMonth.of(due.year + step, repeat.month)
            month.atDay(minOf(repeat.day, month.lengthOfMonth()))
        }
    }

    /**
     * 다음 회차의 알람. 그 회차의 날짜에서 **같은 날 차이·같은 시각**입니다 — 마감 전날 저녁에 걸어 둔 알람은 다음 회차에도
     * 전날 저녁에 옵니다. 알람이 없으면 없습니다.
     */
    fun shiftReminder(remindAt: Long?, from: LocalDate, to: LocalDate, zoneId: ZoneId): Long? {
        if (remindAt == null) return null
        val at = Instant.ofEpochMilli(remindAt).atZone(zoneId)
        val offsetDays = at.toLocalDate().toEpochDay() - from.toEpochDay()
        return to.plusDays(offsetDays).atTime(at.toLocalTime()).atZone(zoneId).toInstant().toEpochMilli()
    }

    /**
     * 끝낸 할 일의 다음 회차. 반복하지 않거나, 이미 다음 회차를 만들었거나, 날짜가 없으면 null입니다.
     *
     * 이름·메모·사진·반복을 그대로 옮깁니다 — 사진은 월세 계좌처럼 매번 볼 것이 많고, 모아 보기는 같은 사진을 한 번만
     * 보입니다([AttachmentGallery]). 끝냄·만든 때·syncId는 새 것입니다.
     */
    fun nextOf(todo: TodoEntity, today: LocalDate, now: Long, zoneId: ZoneId): TodoEntity? {
        if (todo.repeatedAt != null) return null
        val repeat = parse(todo.repeatRule) ?: return null
        val due = runCatching { LocalDate.parse(todo.dueDate) }.getOrNull() ?: return null
        val next = nextDate(repeat, due, today)
        return TodoEntity(
            updatedAt = now,
            title = todo.title,
            note = todo.note,
            dueDate = next.toString(),
            remindAt = shiftReminder(todo.remindAt, due, next, zoneId),
            createdAt = now,
            imageUris = todo.imageUris,
            repeatRule = repeat.code
        )
    }

    /**
     * 목록 줄에 붙는 이름. "매주 수요일" · "2주마다 목요일" · "매달 25일" · "매년 12월 25일".
     * 매주·2주마다의 요일은 그 할 일의 날짜에서 따옵니다. 날짜를 모르면 종류 이름만입니다.
     */
    fun label(code: String?, dueDate: String?): String? {
        val repeat = parse(code) ?: return null
        val due = dueDate?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val weekday = due?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        return when (repeat.kind) {
            TodoRepeatKind.NONE -> null
            TodoRepeatKind.WEEKLY -> listOfNotNull("매주", weekday).joinToString(" ")
            TodoRepeatKind.BIWEEKLY -> listOfNotNull("2주마다", weekday).joinToString(" ")
            TodoRepeatKind.MONTHLY -> "매달 ${repeat.day}일"
            TodoRepeatKind.YEARLY -> "매년 ${repeat.month}월 ${repeat.day}일"
        }
    }

    /** 끝냈을 때 띄우는 말. "다음은 12월 25일(금)" — 사용자가 고른 목업 그대로입니다(2026-11-19). */
    fun nextMessage(next: TodoEntity): String {
        val date = runCatching { LocalDate.parse(next.dueDate) }.getOrNull() ?: return "다음 회차를 만들었습니다"
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        return "다음은 ${date.monthValue}월 ${date.dayOfMonth}일($weekday)"
    }

    /**
     * 목록에서 접는 지난 회차 — 반복하는 할 일을 끝내 다음 회차를 만들었고, **끝낸 날이 오늘보다 앞선 것**입니다.
     *
     * 매주 하는 일을 끝낸 것이 "지난 일"에 해마다 쉰 줄씩 쌓이지 않게 합니다. 끝낸 그날은 줄이 그어진 채 남습니다 — 누르자마자
     * 사라지면 무엇을 눌렀는지 눈으로 놓칩니다. 기록은 지우지 않습니다(점수·검색·동기화는 그대로).
     */
    fun isPastRound(todo: TodoEntity, today: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val doneAt = todo.doneAt ?: return false
        if (todo.repeatedAt == null) return false
        return Instant.ofEpochMilli(doneAt).atZone(zoneId).toLocalDate().isBefore(today)
    }
}

/**
 * 할 일의 끝냄을 뒤집은 결과. [next]는 이번에 끝내며 만든 다음 회차입니다(없으면 null).
 */
data class TodoToggle(
    val todo: TodoEntity,
    val next: TodoEntity? = null
)
