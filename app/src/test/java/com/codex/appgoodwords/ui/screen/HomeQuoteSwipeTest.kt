package com.codex.appgoodwords.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // 이 밀기로 읽었으면 아래 알림이 보관함과 같은 콤보 알약이고 끝에 되돌리기가 붙습니다(사용자가 고름, 2026-11-18).
    @Test
    fun aSwipeThatReadsShowsTheComboWithUndo() {
        val combo = HomeQuoteSwipe(previousId = 7L, readNow = true, moved = true).comboVisuals(readToday = 3)

        assertEquals("되돌리기", combo?.actionLabel)
        assertEquals(3, combo?.count)
        // 숫자는 앞의 수에서 하나 오릅니다.
        assertEquals(2, combo?.countBefore)
        assertEquals("오늘 3개", combo?.combo?.title)
        assertEquals("읽음으로 적고 다음 글귀로 넘겼습니다. 오늘 3개, 5개까지 2개 남았습니다.", combo?.message)
    }

    @Test
    fun movingOnAQuoteReadAlreadyShowsNoCombo() {
        // 새로 읽은 것이 없으므로 콤보가 아니라 지금처럼 글로만 알립니다.
        assertNull(HomeQuoteSwipe(previousId = 7L, readNow = false, moved = true).comboVisuals(readToday = 3))
        assertNull(HomeQuoteSwipe(previousId = 7L, readNow = false, moved = false).comboVisuals(readToday = 3))
    }

    @Test
    fun aReadWithNoOtherQuoteStillShowsTheCombo() {
        val combo = HomeQuoteSwipe(previousId = 7L, readNow = true, moved = false).comboVisuals(readToday = 5)

        assertEquals("되돌리기", combo?.actionLabel)
        assertEquals("읽음으로 적었습니다. 넘길 다른 글귀가 없습니다. 오늘 5개, 5개를 채웠습니다.", combo?.message)
    }
}
