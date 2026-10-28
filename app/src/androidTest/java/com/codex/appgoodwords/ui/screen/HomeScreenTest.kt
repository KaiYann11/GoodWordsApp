package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.DailyCoachTip
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.FeedbackKind
import com.codex.appgoodwords.data.FeedbackNote
import com.codex.appgoodwords.data.StatsCalculator
import com.codex.appgoodwords.data.StatsSummary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 홈.
 *
 * 맨 위는 오늘의 글귀이고(위젯·알림과 같은 것), 그 아래는 오늘 · 한마디 · 돌아보기 세 칸을 좌우로 넘깁니다.
 * 한 장에 카드 열 개가 이어져 난잡했던 것을 나눴습니다(사용자가 고름, 2026-10-28).
 */
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val emptySummary: StatsSummary = StatsCalculator.build(
        events = emptyList(),
        items = emptyList(),
        routineChecks = emptyList(),
        today = LocalDate.now()
    )

    private val quoteItem = ContentItemEntity(
        id = 7L,
        type = ContentType.QUOTE,
        title = "집중",
        body = "중요한 일을 자주 못 하는 이유는 시간이 없어서가 아니라 우선순위가 밀려 있기 때문이다.",
        author = "어느 책"
    )

    private val threeSteps = DailyProgress(
        steps = DailyStep.DEFAULTS,
        doneSteps = emptySet(),
        streakDays = 0,
        bestStreakDays = 0
    )

    private val coachState = DailyCoachState(
        date = LocalDate.now(),
        tip = DailyCoachTip(message = "물 한 컵으로 하루를 여셨네요.", createdAt = 1L)
    )

    @Test
    fun theQuoteIsTheFirstThingOnHome() {
        compose.setContent { homeScreen(quote = HomeQuote(item = quoteItem, hasCandidates = true)) }

        compose.onNodeWithTag(homeQuoteCardTag).assertIsDisplayed()
        compose.onNodeWithText(quoteItem.body).assertIsDisplayed()
        compose.onNodeWithText("— 어느 책").assertIsDisplayed()
        // 제목이 본문의 앞머리가 아니면 작게 얹습니다.
        compose.onNodeWithText("집중").assertIsDisplayed()
    }

    @Test
    fun readingNextAndOpeningAreOneTapAway() {
        var confirmed: ContentItemEntity? = null
        var nexts = 0
        var opened: ContentItemEntity? = null
        compose.setContent {
            homeScreen(
                quote = HomeQuote(item = quoteItem, hasCandidates = true),
                onConfirmQuote = { confirmed = it },
                onNextQuote = { nexts += 1 },
                onOpenQuote = { opened = it }
            )
        }

        compose.onNodeWithTag(homeQuoteConfirmTag).performClick()
        compose.onNodeWithTag(homeQuoteNextTag).performClick()
        compose.onNodeWithTag(homeQuoteTextTag).performClick()

        assertEquals(quoteItem, confirmed)
        assertEquals(1, nexts)
        assertEquals(quoteItem, opened)
    }

    @Test
    fun aQuoteReadTodaySaysSo() {
        compose.setContent {
            homeScreen(quote = HomeQuote(item = quoteItem, hasCandidates = true), quoteConfirmedToday = true)
        }

        compose.onNodeWithText("오늘 읽음").assertIsDisplayed()
        compose.onNodeWithText("읽었어요").assertIsDisplayed()
    }

    @Test
    fun anEmptyLibraryInvitesToAddAQuote() {
        var added = false
        compose.setContent {
            homeScreen(quote = HomeQuote(item = null, hasCandidates = false), onAddQuote = { added = true })
        }

        compose.onNodeWithTag(homeQuoteAddTag).assertIsDisplayed().performClick()
        assertTrue(added)
        compose.onNodeWithTag(homeQuoteConfirmTag).assertDoesNotExist()
    }

    @Test
    fun quotesHiddenByTheCategoryAreNotMistakenForAnEmptyLibrary() {
        // 담아 둔 글귀가 있는데 "담아 보세요"라고 하면 틀린 말입니다. 알림에 보낼 카테고리를 짚습니다.
        compose.setContent {
            homeScreen(quote = HomeQuote(item = null, hasCandidates = false, filteredOut = true))
        }

        compose.onNodeWithTag(homeQuoteAddTag).assertDoesNotExist()
        compose.onNode(hasText("카테고리", substring = true)).assertIsDisplayed()
    }

    @Test
    fun whileReadingNothingIsClaimed() {
        // 아직 읽는 중에 "글귀를 담아 보세요"가 잠깐 비치면, 글귀가 있는 사람도 빈 보관함으로 압니다.
        compose.setContent { homeScreen(quote = null) }

        compose.onNodeWithTag(homeQuoteCardTag).assertExists()
        compose.onNodeWithTag(homeQuoteAddTag).assertDoesNotExist()
        compose.onNodeWithTag(homeQuoteTextTag).assertDoesNotExist()
    }

    @Test
    fun todayIsTheFirstTabAndTheCoachTabOnlyExistsWhenItIsOn() {
        compose.setContent { homeScreen(dailyLoop = threeSteps) }

        compose.onNodeWithTag(homeTabTag(HomeTab.TODAY)).assertIsSelected()
        compose.onNodeWithTag(dailyLoopCardTag).assertExists()
        compose.onNodeWithTag(todayMoodCardTag).assertExists()
        // 끈 사람에게 켜 보라는 칸을 매일 띄우지 않습니다.
        compose.onNodeWithTag(homeTabTag(HomeTab.COACH)).assertDoesNotExist()
        compose.onNodeWithTag(homeTabTag(HomeTab.LOOK_BACK)).assertExists()
    }

    @Test
    fun theTabsSlideSideways() {
        compose.setContent { homeScreen(dailyLoop = threeSteps, coach = coachState) }

        compose.onNodeWithTag(homePagerTag).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithTag(homeTabTag(HomeTab.COACH)).assertIsSelected()
        compose.onNodeWithTag(dailyCoachCardTag).assertExists()
        // 넘긴 칸만 둡니다. 오늘 칸의 카드는 화면에서 빠집니다.
        compose.onNodeWithTag(dailyLoopCardTag).assertDoesNotExist()
    }

    @Test
    fun tappingATabNameOpensIt() {
        compose.setContent { homeScreen(notes = emptyList()) }

        compose.onNodeWithTag(homeTabTag(HomeTab.LOOK_BACK)).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(homeTabTag(HomeTab.LOOK_BACK)).assertIsSelected()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(feedbackCardTag))
        compose.onNodeWithTag(feedbackCardTag).assertIsDisplayed()
    }

    @Test
    fun aNewWordPutsADotOnTheCoachTab() {
        compose.setContent { homeScreen(coach = coachState.copy(tipSeen = false)) }

        compose.onNodeWithTag(homeCoachDotTag, useUnmergedTree = true).assertExists()
    }

    @Test
    fun aWordAlreadySeenLeavesNoDot() {
        compose.setContent { homeScreen(coach = coachState) }

        compose.onNodeWithTag(homeCoachDotTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun overallAndRoutineStatisticsAreOnTheLookBackTab() {
        compose.setContent { homeScreen() }

        compose.onNodeWithTag(homeTabTag(HomeTab.LOOK_BACK)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("통계").assertDoesNotExist()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(homeInsightsToggleTag))
        compose.onNodeWithTag(homeInsightsToggleTag).performClick()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasText("통계"))
        compose.onNodeWithText("통계").assertIsDisplayed()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasText("달성률 - (0/0)"))
        compose.onNodeWithText("달성률 - (0/0)").assertIsDisplayed()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(homeInsightsToggleTag))
        compose.onNodeWithTag(homeInsightsToggleTag).performClick()
        compose.onNodeWithText("통계").assertDoesNotExist()
    }

    @Test
    fun theFeedbackLinesAreShownAsWritten() {
        compose.setContent {
            homeScreen(
                notes = listOf(
                    FeedbackNote("5일째 이어 가고 있습니다.", FeedbackKind.STREAK),
                    FeedbackNote("'물 한 컵'은 이레 내내 지켰습니다.", FeedbackKind.PRAISE)
                )
            )
        }

        compose.onNodeWithTag(homeTabTag(HomeTab.LOOK_BACK)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(feedbackCardTag))
        compose.onNodeWithTag(feedbackCardTag).assertIsDisplayed()
        compose.onNodeWithText("5일째 이어 가고 있습니다.").assertIsDisplayed()
        compose.onNodeWithText("'물 한 컵'은 이레 내내 지켰습니다.").assertIsDisplayed()
    }

    @Test
    fun anEmptyFeedbackCardStillSaysSomething() {
        // 카드만 덩그러니 비어 있으면 고장인지 비어 있는 것인지 알 수 없습니다.
        compose.setContent { homeScreen(notes = emptyList()) }

        compose.onNodeWithTag(homeTabTag(HomeTab.LOOK_BACK)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(feedbackCardTag))
        compose.onNodeWithText("기록이 쌓이면 여기에 짚어 드릴 말이 생깁니다.").assertIsDisplayed()
    }

    @Test
    fun theQuoteStepBringsTheQuoteIntoViewInsteadOfLeaving() {
        // 글귀 읽기는 홈 맨 위에 있습니다. 보관함으로 데려가면 바로 위에 있는 글귀를 두고 떠나게 됩니다.
        var opened: DailyStep? = null
        compose.setContent {
            Box(modifier = Modifier.height(360.dp)) {
                homeScreen(
                    quote = HomeQuote(item = quoteItem, hasCandidates = true),
                    dailyLoop = threeSteps,
                    onOpenStep = { opened = it }
                )
            }
        }
        val listTop = compose.onNodeWithTag(homeListTag).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(dailyLoopStepTag(DailyStep.QUOTE)))
        // 글귀 카드는 위로 굴러 나가 있습니다(목록에서 버려졌거나 위쪽 바깥).
        assertTrue((topOf(homeQuoteCardTag) ?: Float.NEGATIVE_INFINITY) < listTop - 1f)

        compose.onNodeWithTag(dailyLoopStepTag(DailyStep.QUOTE)).performClick()
        compose.waitForIdle()

        // 글귀 카드가 목록 맨 위로 올라왔습니다.
        val quoteTop = topOf(homeQuoteCardTag)
        assertTrue("글귀 카드가 올라오지 않았습니다: $quoteTop vs $listTop", quoteTop != null && kotlin.math.abs(quoteTop - listTop) < 4f)
        assertNull(opened)
    }

    @Test
    fun theOtherStepsStillTakeYouThere() {
        var opened: DailyStep? = null
        compose.setContent { homeScreen(dailyLoop = threeSteps, onOpenStep = { opened = it }) }

        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(dailyLoopStepTag(DailyStep.ROUTINE)))
        compose.onNodeWithTag(dailyLoopStepTag(DailyStep.ROUTINE)).performClick()

        assertEquals(DailyStep.ROUTINE, opened)
    }

    @Test
    fun theCaptureButtonOpensTheCaptureField() {
        // 왼쪽으로 밀면 열리던 칸입니다. 밀기는 이제 칸 넘기기라 단추가 그 자리를 맡습니다.
        var opened = false
        compose.setContent { homeScreen(onOpenCapture = { opened = true }) }

        compose.onNodeWithTag(homeCaptureButtonTag).assertIsDisplayed().performClick()

        assertTrue(opened)
    }

    @Test
    fun comingForTheWordOpensTheCoachTab() {
        // 한마디는 글귀 아래 옆 칸이라 홈만 열면 보이지 않습니다. 알림이나 "새 한마디 · 보기"를 눌러 왔으면 그 칸을 엽니다.
        var focusRequested by mutableStateOf(false)
        var focused = 0
        compose.setContent {
            Box(modifier = Modifier.height(300.dp)) {
                homeScreen(
                    quote = HomeQuote(item = quoteItem, hasCandidates = true),
                    dailyLoop = threeSteps,
                    coach = coachState,
                    coachFocusRequested = focusRequested,
                    onCoachFocused = {
                        focusRequested = false
                        focused += 1
                    }
                )
            }
        }
        assertFalse(isOnScreen(dailyCoachCardTag))

        focusRequested = true
        compose.waitForIdle()

        assertTrue(isOnScreen(dailyCoachCardTag))
        compose.onNodeWithTag(homeTabTag(HomeTab.COACH)).assertIsSelected()
        // 한 번 열고 거둡니다. 남아 있으면 홈에 돌아올 때마다 한마디 칸으로 끌려갑니다.
        assertEquals(1, focused)
        assertFalse(focusRequested)
    }

    private fun topOf(tag: String): Float? =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top

    private fun isOnScreen(tag: String): Boolean =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() &&
            runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

    @androidx.compose.runtime.Composable
    private fun homeScreen(
        notes: List<FeedbackNote> = emptyList(),
        quote: HomeQuote? = HomeQuote(item = null, hasCandidates = false),
        quoteConfirmedToday: Boolean = false,
        onConfirmQuote: (ContentItemEntity) -> Unit = {},
        onNextQuote: () -> Unit = {},
        onOpenQuote: (ContentItemEntity) -> Unit = {},
        onAddQuote: () -> Unit = {},
        dailyLoop: DailyProgress? = null,
        onOpenStep: (DailyStep) -> Unit = {},
        onOpenCapture: () -> Unit = {},
        coach: DailyCoachState? = null,
        coachFocusRequested: Boolean = false,
        onCoachFocused: () -> Unit = {}
    ) {
        HomeScreen(
            summary = emptySummary,
            notes = notes,
            routines = emptyList(),
            checks = emptyList(),
            quote = quote,
            quoteConfirmedToday = quoteConfirmedToday,
            onConfirmQuote = onConfirmQuote,
            onNextQuote = onNextQuote,
            onOpenQuote = onOpenQuote,
            onAddQuote = onAddQuote,
            dailyLoop = dailyLoop,
            onOpenStep = onOpenStep,
            onOpenCapture = onOpenCapture,
            coach = coach,
            coachFocusRequested = coachFocusRequested,
            onCoachFocused = onCoachFocused
        )
    }
}
