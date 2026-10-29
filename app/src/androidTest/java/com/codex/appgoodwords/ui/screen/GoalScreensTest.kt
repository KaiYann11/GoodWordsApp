package com.codex.appgoodwords.ui.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentMemoEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.GoalRoutine
import com.codex.appgoodwords.data.GoalTrack
import com.codex.appgoodwords.data.HomeGoalLine
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.StatsCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 목표 탭 — 이상향·목표·롤모델을 적고, 되새기고, 루틴을 잇습니다.
 *
 * 할 일이나 메모와 다른 자리라는 것이 화면에서 드러나야 합니다: 적고 나면 그 기록으로 들어가 되새기는 칸이 바로 보이고,
 * 이어 둔 루틴을 실제로 밟은 날이 보이고, 롤모델에는 그 사람의 글귀가 모입니다.
 */
class GoalScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val vision = ContentItemEntity(id = 1, syncId = "v-1", type = ContentType.VISION, title = "아침을 지배하는 사람", body = "")
    private val walk = RoutineEntity(id = 7, syncId = "routine-walk", title = "아침 산책")
    private val read = RoutineEntity(id = 8, syncId = "routine-read", title = "책 10쪽")

    private fun track(
        goal: ContentItemEntity = vision,
        reflections: List<ContentMemoEntity> = emptyList(),
        routines: List<GoalRoutine> = emptyList(),
        recentActiveDays: Int = 0,
        quotes: List<ContentItemEntity> = emptyList()
    ) = GoalTrack(goal, reflections, routines, recentActiveDays, quotes)

    @Test
    fun writingAGoalOpensItRightAway() {
        var saved: Triple<Long, ContentType, String>? = null
        var opened: Long? = null
        compose.setContent {
            GoalTabsScreen(
                tracks = listOf(track()),
                onOpen = { opened = it },
                onSave = { id, type, title, _ -> saved = Triple(id, type, title); Result.success(42L) }
            )
        }

        compose.onNodeWithText("이상향 1").assertIsDisplayed()
        compose.onNodeWithTag(goalTabTag(GoalTab.GOAL)).performClick()
        compose.onNodeWithTag(goalAddTag(GoalTab.GOAL)).performClick()
        compose.onNodeWithTag(goalTitleFieldTag).performTextInput("하프 마라톤 완주")
        compose.onNodeWithTag(goalSaveTag).performClick()
        compose.waitForIdle()

        // 적고 나면 그 기록으로 들어갑니다. 되새기고 루틴을 잇는 자리가 거기라서입니다.
        assertEquals(Triple(0L, ContentType.GOAL, "하프 마라톤 완주"), saved)
        assertEquals(42L, opened)
    }

    @Test
    fun comingBackAfterWritingLeavesNoStaleDialogBehind() {
        // 앱처럼 경로마다 화면 상태를 저장하는 자리에 둡니다. 예전에는 적는 창이 열린 채로 그 기록으로 넘어가,
        // 돌아오면 창이 다시 뜨고 다음에 여는 창(롤모델)에 앞서 친 이상향 글이 들어 있었습니다.
        var route by mutableStateOf("list")
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            holder.SaveableStateProvider(route) {
                if (route == "list") {
                    GoalTabsScreen(
                        tracks = emptyList(),
                        onOpen = { route = "detail" },
                        onSave = { _, _, _, _ -> Result.success(7L) }
                    )
                } else {
                    Text("상세")
                }
            }
        }

        compose.onNodeWithTag(goalAddTag(GoalTab.VISION)).performClick()
        compose.onNodeWithTag(goalTitleFieldTag).performTextInput("Morning person")
        compose.onNodeWithTag(goalSaveTag).performClick()
        compose.waitForIdle()
        assertEquals("detail", route)

        route = "list"
        compose.waitForIdle()
        compose.onNodeWithTag(goalTitleFieldTag).assertDoesNotExist()
        compose.onNodeWithTag(goalTabTag(GoalTab.ROLE_MODEL)).performClick()
        compose.onNodeWithTag(goalAddTag(GoalTab.ROLE_MODEL)).performClick()
        compose.onNodeWithTag(goalTitleFieldTag).assert(hasText("Morning person", substring = true).not())
    }

    @Test
    fun aCardSaysWhenItWasLastRevisitedAndHowItMovedWithoutCountingMisses() {
        val memo = ContentMemoEntity(id = 3, contentItemId = 1, contentItemSyncId = "v-1", contentTitle = "", body = "물부터 마셨다",
            createdAt = LocalDate.of(2026, 10, 28).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        compose.setContent {
            GoalTabsScreen(
                tracks = listOf(track(reflections = listOf(memo), routines = listOf(GoalRoutine(walk, 9, 3)), recentActiveDays = 9)),
                onOpen = {},
                onSave = { _, _, _, _ -> Result.success(0L) }
            )
        }

        compose.onNodeWithText("되새김 1번", substring = true).assertIsDisplayed()
        compose.onNodeWithText("최근 4주에 9일 움직임", substring = true).assertIsDisplayed()
    }

    @Test
    fun reflectingAndLinkingAndPinningFromTheDetail() {
        var reflected: String? = null
        var linked: List<String>? = null
        var pinned: Boolean? = null
        compose.setContent {
            GoalDetailScreen(
                track = track(),
                routines = listOf(walk, read),
                onSaveText = { _, _ -> Result.success(1L) },
                onSetRoutines = { linked = it; Result.success(Unit) },
                onSetPinned = { pinned = it; Result.success(Unit) },
                onDelete = { Result.success(Unit) },
                onReflect = { reflected = it; Result.success(5L) },
                onDeleteReflection = { Result.success(1) },
                onOpenQuote = {},
                onOpenRoutines = {},
                onMessage = {}
            )
        }

        compose.onNodeWithText("오늘 이 모습에 가까웠던 순간이 있었나요?").assertIsDisplayed()
        compose.onNodeWithTag(goalReflectFieldTag).performTextInput("  물 한 컵으로 시작했다  ")
        compose.onNodeWithTag(goalReflectSaveTag).performClick()
        compose.waitForIdle()
        assertEquals("물 한 컵으로 시작했다", reflected)

        compose.onNodeWithTag(goalPinTag).performClick()
        compose.waitForIdle()
        assertEquals(true, pinned)

        compose.onNodeWithTag(goalDetailListTag).performScrollToNode(hasTestTag(goalLinkRoutinesTag))
        compose.onNodeWithTag(goalLinkRoutinesTag).performClick()
        compose.onNodeWithTag(goalRoutineCheckTag("routine-read")).performClick()
        compose.onNodeWithTag(goalRoutinesSaveTag).performClick()
        compose.waitForIdle()
        assertEquals(listOf("routine-read"), linked)
    }

    @Test
    fun aRoleModelGathersTheirQuotes() {
        val model = ContentItemEntity(id = 2, syncId = "m-1", type = ContentType.ROLE_MODEL, title = "James Clear", body = "작은 습관")
        val quote = ContentItemEntity(id = 9, type = ContentType.QUOTE, title = "습관", body = "습관은 복리로 쌓인다", author = "James Clear")
        var opened: ContentItemEntity? = null
        compose.setContent {
            GoalDetailScreen(
                track = track(goal = model, quotes = listOf(quote)),
                routines = emptyList(),
                onSaveText = { _, _ -> Result.success(2L) },
                onSetRoutines = { Result.success(Unit) },
                onSetPinned = { Result.success(Unit) },
                onDelete = { Result.success(Unit) },
                onReflect = { Result.success(1L) },
                onDeleteReflection = { Result.success(1) },
                onOpenQuote = { opened = it },
                onOpenRoutines = {},
                onMessage = {}
            )
        }

        compose.onNodeWithText("그라면 오늘 어떻게 했을까요?").assertIsDisplayed()
        compose.onNodeWithTag(goalDetailListTag).performScrollToNode(hasText("“습관은 복리로 쌓인다”"))
        compose.onNodeWithText("“습관은 복리로 쌓인다”").performClick()
        assertEquals(quote, opened)
    }

    @Test
    fun theHomeTodayTabStartsWithTheDirection() {
        var opened: Long? = null
        compose.setContent {
            HomeScreen(
                summary = StatsCalculator.build(events = emptyList(), items = emptyList(), routineChecks = emptyList(), today = LocalDate.now()),
                notes = emptyList(),
                routines = emptyList(),
                checks = emptyList(),
                quote = HomeQuote(item = null, hasCandidates = false),
                goalLine = HomeGoalLine(vision = vision, other = null),
                onOpenGoal = { opened = it }
            )
        }

        compose.onNodeWithTag(homeListTag).performScrollToNode(hasTestTag(homeGoalLineTag))
        compose.onNodeWithText("되고 싶은 나").assertIsDisplayed()
        compose.onNodeWithText("아침을 지배하는 사람").performClick()
        assertTrue(opened == 1L)
    }

    @Test
    fun nothingWrittenLeavesNoHomeLine() {
        compose.setContent {
            HomeScreen(
                summary = StatsCalculator.build(events = emptyList(), items = emptyList(), routineChecks = emptyList(), today = LocalDate.now()),
                notes = emptyList(),
                routines = emptyList(),
                checks = emptyList(),
                quote = HomeQuote(item = null, hasCandidates = false),
                goalLine = null
            )
        }

        compose.onNodeWithTag(homeGoalLineTag).assertDoesNotExist()
    }
}
