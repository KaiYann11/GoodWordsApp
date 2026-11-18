package com.codex.appgoodwords.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈 글귀를 민 뒤 스낵바가 하는 말과 되돌릴 것이 있는지(사용자가 정함, 2026-11-18).
 *
 * 되돌리기는 그 밀기가 바꾼 것만 되돌립니다 — 이미 읽은 글귀의 읽음은 밀기 전부터 있던 것이라 그대로 둡니다.
 */
class HomeQuoteSwipeTest {
    @Test
    fun readingAndMovingOnSaysBoth() {
        val swipe = HomeQuoteSwipe(previousId = 7L, readNow = true, moved = true)

        assertEquals("읽음으로 적고 다음 글귀로 넘겼습니다.", swipe.message)
        assertTrue(swipe.canUndo)
        assertEquals("읽음을 거두고 앞 글귀로 돌아갔습니다.", swipe.undoneMessage)
    }

    @Test
    fun aQuoteReadAlreadyIsOnlyMovedOnAndKeepsItsRead() {
        val swipe = HomeQuoteSwipe(previousId = 7L, readNow = false, moved = true)

        assertEquals("다음 글귀로 넘겼습니다.", swipe.message)
        assertTrue(swipe.canUndo)
        assertEquals("앞 글귀로 돌아갔습니다.", swipe.undoneMessage)
    }

    @Test
    fun withNoOtherQuoteOnlyTheReadIsUndone() {
        val swipe = HomeQuoteSwipe(previousId = 7L, readNow = true, moved = false)

        assertEquals("읽음으로 적었습니다. 넘길 다른 글귀가 없습니다.", swipe.message)
        assertTrue(swipe.canUndo)
        assertEquals("읽음을 거뒀습니다.", swipe.undoneMessage)
    }

    @Test
    fun aReadQuoteWithNoOtherHasNothingToUndo() {
        val swipe = HomeQuoteSwipe(previousId = 7L, readNow = false, moved = false)

        assertEquals("넘길 다른 글귀가 없습니다.", swipe.message)
        assertFalse(swipe.canUndo)
    }
}
