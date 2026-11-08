package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.GoalTimeLogEntity
import com.codex.appgoodwords.data.GoalTimer
import com.codex.appgoodwords.data.GoalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 목표의 "쓴 시간"(사용자가 정함, 2026-11-08 — 되새기기와 따로, 직접 적기 + 타이머).
 *
 * 들인 시간은 목표 칸에만 붙고, 고르거나 적은 시간 또는 타이머로 잰 시간이 그대로 넘어가는지, 타이머가 한 번에 하나인지 봅니다.
 */
class GoalTimeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val goal = ContentItemEntity(id = 3, syncId = "g-1", type = ContentType.GOAL, title = "하프 마라톤 완주", body = "")
    private val vision = ContentItemEntity(id = 1, syncId = "v-1", type = ContentType.VISION, title = "아침을 지배하는 사람", body = "")

    private fun track(item: ContentItemEntity = goal, logs: List<GoalTimeLogEntity> = emptyList()) = GoalTrack(
        goal = item,
        reflections = emptyList(),
        routines = emptyList(),
        recentActiveDays = 0,
        quotes = emptyList(),
        timeLogs = logs,
        totalMinutes = logs.sumOf { it.minutes },
        recentMinutes = logs.sumOf { it.minutes }
    )

    private fun log(id: Long, minutes: Int, note: String = "") = GoalTimeLogEntity(
        id = id, syncId = "t-$id", goalSyncId = "g-1", minutes = minutes, note = note, loggedAt = 1_762_000_000_000L - id * 86_400_000L
    )

    private fun detail(
        track: GoalTrack = track(),
        timer: GoalTimer? = null,
        timerGoalTitle: String? = null,
        onAddTime: (Int, String) -> Unit = { _, _ -> },
        onStartTimer: () -> Unit = {},
        onStopTimer: () -> Unit = {}
    ) {
        compose.setContent {
            GoalDetailScreen(
                track = track,
                routines = emptyList(),
                onSaveText = { _, _ -> Result.success(1L) },
                onSetRoutines = { Result.success(Unit) },
                onSetPinned = { Result.success(Unit) },
                onDelete = { Result.success(Unit) },
                onReflect = { Result.success(1L) },
                onDeleteReflection = { Result.success(1) },
                onOpenQuote = {},
                onOpenRoutines = {},
                onMessage = {},
                timer = timer,
                timerGoalTitle = timerGoalTitle,
                onAddTime = { minutes, note -> onAddTime(minutes, note); Result.success(9L) },
                onStartTimer = onStartTimer,
                onStopTimer = onStopTimer
            )
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(goalDetailListTag).performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun onlyAGoalHasTimeToRecord() {
        detail(track = track(item = vision))

        // 이상향·롤모델은 들인 시간으로 따라가는 것이 아닙니다.
        compose.onNodeWithTag(goalTimeSaveTag).assertDoesNotExist()
        compose.onNodeWithTag(goalTimerStartTag).assertDoesNotExist()
    }

    @Test
    fun aQuickTimeAndWhatWasDoneAreSaved() {
        var saved: Pair<Int, String>? = null
        detail(onAddTime = { minutes, note -> saved = minutes to note })

        scrollTo(goalTimeSaveTag)
        compose.onNodeWithTag(goalTimeSaveTag).assertIsNotEnabled()
        compose.onNodeWithTag(goalTimeQuickTag(30)).performClick()
        compose.onNodeWithTag(goalTimeNoteTag).performTextInput("  스트레칭과 폼롤러 ")
        compose.onNodeWithTag(goalTimeSaveTag).performClick()
        compose.waitForIdle()

        assertEquals(30 to "스트레칭과 폼롤러", saved)
    }

    @Test
    fun typedHoursAndMinutesAreAddedUp() {
        var saved: Int? = null
        detail(onAddTime = { minutes, _ -> saved = minutes })

        scrollTo(goalTimeManualTag)
        compose.onNodeWithTag(goalTimeManualTag).performClick()
        compose.onNodeWithTag(goalTimeHoursTag).performTextInput("25")
        scrollTo(goalTimeSaveTag)
        // 하루를 넘으면 남기지 못합니다.
        compose.onNodeWithTag(goalTimeSaveTag).assertIsNotEnabled()
        compose.onNodeWithText("1분부터 24시간까지 남길 수 있습니다.").assertIsDisplayed()

        scrollTo(goalTimeHoursTag)
        compose.onNodeWithTag(goalTimeHoursTag).performTextClearance()
        compose.onNodeWithTag(goalTimeHoursTag).performTextInput("1")
        compose.onNodeWithTag(goalTimeMinutesTag).performTextInput("20")
        scrollTo(goalTimeSaveTag)
        compose.onNodeWithTag(goalTimeSaveTag).performClick()
        compose.waitForIdle()

        assertEquals(80, saved)
    }

    @Test
    fun aStoppedTimerFillsInTheMeasuredTime() {
        var saved: Int? = null
        // 42분 10초를 쟀습니다. 올려서 43분입니다.
        val timer = GoalTimer(goalSyncId = "g-1", startedAt = 1_000L, stoppedAt = 1_000L + 42 * 60_000L + 10_000L)
        detail(timer = timer, onAddTime = { minutes, _ -> saved = minutes })

        compose.onNodeWithText("잰 시간 43분", substring = true).assertIsDisplayed()
        scrollTo(goalTimeSaveTag)
        compose.onNodeWithTag(goalTimeSaveTag).performClick()
        compose.waitForIdle()

        assertEquals(43, saved)
    }

    @Test
    fun aRunningTimerShowsTheClockAndCanBeStopped() {
        var stopped = false
        val timer = GoalTimer(goalSyncId = "g-1", startedAt = System.currentTimeMillis() - 65_000L)
        detail(timer = timer, onStopTimer = { stopped = true })

        compose.onNodeWithText("재는 중", substring = true).assertIsDisplayed()
        compose.onNodeWithText("앱을 닫아도 계속 잽니다.").assertIsDisplayed()
        compose.onNodeWithTag(goalTimerStopTag).performClick()

        assertTrue(stopped)
    }

    @Test
    fun startingIsBlockedWhileAnotherGoalIsBeingTimed() {
        var started = false
        val timer = GoalTimer(goalSyncId = "g-other", startedAt = System.currentTimeMillis())
        detail(timer = timer, timerGoalTitle = "책 50권", onStartTimer = { started = true })

        compose.onNodeWithText("'책 50권'에서 재는 중입니다", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(goalTimerStartTag).assertDoesNotExist()
        assertTrue(!started)
    }

    @Test
    fun noTimerOffersToStartOne() {
        var started = false
        detail(onStartTimer = { started = true })

        compose.onNodeWithTag(goalTimerStartTag).performClick()

        assertTrue(started)
    }

    @Test
    fun recordsShowTheLatestFiveUntilOpened() {
        val logs = (1L..7L).map { log(it, minutes = 10 * it.toInt(), note = "기록 $it") }
        detail(track = track(logs = logs))

        compose.onNodeWithText("누적 4시간 40분", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(goalDetailListTag).performScrollToNode(hasText("시간 기록 7"))
        compose.onNodeWithTag(goalDetailListTag).performScrollToNode(hasTestTag(goalTimeAllTag))
        compose.onNodeWithText("기록 7").assertDoesNotExist()
        compose.onNodeWithText("시간 기록 모두 보기 (7개)").performClick()
        compose.onNodeWithTag(goalDetailListTag).performScrollToNode(hasText("기록 7"))
        compose.onNodeWithText("기록 7").assertIsDisplayed()
    }

    @Test
    fun theGoalCardShowsTimeSpentAndAMeasuredTimer() {
        val timer = GoalTimer(goalSyncId = "g-1", startedAt = 1_000L, stoppedAt = 1_000L + 30 * 60_000L)
        compose.setContent {
            GoalTabsScreen(
                tracks = listOf(track(logs = listOf(log(1, 750)))),
                onOpen = {},
                onSave = { _, _, _, _ -> Result.success(0L) },
                goalTimer = timer
            )
        }

        compose.onNodeWithTag(goalTabTag(GoalTab.GOAL)).performClick()
        compose.onNodeWithText("들인 시간 12시간 30분", substring = true).assertIsDisplayed()
        // 카드 전체가 눌리는 자리라 안쪽 줄은 합친 트리에서 따로 보이지 않습니다.
        compose.onNodeWithTag(goalCardTimerTag(goal.id), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("잰 시간 30분 · 남기기를 기다립니다").assertIsDisplayed()
    }
}
