package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal const val routineTabTag = "routine_tab"
internal const val notToDoTabTag = "not_to_do_tab"

/** 할 행동과 피할 행동을 나란히 두고, 각 목록의 보던 위치를 유지합니다. */
@Composable
fun RoutineTabsScreen(
    routineContent: @Composable () -> Unit,
    notToDoContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    requestedNotToDo: Boolean? = null,
    requestKey: Any? = null
) {
    var showNotToDo by rememberSaveable { mutableStateOf(requestedNotToDo == true) }
    val stateHolder = rememberSaveableStateHolder()
    LaunchedEffect(requestedNotToDo, requestKey) {
        if (requestedNotToDo != null) showNotToDo = requestedNotToDo
    }
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (showNotToDo) 1 else 0) {
            Tab(
                selected = !showNotToDo,
                onClick = { showNotToDo = false },
                text = { Text("루틴") },
                modifier = Modifier.testTag(routineTabTag)
            )
            Tab(
                selected = showNotToDo,
                onClick = { showNotToDo = true },
                text = { Text("하지 않을 일") },
                modifier = Modifier.testTag(notToDoTabTag)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            stateHolder.SaveableStateProvider(showNotToDo) {
                if (showNotToDo) notToDoContent() else routineContent()
            }
        }
    }
}
