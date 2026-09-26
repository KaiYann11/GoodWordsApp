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
internal const val principleTabTag = "principle_tab"

/**
 * 루틴 탭 안의 세 자리.
 *
 * 밟는 것(루틴)과 지키는 것(하지 않을 일·원칙)을 나란히 둡니다. 셋 다 "어떻게 살기로 했나"를
 * 적는 자리라 한 탭에 모으고, 세는 방식이 다르므로 화면만 나눕니다.
 */
enum class RoutineTab(val label: String, val tag: String) {
    ROUTINE("루틴", routineTabTag),
    NOT_TO_DO("하지 않을 일", notToDoTabTag),
    PRINCIPLE("원칙", principleTabTag)
}

/** 할 행동과 피할 행동, 지킬 기준을 나란히 두고, 각 목록의 보던 위치를 유지합니다. */
@Composable
fun RoutineTabsScreen(
    routineContent: @Composable () -> Unit,
    notToDoContent: @Composable () -> Unit,
    principleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    requestedTab: RoutineTab? = null,
    requestKey: Any? = null
) {
    var selected by rememberSaveable { mutableStateOf(requestedTab ?: RoutineTab.ROUTINE) }
    val stateHolder = rememberSaveableStateHolder()
    LaunchedEffect(requestedTab, requestKey) {
        if (requestedTab != null) selected = requestedTab
    }
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected.ordinal) {
            RoutineTab.entries.forEach { tab ->
                Tab(
                    selected = selected == tab,
                    onClick = { selected = tab },
                    text = { Text(tab.label) },
                    modifier = Modifier.testTag(tab.tag)
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            // 탭마다 보던 자리를 따로 기억합니다. 한 벌만 두면 탭을 옮길 때마다 맨 위로 튑니다.
            stateHolder.SaveableStateProvider(selected) {
                when (selected) {
                    RoutineTab.ROUTINE -> routineContent()
                    RoutineTab.NOT_TO_DO -> notToDoContent()
                    RoutineTab.PRINCIPLE -> principleContent()
                }
            }
        }
    }
}
