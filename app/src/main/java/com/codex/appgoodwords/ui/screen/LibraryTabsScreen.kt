package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal const val libraryQuoteTabTag = "library_tab_quote"
internal const val libraryIdeaTabTag = "library_tab_idea"
internal const val libraryBookTabTag = "library_tab_book"
internal const val libraryInboxTabTag = "library_tab_inbox"
internal const val libraryMemoTabTag = "library_tab_memo"
internal const val libraryConceptTabTag = "library_tab_concept"

/**
 * 보관함 안의 자리. 검색에서 넘어올 때도 이 번호로 짚습니다.
 *
 * 번호는 이름표일 뿐이고 **늘어놓는 차례는 [ORDER]가 정합니다.** 번호로 차례를 매기면
 * 탭을 옮길 때마다 저장된 번호가 다른 탭을 가리켜, 검색에서 넘어온 사람이 엉뚱한 곳에 떨어집니다.
 */
internal object LibraryTab {
    const val QUOTE = 0
    const val IDEA = 1
    const val BOOK = 2
    const val INBOX = 3
    const val MEMO = 4
    const val CONCEPT = 5

    /**
     * 자주 보는 것이 앞입니다.
     *
     * 글귀가 이 앱의 첫 자리이고, 번뜩인 것과 책이 그 둘레입니다. 개념과 메모는 찾아볼 때
     * 들르는 곳이라 그 뒤에 둡니다. **임시저장소는 맨 뒤입니다** — 아직 무엇인지 정하지 않은
     * 글 더미라, 보관함을 열 때마다 먼저 보이면 정리할 일부터 눈에 들어옵니다.
     */
    val ORDER = listOf(QUOTE, IDEA, BOOK, CONCEPT, MEMO, INBOX)
}

/**
 * 모아 둔 글귀와 번뜩인 것과 읽는 책을 한 탭 안에서 나눠 보여 줍니다.
 *
 * 책에서 뽑은 글귀가 그대로 보관함으로 가므로 셋은 이어져 있습니다.
 * 하단 바에 탭을 더 넣으면 글자가 잘려서, 오늘 탭과 같은 방식으로 안에서 나눴습니다.
 *
 * **번뜩인 것을 글귀와 한 목록에 두지 않습니다.** 담기는 자리는 같아도(그래야 루틴·할 일로
 * 옮기는 길이 그대로입니다) 성격이 다릅니다. 글귀는 음미하는 것이고 번뜩인 것은
 * "이거 아직 살아 있나" 하고 되묻는 것입니다. 섞어 두면 둘 다 흐려집니다.
 */
@Composable
fun LibraryTabsScreen(
    quoteContent: @Composable () -> Unit,
    bookContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    ideaContent: @Composable () -> Unit = {},
    /** 검색에서 넘어올 때 어느 쪽을 열지. null이면 보던 쪽 그대로입니다. */
    requestedTab: Int? = null,
    requestKey: Any? = null,
    inboxCount: Int = 0,
    inboxContent: @Composable () -> Unit = {},
    memoContent: @Composable () -> Unit = {},
    conceptContent: @Composable () -> Unit = {}
) {
    // 화면을 돌려도 보던 쪽이 유지되어야 합니다.
    var selected by rememberSaveable { mutableIntStateOf(LibraryTab.QUOTE) }

    // 검색에서 책을 골랐는데 글귀 쪽이 열려 있으면 찾던 것이 안 보입니다.
    LaunchedEffect(requestKey, requestedTab) {
        if (requestedTab != null) selected = requestedTab
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = LibraryTab.ORDER.indexOf(selected), edgePadding = 8.dp) {
            Tab(
                selected = selected == LibraryTab.QUOTE,
                onClick = { selected = LibraryTab.QUOTE },
                text = { Text("글귀") },
                modifier = Modifier.testTag(libraryQuoteTabTag)
            )
            Tab(
                selected = selected == LibraryTab.IDEA,
                onClick = { selected = LibraryTab.IDEA },
                text = { Text("아이디어") },
                modifier = Modifier.testTag(libraryIdeaTabTag)
            )
            Tab(
                selected = selected == LibraryTab.BOOK,
                onClick = { selected = LibraryTab.BOOK },
                text = { Text("독서") },
                modifier = Modifier.testTag(libraryBookTabTag)
            )
            Tab(
                selected = selected == LibraryTab.CONCEPT,
                onClick = { selected = LibraryTab.CONCEPT },
                text = { Text("개념") },
                modifier = Modifier.testTag(libraryConceptTabTag)
            )
            Tab(
                selected = selected == LibraryTab.MEMO,
                onClick = { selected = LibraryTab.MEMO },
                text = { Text("메모") },
                modifier = Modifier.testTag(libraryMemoTabTag)
            )
            Tab(
                selected = selected == LibraryTab.INBOX,
                onClick = { selected = LibraryTab.INBOX },
                // 맨 뒤에 있어도 쌓인 것이 보이도록 개수를 답니다.
                text = { Text(if (inboxCount > 0) "임시저장소 $inboxCount" else "임시저장소") },
                modifier = Modifier.testTag(libraryInboxTabTag)
            )
        }
        when (selected) {
            LibraryTab.INBOX -> inboxContent()
            LibraryTab.MEMO -> memoContent()
            LibraryTab.CONCEPT -> conceptContent()
            LibraryTab.IDEA -> ideaContent()
            LibraryTab.BOOK -> bookContent()
            else -> quoteContent()
        }
    }
}
