package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.DiaryMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 오늘 기분 카드.
 *
 * 예전에는 기분이 일기에만 붙어 있어서 일기를 써야만 남았습니다. 바쁘고 힘든 날일수록
 * 일기를 못 쓰는데, 정작 그런 날이 가장 알고 싶은 날입니다.
 *
 * 그래서 **누르는 것 말고는 아무것도 요구하지 않는지**를 특히 봅니다. 적을 것이 하나라도
 * 붙으면 3초에 안 끝나고, 3초에 안 끝나면 바쁜 날 또 비어 버립니다.
 */
class TodayMoodCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun oneTapIsTheWholeThing() {
        var picked: DiaryMood? = null
        compose.setContent { TodayMoodCard(mood = null, onPick = { picked = it }) }

        compose.onNodeWithTag(todayMoodChipTag(DiaryMood.TIRED)).performClick()

        assertEquals(DiaryMood.TIRED, picked)
    }

    @Test
    fun everyMoodIsOnTheCard() {
        // 앱과 웹이 같은 선택지를 써야 한쪽에서 고른 것이 다른 쪽에서 빈칸으로 보이지 않습니다.
        compose.setContent { TodayMoodCard(mood = null, onPick = {}) }

        // 칩 줄은 가로로 굴러갑니다. 화면 밖에 있는 것은 아예 만들어지지 않아 굴려야 짚힙니다.
        DiaryMood.entries.forEach { mood ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(todayMoodChipTag(mood)))
            compose.onNodeWithTag(todayMoodChipTag(mood)).assertExists()
        }
    }

    @Test
    fun whatWasAlreadyAnsweredIsShown() {
        // 물어봐 놓고 답이 있는 줄 모르면 사용자는 같은 것을 두 번 말하게 됩니다.
        compose.setContent { TodayMoodCard(mood = DiaryMood.GOOD, onPick = {}) }

        compose.onNodeWithText("오늘은 좋음").assertIsDisplayed()
        compose.onNodeWithText("오늘 기분은 어떠세요?").assertDoesNotExist()
    }

    @Test
    fun theMoodFromADiaryCannotBeClearedHere() {
        // 일기에서 온 기분은 일기에서 고칩니다. 여기서 지울 수 있는 것은 찍어 둔 것뿐입니다.
        compose.setContent { TodayMoodCard(mood = DiaryMood.GOOD, onPick = {}, onClear = null) }

        compose.onNodeWithText("지우기").assertDoesNotExist()
    }

    @Test
    fun aTapCanBeTakenBack() {
        var cleared = false
        compose.setContent {
            TodayMoodCard(mood = DiaryMood.GOOD, onPick = {}, onClear = { cleared = true })
        }

        compose.onNodeWithText("지우기").performClick()

        assertTrue(cleared)
    }
}
