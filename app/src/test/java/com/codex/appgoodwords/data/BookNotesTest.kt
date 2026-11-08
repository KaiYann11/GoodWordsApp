package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookNotesTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test fun anEntryCarriesTheDateAndPage() {
        assertEquals("11월 8일 · 120쪽 — 주인공의 선택이 보인다", BookNotes.entry(today, 120, "  주인공의 선택이 보인다 "))
    }

    @Test fun anUnknownPageIsLeftOut() {
        assertEquals("11월 8일 — 초반은 느리다", BookNotes.entry(today, 0, "초반은 느리다"))
    }

    @Test fun nothingWrittenMeansNoEntry() {
        assertNull(BookNotes.entry(today, 120, ""))
        assertNull(BookNotes.entry(today, 120, " \n\t "))
    }

    @Test fun aMultiLineNoteBecomesOneLine() {
        // 한 기록이 두 줄로 갈리면 카드의 "끝의 두 줄"에 앞날의 끝줄과 오늘 것이 섞여 보입니다.
        assertEquals("11월 8일 · 5쪽 — 첫 줄 둘째 줄", BookNotes.entry(today, 5, "첫 줄\n\n  둘째 줄"))
    }

    @Test fun appendingKeepsTheExistingMemo() {
        assertEquals("새 줄", BookNotes.append("", "새 줄"))
        assertEquals("새 줄", BookNotes.append("  \n", "새 줄"))
        assertEquals("손으로 적은 메모\n새 줄", BookNotes.append("손으로 적은 메모\n\n", "새 줄"))
    }

    @Test fun blankLinesAreNotCounted() {
        assertEquals(listOf("하나", "둘"), BookNotes.lines("하나\n\n  \n둘  \n"))
        assertEquals(emptyList<String>(), BookNotes.lines(""))
    }
}
