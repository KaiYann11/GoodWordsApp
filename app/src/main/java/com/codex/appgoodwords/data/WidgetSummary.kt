package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 위젯 아래에 한두 줄로 붙는 오늘 요약.
 *
 * 위젯은 좁습니다. 다 보여 주려 하면 글귀가 밀려나 정작 이 위젯을 놓은 이유가 사라집니다.
 * 그래서 **지금 해야 할 것**만 남깁니다. 끝낸 일과 다 읽은 책은 넣지 않습니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
data class WidgetSummary(
    /** 오늘까지 마감인데 아직 안 끝낸 일. 지난 일도 오늘 할 일로 봅니다. */
    val remainingTodos: Int,
    val overdueTodos: Int,
    /** 지금 읽는 책. 없으면 빈 문자열입니다. */
    val readingTitle: String,
    /** 그 책의 진도(0~100). 전체 쪽수를 모르면 null입니다. */
    val readingPercent: Int?
) {
    val hasTodos: Boolean
        get() = remainingTodos > 0

    val hasBook: Boolean
        get() = readingTitle.isNotBlank()

    val isEmpty: Boolean
        get() = !hasTodos && !hasBook

    /** "할 일 3개 · 지난 일 1개"처럼 한 줄로. */
    val todoLine: String
        get() = buildString {
            append("할 일 ${remainingTodos}개")
            if (overdueTodos > 0) append(" · 지난 일 ${overdueTodos}개")
        }

    /** "읽는 중 · 몰입의 즐거움 34%"처럼 한 줄로. */
    val bookLine: String
        get() = buildString {
            append("읽는 중 · ")
            append(readingTitle)
            // 전체 쪽수를 모르면 비율을 적지 않습니다. 0%라고 쓰면 안 읽은 것처럼 보입니다.
            readingPercent?.let { append(" ${it}%") }
        }

    companion object {
        fun of(
            todos: List<TodoEntity>,
            books: List<BookEntity>,
            today: LocalDate
        ): WidgetSummary {
            val open = todos.filter { it.doneAt == null && it.dueDate <= today.toString() }
            // 가장 최근에 손댄 책을 보여 줍니다. 여러 권을 함께 읽어도 지금 보는 것은 하나입니다.
            val reading = books.filterNot { it.isFinished }.maxByOrNull { it.updatedAt }

            return WidgetSummary(
                remainingTodos = open.size,
                overdueTodos = open.count { it.isOverdueOn(today) },
                readingTitle = reading?.title.orEmpty(),
                readingPercent = reading?.progress?.let { (it * 100).toInt() }
            )
        }
    }
}
