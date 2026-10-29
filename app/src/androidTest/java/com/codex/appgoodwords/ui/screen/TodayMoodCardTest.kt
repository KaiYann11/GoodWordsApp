package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.codex.appgoodwords.data.DayMood
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.MoodLogEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private val today = LocalDate.of(2026, 10, 29)

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

        compose.onNodeWithText("지금은 좋음").assertIsDisplayed()
        compose.onNodeWithText("오늘 기분은 어떠세요?").assertDoesNotExist()
    }

    @Test
    fun theMoodFromADiaryCannotBeClearedHere() {
        // 일기에서 온 기분은 일기에서 고칩니다. 여기서 지울 수 있는 것은 찍어 둔 것뿐입니다.
        compose.setContent { TodayMoodCard(mood = DiaryMood.GOOD, onPick = {}, onClear = {}) }

        compose.onNodeWithText("지우기").assertDoesNotExist()
        compose.onNodeWithTag(moodTimelineTag).assertDoesNotExist()
        compose.onNodeWithText("오늘의 흐름", substring = true).assertDoesNotExist()
    }

    @Test
    fun aTapCanBeTakenBack() {
        var cleared: String? = null
        val first = log("first", 9, DiaryMood.GOOD)
        val last = log("last", 18, DiaryMood.TIRED)
        compose.setContent {
            TodayMoodCard(mood = DiaryMood.TIRED, logs = listOf(first, last), onPick = {}, onClear = { cleared = it })
        }

        // 기록마다 지울 길이 보이고, 옆으로 넘기다 ✕에 닿아도 곧바로 지워지지는 않습니다.
        compose.onNodeWithTag(moodLogDeleteTag(first.syncId)).assertExists()
        compose.onNodeWithTag(moodTimelineTag).performScrollToNode(hasTestTag(moodLogDeleteTag(last.syncId)))
        compose.onNodeWithTag(moodLogDeleteTag(last.syncId)).performClick()
        assertNull(cleared)
        compose.onNodeWithTag(moodLogDeleteCancelTag).performClick()
        assertNull(cleared)
        compose.onNodeWithTag(moodLogDeleteTag(last.syncId)).performClick()
        compose.onNodeWithTag(moodLogDeleteConfirmTag).performClick()

        assertEquals(last.syncId, cleared)
        compose.onNodeWithTag(moodLogDeleteConfirmTag).assertDoesNotExist()
    }

    @Test
    fun theFlowShowsTimesInChronologicalOrder() {
        val first = log("first", 9, DiaryMood.GOOD)
        val last = log("last", 18, DiaryMood.TIRED)
        compose.setContent {
            TodayMoodCard(
                mood = DiaryMood.TIRED,
                logs = DayMood.logsOn(listOf(last, first), today),
                onPick = {}
            )
        }

        compose.onNodeWithText("09:00  🙂 좋음").assertIsDisplayed()
        compose.onNodeWithText("18:00  😪 지침").assertIsDisplayed()
        val firstBounds = compose.onNodeWithTag(moodLogTag(first.syncId)).fetchSemanticsNode().boundsInRoot
        val lastBounds = compose.onNodeWithTag(moodLogTag(last.syncId)).fetchSemanticsNode().boundsInRoot
        assertTrue("앞선 기록이 왼쪽이어야 합니다.", firstBounds.left < lastBounds.left)
        assertEquals(firstBounds.top, lastBounds.top, 1f)
    }

    @Test
    fun aDayWithoutTapsDoesNotReserveSpaceForAFlow() {
        compose.setContent { TodayMoodCard(mood = null, onPick = {}, onClear = {}) }

        compose.onNodeWithTag(moodTimelineTag).assertDoesNotExist()
        compose.onNodeWithText("오늘의 흐름", substring = true).assertDoesNotExist()
    }

    private fun log(syncId: String, hour: Int, mood: DiaryMood): MoodLogEntity {
        val at = today.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return MoodLogEntity(syncId = syncId, entryDate = today.toString(), mood = mood.name, createdAt = at, updatedAt = at)
    }
}
