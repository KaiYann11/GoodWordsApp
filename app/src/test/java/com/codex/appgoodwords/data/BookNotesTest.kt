package com.codex.appgoodwords.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookNotesTest {
    private val today = LocalDate.of(2026, 11, 9)

    @Test fun anEntryStartsWithTheDateAndPage() {
        assertEquals("11월 9일 · 120쪽\n주인공의 선택이 보인다", BookNotes.entry(today, 120, "  주인공의 선택이 보인다 "))
    }

    @Test fun anUnknownPageIsLeftOut() {
        assertEquals("11월 9일\n초반은 느리다", BookNotes.entry(today, 0, "초반은 느리다"))
    }

    @Test fun nothingWrittenMeansNoEntry() {
        assertNull(BookNotes.entry(today, 120, ""))
        assertNull(BookNotes.entry(today, 120, " \n\t "))
    }

    @Test fun lineBreaksStayAsWritten() {
        // 독후감은 문단으로 읽는 글입니다. 한 줄로 이으면 길게 적은 생각이 한 덩어리가 됩니다.
        assertEquals("11월 9일 · 5쪽\n첫 줄\n\n  둘째 줄", BookNotes.entry(today, 5, "\n첫 줄  \n\n  둘째 줄 \n"))
    }

    @Test fun appendingLeavesABlankLineAfterTheReview() {
        val entry = "11월 9일 · 5쪽\n새 생각"
        assertEquals(entry, BookNotes.append("", entry))
        assertEquals(entry, BookNotes.append("  \n", entry))
        assertEquals("써 둔 독후감\n\n$entry", BookNotes.append("써 둔 독후감\n\n\n", entry))
    }

    @Test fun blankLinesAreNotCounted() {
        assertEquals(listOf("하나", "둘"), BookNotes.lines("하나\n\n  \n둘  \n"))
        assertEquals(emptyList<String>(), BookNotes.lines(""))
    }
}
