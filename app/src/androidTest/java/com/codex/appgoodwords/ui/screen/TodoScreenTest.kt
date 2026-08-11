package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.TodoDraft
import com.codex.appgoodwords.data.TodoEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 못 끝낸 지난 일이 어떻게 보이는지 확인합니다.
 *
 * 지난 일이 오늘 목록에 그냥 섞이면 며칠만 밀려도 목록을 읽을 수 없게 되고,
 * 아예 안 보이면 밀린 일을 영영 놓칩니다. 그래서 따로 모아 보여 줍니다.
 */
class TodoScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 8, 12)

    @Test
    fun anUnfinishedTaskFromAnEarlierDayShowsUnderOverdue() {
        compose.setContent {
            todoScreen(
                todos = listOf(
                    todo(id = 1, title = "밀린 일", dueDate = "2026-08-10"),
                    todo(id = 2, title = "오늘 일", dueDate = "2026-08-12")
                )
            )
        }

        compose.onNodeWithText("지난 일 1개").assertIsDisplayed()
        compose.onNodeWithText("밀린 일").assertIsDisplayed()
        compose.onNodeWithText("오늘 일").assertIsDisplayed()
    }

    @Test
    fun aFinishedTaskFromAnEarlierDayIsNotOverdue() {
        compose.setContent {
            todoScreen(
                todos = listOf(todo(id = 1, title = "끝낸 일", dueDate = "2026-08-10", doneAt = 5_000L))
            )
        }

        // 끝낸 일까지 지난 일로 세면 다 해 놓고도 밀린 것처럼 보인다.
        compose.onNodeWithText("지난 일 1개").assertDoesNotExist()
    }

    @Test
    fun theRemainingCountIgnoresFinishedTasks() {
        compose.setContent {
            todoScreen(
                todos = listOf(
                    todo(id = 1, title = "남은 일", dueDate = "2026-08-12"),
                    todo(id = 2, title = "끝낸 일", dueDate = "2026-08-12", doneAt = 5_000L)
                )
            )
        }

        compose.onNodeWithText("오늘 남은 일 1개").assertIsDisplayed()
    }

    @Test
    fun addingUsesTodayAsTheDueDate() {
        var saved: TodoDraft? = null
        compose.setContent { todoScreen(todos = emptyList(), onSaveTodo = { saved = it }) }

        compose.onNodeWithTag(todoInputTag).performTextInput("우체국 가기")
        compose.onNodeWithTag(todoAddButtonTag).performClick()

        assertEquals("우체국 가기", saved?.title)
        assertEquals(today, saved?.dueDate)
    }

    @Test
    fun anEmptyTitleCannotBeAdded() {
        compose.setContent { todoScreen(todos = emptyList()) }

        compose.onNodeWithTag(todoAddButtonTag).assertIsNotEnabled()
    }

    @Test
    fun aMissingExactAlarmPermissionIsVisible() {
        compose.setContent { todoScreen(todos = emptyList(), canScheduleExactAlarms = false) }

        // 권한이 없으면 알람이 늦게 울린다. 조용히 넘어가면 사용자는 이유를 알 수 없다.
        compose.onNodeWithText("정확한 알람 권한이 없어 알람이 늦게 울릴 수 있습니다.").assertIsDisplayed()
    }

    @Test
    fun nothingIsSaidAboutAlarmsWhenThePermissionIsThere() {
        compose.setContent { todoScreen(todos = emptyList(), canScheduleExactAlarms = true) }

        compose.onNodeWithText("정확한 알람 권한이 없어 알람이 늦게 울릴 수 있습니다.").assertDoesNotExist()
    }

    private fun todo(
        id: Long,
        title: String,
        dueDate: String,
        doneAt: Long? = null
    ) = TodoEntity(
        id = id,
        syncId = "todo-$id",
        title = title,
        dueDate = dueDate,
        doneAt = doneAt
    )

    @androidx.compose.runtime.Composable
    private fun todoScreen(
        todos: List<TodoEntity>,
        canScheduleExactAlarms: Boolean = true,
        onSaveTodo: (TodoDraft) -> Unit = {}
    ) {
        TodoScreen(
            todos = todos,
            today = today,
            canScheduleExactAlarms = canScheduleExactAlarms,
            onSaveTodo = onSaveTodo,
            onToggleDone = {},
            onDeleteTodo = {},
            onOpenExactAlarmSettings = {}
        )
    }
}
