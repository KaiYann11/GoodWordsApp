package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.DiaryDraft
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.DiaryKind
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

        compose.onNodeWithTag(diaryCardTag(1)).performClick()
        compose.onNodeWithText("수정").performClick()
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        // 본문만 고치려고 열었다가 날씨가 지워지면 사용자는 알아채지 못합니다.
        assertEquals(DiaryWeather.SUNNY, saved?.weatherOption)
        assertEquals(DiaryMood.ANGRY, saved?.moodOption)
    }

    @Test
    fun choosingGratitudeAsksItsQuestions() {
        var saved: DiaryDraft? = null
        compose.setContent { diaryScreen(diaries = emptyList(), onSaveDiary = { saved = it }) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.GRATITUDE)).performScrollTo().performClick()
        // 물음이 보여야 무엇을 적을지 알 수 있습니다. 칸만 셋 나오면 자유 일기와 다를 바 없습니다.
        compose.onNodeWithTag(diaryAnswerTag(0)).performScrollTo()
        compose.onNodeWithText(DiaryKind.GRATITUDE.prompts.first()).assertIsDisplayed()
        compose.onNodeWithTag(diaryAnswerTag(0)).performTextInput("따뜻한 커피")
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        assertEquals(DiaryKind.GRATITUDE, saved?.kindOption)
        assertEquals("따뜻한 커피", saved?.answers?.firstOrNull())
    }

    @Test
    fun anAnswerAloneCanBeSaved() {
        compose.setContent { diaryScreen(diaries = emptyList()) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.REFLECTION)).performScrollTo().performClick()
        // 본문 없이 답만 적는 날이 흔합니다. 여기서 막히면 물음이 있어도 저장할 수 없습니다.
        compose.onNodeWithTag(diarySaveButtonTag).assertIsNotEnabled()
        compose.onNodeWithTag(diaryAnswerTag(1)).performScrollTo().performTextInput("늦게 잔 것")
        compose.onNodeWithTag(diarySaveButtonTag).performClick()
    }

    @Test
    fun theLastAnswerStaysWithItsOwnQuestion() {
        var saved: DiaryDraft? = null
        compose.setContent { diaryScreen(diaries = emptyList(), onSaveDiary = { saved = it }) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.GRATITUDE)).performScrollTo().performClick()
        compose.onNodeWithTag(diaryAnswerTag(2)).performScrollTo().performTextInput("비 그친 하늘")
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        // 앞의 빈칸이 사라지면 마지막 답이 첫 물음의 답이 됩니다.
        assertEquals(listOf("", "", "비 그친 하늘"), saved?.answers)
    }

    @Test
    fun switchingKindKeepsWhatWasWritten() {
        var saved: DiaryDraft? = null
        compose.setContent { diaryScreen(diaries = emptyList(), onSaveDiary = { saved = it }) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.GRATITUDE)).performScrollTo().performClick()
        compose.onNodeWithTag(diaryAnswerTag(0)).performScrollTo().performTextInput("커피")
        // 잘못 눌러 종류가 바뀌었을 때 적어 둔 답이 날아가면 되돌릴 방법이 없습니다.
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.REFLECTION)).performScrollTo().performClick()
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.GRATITUDE)).performClick()
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        assertEquals("커피", saved?.answers?.firstOrNull())
    }

    @Test
    fun theListShowsTheQuestionWithItsAnswer() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(
                        id = 1,
                        syncId = "diary-1",
                        entryDate = "2026-08-17",
                        // 제목을 비워 두면 첫 답이 제목 자리에도 나와 같은 글이 두 번 잡힙니다.
                        title = "오늘",
                        kind = DiaryKind.GRATITUDE.name,
                        answers = listOf("", "", "비 그친 하늘")
                    )
                )
            )
        }
        compose.onNodeWithTag(diaryCardTag(1)).performClick()
        // 답만 있으면 무엇에 답한 것인지 알 수 없습니다.
        compose.onNodeWithText(DiaryKind.GRATITUDE.prompts[2]).assertIsDisplayed()
        compose.onNodeWithText("비 그친 하늘").assertIsDisplayed()
    }

    @Test
    fun editingAGuidedDiaryKeepsItsAnswers() {
        var saved: DiaryDraft? = null
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(
                        id = 1,
                        syncId = "diary-1",
                        entryDate = "2026-08-17",
                        kind = DiaryKind.REFLECTION.name,
                        answers = listOf("일찍 일어남", "", "물 마시기")
                    )
                ),
                onSaveDiary = { saved = it }
            )
        }

        compose.onNodeWithTag(diaryCardTag(1)).performClick()
        compose.onNodeWithText("수정").performClick()
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        assertEquals(DiaryKind.REFLECTION, saved?.kindOption)
        assertEquals(listOf("일찍 일어남", "", "물 마시기"), saved?.answers)
    }

    @Test
    fun theListShowsOnlyTitlesUntilOneIsTapped() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(
                        id = 1,
                        syncId = "diary-1",
                        entryDate = "2026-08-17",
                        title = "바다에 다녀왔다",
                        body = "오래 걸었고 마음이 놓였다."
                    )
                )
            )
        }

        // 일기는 길어서 다 펼쳐 두면 어제 것을 보려고도 한참 굴려야 합니다.
        compose.onNodeWithText("바다에 다녀왔다").assertIsDisplayed()
        compose.onNodeWithText("오래 걸었고 마음이 놓였다.").assertDoesNotExist()

        compose.onNodeWithTag(diaryCardTag(1)).performClick()

        compose.onNodeWithText("오래 걸었고 마음이 놓였다.").assertIsDisplayed()
    }

    @Test
    fun tappingAgainFoldsItBack() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(id = 1, syncId = "diary-1", entryDate = "2026-08-17", title = "제목", body = "본문")
                )
            )
        }

        compose.onNodeWithTag(diaryCardTag(1)).performClick()
        compose.onNodeWithText("본문").assertIsDisplayed()
        compose.onNodeWithTag(diaryCardTag(1)).performClick()

        compose.onNodeWithText("본문").assertDoesNotExist()
    }

    @Test
    fun editAndDeleteAppearOnlyWhenOpened() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(id = 1, syncId = "diary-1", entryDate = "2026-08-17", title = "제목", body = "본문")
                )
            )
        }

        // 접힌 목록에 버튼이 줄지어 있으면 훑어보다가 잘못 누르기 쉽습니다.
        compose.onNodeWithText("수정").assertDoesNotExist()
        compose.onNodeWithTag(diaryCardTag(1)).performClick()
        compose.onNodeWithText("수정").assertIsDisplayed()
    }

    @Test
    fun aFoldedDiarySaysWhatIsInsideIt() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(
                        id = 1,
                        syncId = "diary-1",
                        entryDate = "2026-08-17",
                        title = "제목",
                        imageUris = listOf("content://a", "content://b")
                    )
                )
            )
        }

        // 접혀 있어도 첨부가 있다는 것은 알려 줘야 열어 볼지 정할 수 있습니다.
        compose.onNodeWithText("2026-08-17  ·  사진 2").assertIsDisplayed()
    }

    @Test
    fun theDiaryFoundBySearchOpensItself() {
        compose.setContent {
            diaryScreen(
                diaries = listOf(
                    DiaryEntity(
                        id = 7,
                        syncId = "diary-7",
                        entryDate = "2026-08-17",
                        title = "찾던 일기",
                        body = "여기에 찾던 말이 있다"
                    )
                ),
                focusId = 7
            )
        }

        // 데려다 놓고 접어 두면 찾던 말을 못 보여 주는 셈입니다.
        compose.onNodeWithText("여기에 찾던 말이 있다").assertIsDisplayed()
    }

    @Test
    fun aGuidedDiaryAsksNoWeatherOrMood() {
        compose.setContent { diaryScreen(diaries = emptyList()) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        compose.onNodeWithTag(diaryMoodChipTag(DiaryMood.GOOD)).assertExists()
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.GRATITUDE)).performScrollTo().performClick()

        // 답하는 자리에 고를 것이 늘어날수록 손이 무거워집니다.
        compose.onNodeWithTag(diaryMoodChipTag(DiaryMood.GOOD)).assertDoesNotExist()
        compose.onNodeWithTag(diaryWeatherChipTag(DiaryWeather.RAIN)).assertDoesNotExist()
    }

    @Test
    fun aMoodChosenBeforeSwitchingIsNotSavedButNotLost() {
        var saved: DiaryDraft? = null
        compose.setContent { diaryScreen(diaries = emptyList(), onSaveDiary = { saved = it }) }

        compose.onNodeWithTag(diaryWriteButtonTag).performClick()
        compose.onNodeWithTag(diaryMoodChipTag(DiaryMood.GOOD)).performScrollTo().performClick()
        compose.onNodeWithTag(diaryKindChipTag(DiaryKind.GRATITUDE)).performScrollTo().performClick()
        compose.onNodeWithTag(diaryAnswerTag(0)).performScrollTo().performTextInput("커피")
        compose.onNodeWithTag(diarySaveButtonTag).performClick()

        // 화면에 없는 값을 저장하면 돌아보기의 기분 집계에 안 보이는 기분이 섞입니다.
        assertEquals("", saved?.effectiveMood)
        // 고른 값 자체는 남아 있어야 자유로 되돌렸을 때 다시 보입니다.
        assertEquals(DiaryMood.GOOD.name, saved?.mood)
    }

    @androidx.compose.runtime.Composable
    private fun diaryScreen(
        diaries: List<DiaryEntity>,
        onSaveDiary: (DiaryDraft) -> Unit = {},
        focusId: Long? = null
    ) {
        DiaryScreen(
            diaries = diaries,
            today = today,
            onSaveDiary = onSaveDiary,
            onDeleteDiary = {},
            focusId = focusId
        )
    }
}
