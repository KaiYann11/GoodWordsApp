package com.codex.appgoodwords.ui.screen

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 홈에서 글귀를 읽었을 때 아래에 뜨는 콤보 알약(사용자가 고름, 2026-11-18).
 *
 * 보관함과 같은 알약을 스낵바 자리에 띄워, 밀었을 때의 되돌리기와 한 자리를 씁니다.
 */
class ComboSnackbarTest {
    @get:Rule
    val compose = createComposeRule()

    private val host = SnackbarHostState()
    private lateinit var scope: CoroutineScope

    @Test
    fun aSwipeThatReadShowsTheComboAndItsUndo() {
        var result: SnackbarResult? = null
        showHost()
        val combo = HomeQuoteSwipe(previousId = 7L, readNow = true, moved = true).comboVisuals(readToday = 3)!!
        compose.runOnIdle { scope.launch { result = host.showSnackbar(combo) } }

        // 숫자는 앞의 수(2)에서 지금의 수로 오르고 멈춥니다.
        compose.onNodeWithTag(comboSnackbarTag).assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("오늘 3개").assertIsDisplayed()
        compose.onNodeWithText("5개까지 2개 남았습니다").assertIsDisplayed()

        compose.onNodeWithText("되돌리기").performClick()
        compose.waitForIdle()

        assertEquals(SnackbarResult.ActionPerformed, result)
    }

    @Test
    fun aScreenReaderHearsWhatHappenedAndCanReachUndo() {
        showHost()
        val combo = HomeQuoteSwipe(previousId = 7L, readNow = true, moved = true).comboVisuals(readToday = 3)!!
        compose.runOnIdle { scope.launch { host.showSnackbar(combo) } }

        compose.onNodeWithTag(comboSnackbarTag)
            .assert(hasContentDescription("읽음으로 적고 다음 글귀로 넘겼습니다. 오늘 3개, 5개까지 2개 남았습니다."))
        // 되돌리기는 알약에 합쳐지지 않고 따로 누를 수 있습니다.
        compose.onNodeWithText("되돌리기").assertHasClickAction()
    }

    @Test
    fun takingAReadBackSaysSoWithoutUndo() {
        showHost()
        compose.runOnIdle { scope.launch { host.showSnackbar(ComboSnackbarVisuals.toggled(confirmed = false, count = 2)) } }

        compose.onNodeWithText("읽음 취소").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("되돌리기").assertDoesNotExist()
    }

    @Test
    fun otherNoticesStayPlain() {
        showHost()
        compose.runOnIdle { scope.launch { host.showSnackbar("앞 글귀로 돌아갔습니다.") } }

        compose.onNodeWithText("앞 글귀로 돌아갔습니다.").assertIsDisplayed()
        compose.onNodeWithTag(comboSnackbarTag).assertDoesNotExist()
    }

    /** 앱과 같은 아래 알림 자리([AppSnackbar])를 띄웁니다. */
    private fun showHost() {
        compose.setContent {
            scope = rememberCoroutineScope()
            SnackbarHost(host) { data -> AppSnackbar(data) }
        }
    }
}
