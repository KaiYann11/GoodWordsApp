package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal const val todayRoutineTabTag = "today_tab_routine"
internal const val todayTodoTabTag = "today_tab_todo"

/**
 * 루틴과 할 일을 한 탭 안에서 나눠 보여 줍니다.
 *
 * 둘은 다른 개념입니다. 루틴은 매일 반복하며 몇 번 했는지를 세고,
 * 할 일은 한 번 끝내면 없어집니다. 그래도 "오늘 무엇을 하지"라는 질문은 같아서 함께 둡니다.
 * 하단 바에 탭을 하나 더 넣으면 글자가 잘려서 안에서 나눴습니다.
 */
@Composable
fun TodayScreen(
    routineContent: @Composable () -> Unit,
    todoContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // 화면을 돌려도 보던 쪽이 유지되어야 합니다.
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            Tab(
                selected = selected == 0,
                onClick = { selected = 0 },
                text = { Text("루틴") },
                modifier = Modifier.testTag(todayRoutineTabTag)
            )
            Tab(
                selected = selected == 1,
                onClick = { selected = 1 },
                text = { Text("할 일") },
                modifier = Modifier.testTag(todayTodoTabTag)
            )
        }
        if (selected == 0) routineContent() else todoContent()
    }
}
