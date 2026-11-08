package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 진도를 적을 때 남긴 말을 책 메모([BookEntity.note])에 이어 붙입니다.
 *
 * 사용자가 정함(2026-11-08): 메모 끝에 `11월 8일 · 120쪽 — 글`로 한 줄씩 쌓고, 책 카드에는 최근 두 줄만 보이다가 눌러서
 * 펼칩니다. 새 자리를 만들지 않고 이미 있는 메모에 붙이므로 동기화 형식은 그대로입니다(웹은 메모의 줄만 살려 그립니다).
 * AI에는 나가지 않습니다 — 성장 보고는 책 제목과 진도만 씁니다([GrowthPrompt]).
 *
 * **한 번에 한 줄입니다.** 여러 줄로 적어도 한 줄로 잇습니다 — 카드가 "최근 두 줄"을 보일 때 한 기록이 둘로 갈리면 앞날의
 * 끝줄과 오늘 것이 섞여 보입니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object BookNotes {
    /** 책 카드에 보이는 메모 줄 수. 넘으면 "메모 전체 보기"로 펼칩니다. */
    const val COLLAPSED_LINES = 2

    /** `11월 8일 · 120쪽 — 글`. 쪽을 모르면(0) 쪽은 뺍니다. 남긴 말이 비면 null입니다. */
    fun entry(date: LocalDate, page: Int, text: String): String? {
        val body = text.trim().replace(WHITESPACE_RUN, " ")
        if (body.isEmpty()) return null
        val where = if (page > 0) " · ${page}쪽" else ""
        return "${date.monthValue}월 ${date.dayOfMonth}일$where — $body"
    }

    /** 메모 끝에 한 줄로 붙입니다. 메모가 비었으면 그 줄이 메모입니다. */
    fun append(note: String, entry: String): String =
        if (note.isBlank()) entry else note.trimEnd() + "\n" + entry

    /** 메모의 줄들. 빈 줄은 세지 않습니다(손으로 적은 메모에 빈 줄이 끼어 있어도 "최근 두 줄"이 비지 않게). */
    fun lines(note: String): List<String> = note.lines().map { it.trimEnd() }.filter { it.isNotBlank() }

    private val WHITESPACE_RUN = Regex("\\s+")
}
