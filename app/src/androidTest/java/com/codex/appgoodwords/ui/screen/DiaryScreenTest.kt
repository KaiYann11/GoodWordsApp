package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.codex.appgoodwords.data.DiaryDraft
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.DiaryWeather
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 일기의 날씨와 기분이 화면에서 실제로 골라지고 다시 보이는지 확인합니다.
 *
 * 저장은 되는데 화면에 안 보이면 사용자는 고른 적이 없다고 생각합니다.
 */
class DiaryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 8, 17)

    @Test
    fun choosingWeatherAndMoodIsSaved() {
        var saved: DiaryDraft? = null
        compose.setContent { diaryScreen(diaries = emptyList(), onSaveDiary = { saved = it }) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        compose.onNodeWithTag(diaryWeatherChipTag(DiaryWeather.RAIN)).performScrollTo().performClick()
        compose.onNodeWithTag(diaryMoodChipTag(DiaryMood.GOOD)).performScrollTo().performClick()
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        assertEquals(DiaryWeather.RAIN, saved?.weatherOption)
        assertEquals(DiaryMood.GOOD, saved?.moodOption)
    }

    @Test
    fun tappingTheSameChipAgainUnchoosesIt() {
        var saved: DiaryDraft? = null
        compose.setContent { diaryScreen(diaries = emptyList(), onSaveDiary = { saved = it }) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        // 잘못 고른 것을 되돌릴 방법이 이것뿐입니다. 안 되면 한 번 누른 값이 영영 남습니다.
        compose.onNodeWithTag(diaryWeatherChipTag(DiaryWeather.SNOW)).performScrollTo().performClick()
        compose.onNodeWithTag(diaryWeatherChipTag(DiaryWeather.SNOW)).performClick()
        compose.onNodeWithTag(diaryMoodChipTag(DiaryMood.GREAT)).performScrollTo().performClick()
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        assertEquals(null, saved?.weatherOption)
        assertEquals(DiaryMood.GREAT, saved?.moodOption)
    }

    @Test
    fun aMoodAloneCanBeSaved() {
        compose.setContent { diaryScreen(diaries = emptyList()) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        // 글 쓸 기운이 없는 날에도 기분은 남길 수 있어야 합니다.
        compose.onNodeWithTag(diarySaveButtonTag).assertIsNotEnabled()
        compose.onNodeWithTag(diaryMoodChipTag(DiaryMood.TIRED)).performScrollTo().performClick()
        compose.onNodeWithTag(diarySaveButtonTag).performClick()
    }

    @Test
    fun theListShowsWhatWasChosen() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(
                        id = 1,
                        syncId = "diary-1",
                        entryDate = "2026-08-17",
                        body = "비 오는 날",
                        weather = DiaryWeather.RAIN.name,
                        mood = DiaryMood.GOOD.name
                    )
                )
            )
        }

        compose.onNodeWithText("2026-08-17  ·  🌧️ 비  ·  🙂 좋음").assertIsDisplayed()
    }

    @Test
    fun aDiaryWithoutAChoiceShowsOnlyTheDate() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(id = 1, syncId = "diary-1", entryDate = "2026-08-17", body = "그냥 하루")
                )
            )
        }

        // 날씨 기능이 생기기 전에 쓴 일기가 이 모습입니다. 가운뎃점만 덩그러니 남으면 안 됩니다.
        compose.onNodeWithText("2026-08-17").assertIsDisplayed()
    }

    @Test
    fun editingKeepsTheChoiceAlreadyMade() {
        var saved: DiaryDraft? = null
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(
                        id = 1,
                        syncId = "diary-1",
                        entryDate = "2026-08-17",
                        body = "고칠 일기",
                        weather = DiaryWeather.SUNNY.name,
                        mood = DiaryMood.ANGRY.name
                    )
                ),
                onSaveDiary = { saved = it }
            )
        }

        compose.onNodeWithText("수정").performClick()
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        // 본문만 고치려고 열었다가 날씨가 지워지면 사용자는 알아채지 못합니다.
        assertEquals(DiaryWeather.SUNNY, saved?.weatherOption)
        assertEquals(DiaryMood.ANGRY, saved?.moodOption)
    }

    @androidx.compose.runtime.Composable
    private fun diaryScreen(
        diaries: List<DiaryEntity>,
        onSaveDiary: (DiaryDraft) -> Unit = {}
    ) {
        DiaryScreen(
            diaries = diaries,
            today = today,
            onSaveDiary = onSaveDiary,
            onDeleteDiary = {}
        )
    }
}
