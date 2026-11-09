package com.codex.appgoodwords.data

import java.time.LocalDate

/**
 * 진도를 적을 때 남긴 말을 독후감([BookEntity.review]) 끝에 붙입니다.
 *
 * 사용자가 정함(2026-11-09): 1.21.0은 책 메모([BookEntity.note]) 끝에 한 줄씩 붙였는데, 읽으며 든 생각이 모이는 곳은 독후감이라
 * **독후감에만** 붙입니다. 앞 글과 빈 줄로 띄우고 머리 줄에 `11월 9일 · 120쪽`, 그 아래에 적은 말을 줄바꿈까지 그대로 둡니다 —
 * 독후감은 펼쳐 읽고 다듬는 긴 글이라 문단처럼 읽혀야 합니다. 새 자리를 만들지 않고 이미 있는 독후감에 붙이므로 동기화 형식은
 * 그대로입니다. 독후감은 AI에 나가지 않습니다 — 성장 보고는 책 제목과 진도만 씁니다([GrowthPrompt]).
 *
 * 책 메모는 이제 수정 창에서만 적습니다. 1.21.0이 한 줄씩 쌓아 둔 메모가 남아 있어 카드는 메모의 끝 두 줄만 보이고 펼칩니다([lines]).
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object BookNotes {
    /** 책 카드에 보이는 메모 줄 수. 넘으면 "메모 전체 보기"로 펼칩니다. */
    const val COLLAPSED_LINES = 2

    /**
     * 머리 줄 `11월 9일 · 120쪽`과 그 아래의 적은 말. 쪽을 모르면(0) 쪽은 뺍니다. 남긴 말이 비면 null입니다.
     *
     * 적은 말의 줄바꿈과 빈 줄은 그대로 둡니다. 앞뒤 공백과 줄 끝의 보이지 않는 공백만 뗍니다.
     */
    fun entry(date: LocalDate, page: Int, text: String): String? {
        val body = text.trim().lines().joinToString("\n") { it.trimEnd() }
        if (body.isEmpty()) return null
        val where = if (page > 0) " · ${page}쪽" else ""
        return "${date.monthValue}월 ${date.dayOfMonth}일$where\n$body"
    }

    /** 독후감 끝에 빈 줄 하나를 띄우고 붙입니다. 독후감이 비었으면 그 기록이 독후감의 처음입니다. */
    fun append(review: String, entry: String): String =
        if (review.isBlank()) entry else review.trimEnd() + "\n\n" + entry

    /** 메모의 줄들. 빈 줄은 세지 않습니다(손으로 적은 메모에 빈 줄이 끼어 있어도 "최근 두 줄"이 비지 않게). */
    fun lines(note: String): List<String> = note.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
}
