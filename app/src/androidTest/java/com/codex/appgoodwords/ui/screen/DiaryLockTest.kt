package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.DiaryEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 일기만 따로 거는 잠금.
 *
 * 앱 잠금과 별개입니다. 폰을 잠깐 건네줄 때 나머지는 보여 줘도 일기는 아닐 수 있습니다.
 *
 * 가리는 것은 **본문**입니다. 날짜와 기분까지 가리면 목록이 아무 뜻 없는 줄이 되어
 * 무엇을 열지 고를 수조차 없습니다.
 */
class DiaryLockTest {
    @get:Rule
    val compose = createComposeRule()

    private val diary = DiaryEntity(
        id = 1,
        syncId = "d1",
        entryDate = "2026-08-26",
        body = "아무에게도 하지 못한 말이 있다.",
        mood = "SAD"
    )

    @Test
    fun aLockedDiaryDoesNotOpenOnItsOwn() {
        compose.setContent { diaryScreen(locked = true, onRequestUnlock = { }) }

        compose.onNodeWithTag(diaryCardTag(diary.id)).performClick()

        // 풀어 주지 않았으므로 본문이 나오면 안 됩니다.
        compose.onNodeWithText("아무에게도 하지 못한 말").assertDoesNotExist()
    }

    @Test
    fun itOpensOnceUnlocked() {
        compose.setContent { diaryScreen(locked = true, onRequestUnlock = { it() }) }

        compose.onNodeWithTag(diaryCardTag(diary.id)).performClick()

        // 펼치면 제목 자리와 본문 두 곳에 나옵니다. 하나라도 보이면 열린 것입니다.
        assertBodyShown()
    }

    @Test
    fun theFirstLineOfTheBodyIsHiddenToo() {
        // 제목이 비면 본문 첫 줄이 제목 자리에 나옵니다. 잠긴 동안에는 그것도 본문입니다.
        compose.setContent { diaryScreen(locked = true, onRequestUnlock = { }) }

        compose.onNodeWithText("아무에게도 하지 못한 말", substring = true).assertDoesNotExist()
    }

    @Test
    fun theDateAndMoodStayVisible() {
        // 여기까지 가리면 목록이 아무 뜻 없는 줄이 되어 무엇을 열지 고를 수조차 없습니다.
        compose.setContent { diaryScreen(locked = true, onRequestUnlock = { }) }

        compose.onNodeWithText("2026-08-26", substring = true).assertExists()
        compose.onNodeWithText("잠김", substring = true).assertExists()
    }

    @Test
    fun itIsAskedOnlyOncePerVisit() {
        // 하나를 열 때마다 물으면 어제와 오늘을 견주는 것만으로도 지문을 여러 번 대야 합니다.
        var asked = 0
        compose.setContent {
            diaryScreen(locked = true, onRequestUnlock = { asked += 1; it() })
        }

        compose.onNodeWithTag(diaryCardTag(diary.id)).performClick()
        compose.onNodeWithTag(diaryCardTag(diary.id)).performClick()
        compose.onNodeWithTag(diaryCardTag(diary.id)).performClick()

        assertEquals(1, asked)
    }

    @Test
    fun nothingIsAskedWhenTheLockIsOff() {
        var asked = 0
        compose.setContent {
            diaryScreen(locked = false, onRequestUnlock = { asked += 1; it() })
        }

        compose.onNodeWithTag(diaryCardTag(diary.id)).performClick()

        assertEquals(0, asked)
        assertBodyShown()
    }

    private fun assertBodyShown() {
        val shown = compose
            .onAllNodesWithText("아무에게도 하지 못한 말", substring = true)
            .fetchSemanticsNodes()
        assertTrue("본문이 보이지 않습니다.", shown.isNotEmpty())
    }

    @androidx.compose.runtime.Composable
    private fun diaryScreen(locked: Boolean, onRequestUnlock: (() -> Unit) -> Unit) {
        DiaryScreen(
            diaries = listOf(diary),
            today = LocalDate.of(2026, 8, 26),
            onSaveDiary = {},
            onDeleteDiary = {},
            locked = locked,
            onRequestUnlock = onRequestUnlock
        )
    }
}
