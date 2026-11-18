package com.codex.appgoodwords.ui.screen

import androidx.compose.material3.SnackbarDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 홈에서 글귀를 읽었을 때 아래에 뜨는 콤보 알약(사용자가 고름, 2026-11-18).
 *
 * 보관함과 같은 수(오늘 읽은 수)를 보여 주고, 화면 읽기 프로그램에는 무엇을 했는지와 콤보를 함께 들려줍니다.
 */
class ComboSnackbarVisualsTest {
    @Test
    fun readingByTheButtonCountsUpWithoutUndo() {
        // 단추는 다시 누르면 거두므로 되돌리기를 달지 않습니다.
        val combo = ComboSnackbarVisuals.toggled(confirmed = true, count = 1)

        assertTrue(combo.confirmed)
        assertNull(combo.actionLabel)
        assertEquals(0, combo.countBefore)
        assertEquals("읽음으로 적었습니다. 오늘 첫 글귀, 5개까지 4개 남았습니다.", combo.message)
    }

    @Test
    fun takingAReadBackCountsDown() {
        val combo = ComboSnackbarVisuals.toggled(confirmed = false, count = 0)

        assertFalse(combo.confirmed)
        assertEquals(1, combo.countBefore)
        assertEquals("읽음을 거뒀습니다. 오늘은 아직, 한 개만 읽어도 오늘이 시작됩니다.", combo.message)
    }

    @Test
    fun aMilestoneIsCelebratedInWords() {
        val combo = ComboSnackbarVisuals.toggled(confirmed = true, count = 10)

        assertTrue(combo.combo.isMilestone)
        assertEquals("읽음으로 적었습니다. 오늘 10개, 10개를 채웠습니다.", combo.message)
    }

    @Test
    fun itHasNoCloseButtonAndGoesLikeOtherNotices() {
        // 닫기 단추를 달면 알약이 보관함과 달라 보입니다. 머무는 때는 여느 짧은 알림과 같습니다(되돌리기 4초).
        val combo = ComboSnackbarVisuals(lead = "읽음으로 적고 다음 글귀로 넘겼습니다.", count = 2, confirmed = true, actionLabel = "되돌리기")

        assertFalse(combo.withDismissAction)
        assertEquals(SnackbarDuration.Short, combo.duration)
    }
}
