package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

internal const val libraryQuoteTabTag = "library_tab_quote"
internal const val libraryBookTabTag = "library_tab_book"

/**
 * 모아 둔 글귀와 읽는 책을 한 탭 안에서 나눠 보여 줍니다.
 *
 * 책에서 뽑은 글귀가 그대로 보관함으로 가므로 둘은 이어져 있습니다.
 * 하단 바에 탭을 하나 더 넣으면 글자가 잘려서, 오늘 탭과 같은 방식으로 안에서 나눴습니다.
 */
@Composable
fun LibraryTabsScreen(
    quoteContent: @Composable () -> Unit,
    bookContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // 화면을 돌려도 보던 쪽이 유지되어야 합니다.
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected) {
            Tab(
                selected = selected == 0,
                onClick = { selected = 0 },
                text = { Text("글귀") },
                modifier = Modifier.testTag(libraryQuoteTabTag)
            )
            Tab(
                selected = selected == 1,
                onClick = { selected = 1 },
                text = { Text("독서") },
                modifier = Modifier.testTag(libraryBookTabTag)
            )
        }
        if (selected == 0) quoteContent() else bookContent()
    }
}
