package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
internal const val libraryNoteTabTag = "library_tab_note"

// 아래 셋은 이제 노트 칸 안의 알약에 붙습니다. 탭이던 때의 이름을 그대로 두어 시험이 그대로 짚습니다.
internal const val libraryInboxTabTag = "library_tab_inbox"
internal const val libraryMemoTabTag = "library_tab_memo"
internal const val libraryConceptTabTag = "library_tab_concept"

/**
 * 보관함 안의 자리. 검색에서 넘어올 때도 이 번호로 짚습니다.
 *
 * 번호는 이름표일 뿐이고 **늘어놓는 차례는 [ORDER]가 정합니다.** 번호로 차례를 매기면
 * 탭을 옮길 때마다 저장된 번호가 다른 탭을 가리켜, 검색에서 넘어온 사람이 엉뚱한 곳에 떨어집니다.
 *
 * [MEMO]·[CONCEPT]·[INBOX]는 예전에는 탭이었고 지금은 [NOTE] 칸 안의 알약입니다. 번호를 그대로 두어서
 * 검색에서 메모를 고르면 여전히 [MEMO]로 짚어 오고, 노트 칸의 메모 알약이 열립니다.
 */
internal object LibraryTab {
    const val QUOTE = 0
    const val IDEA = 1
    const val BOOK = 2
    const val INBOX = 3
    const val MEMO = 4
    const val CONCEPT = 5
    const val NOTE = 6

    /**
     * 탭 줄의 차례. 자주 보는 것이 앞입니다.
     *
     * 글귀가 이 앱의 첫 자리이고, 번뜩인 것과 책이 그 둘레입니다. 노트는 찾아볼 때 들르는 곳이라 맨 뒤입니다.
     * 넷이라 한 줄에 다 보입니다 — 여섯이던 때는 옆으로 밀어야 메모와 임시저장소가 나왔습니다.
     */
    val ORDER = listOf(QUOTE, IDEA, BOOK, NOTE)

    /**
     * 노트 칸 안의 알약 차례.
     *
     * 셋 다 내가 적어 둔 것이라(`ContentType.personalTypes`, 알림·위젯에 오지 않음) 한 칸에 둡니다.
     * 아이디어는 다릅니다 — 다시 만나야 하는 것이라 알림·위젯에 오고, 자기 칸이 따로 있습니다.
     * **임시저장소는 맨 뒤입니다** — 아직 무엇인지 정하지 않은 글 더미라, 노트를 열 때마다 먼저 보이면
     * 정리할 일부터 눈에 들어옵니다.
     */
    val NOTE_PILLS = listOf(MEMO, CONCEPT, INBOX)

    /** 탭 줄에서 켜질 칸. 노트 안 알약은 노트 칸입니다. */
    fun tabOf(selected: Int): Int = if (selected in NOTE_PILLS) NOTE else selected
}

/**
 * 모아 둔 글귀와 번뜩인 것과 읽는 책, 그리고 내가 적어 둔 노트를 한 탭 안에서 나눠 보여 줍니다.
 *
 * 책에서 뽑은 글귀가 그대로 보관함으로 가므로 셋은 이어져 있습니다.
 * 하단 바에 탭을 더 넣으면 글자가 잘려서, 오늘 탭과 같은 방식으로 안에서 나눴습니다.
 *
 * **번뜩인 것을 글귀와 한 목록에 두지 않습니다.** 담기는 자리는 같아도(그래야 루틴·할 일로
 * 옮기는 길이 그대로입니다) 성격이 다릅니다. 글귀는 음미하는 것이고 번뜩인 것은
 * "이거 아직 살아 있나" 하고 되묻는 것입니다. 섞어 두면 둘 다 흐려집니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTabsScreen(
    quoteContent: @Composable () -> Unit,
    bookContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    ideaContent: @Composable () -> Unit = {},
    /** 검색에서 넘어올 때 어느 쪽을 열지. null이면 보던 쪽 그대로입니다. 노트 안 알약 번호도 받습니다. */
    requestedTab: Int? = null,
    requestKey: Any? = null,
    inboxCount: Int = 0,
    memoCount: Int = 0,
    conceptCount: Int = 0,
    inboxContent: @Composable () -> Unit = {},
    memoContent: @Composable () -> Unit = {},
    conceptContent: @Composable () -> Unit = {}
) {
    // 화면을 돌려도 보던 쪽이 유지되어야 합니다. 노트를 보고 있으면 그 안의 알약 번호를 담습니다.
    var selected by rememberSaveable { mutableIntStateOf(LibraryTab.QUOTE) }
    // 다른 탭에 다녀와도 노트는 보던 알약으로 열립니다.
    var lastNotePill by rememberSaveable { mutableIntStateOf(LibraryTab.MEMO) }

    // 검색에서 책을 골랐는데 글귀 쪽이 열려 있으면 찾던 것이 안 보입니다.
    LaunchedEffect(requestKey, requestedTab) {
        when (requestedTab) {
            null -> Unit
            LibraryTab.NOTE -> selected = lastNotePill
            else -> {
                selected = requestedTab
                if (requestedTab in LibraryTab.NOTE_PILLS) lastNotePill = requestedTab
            }
        }
    }
    val currentTab = LibraryTab.tabOf(selected)

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = LibraryTab.ORDER.indexOf(currentTab)) {
            Tab(
                selected = currentTab == LibraryTab.QUOTE,
                onClick = { selected = LibraryTab.QUOTE },
                text = { Text("글귀") },
                modifier = Modifier.testTag(libraryQuoteTabTag)
            )
            Tab(
                selected = currentTab == LibraryTab.IDEA,
                onClick = { selected = LibraryTab.IDEA },
                text = { Text("아이디어") },
                modifier = Modifier.testTag(libraryIdeaTabTag)
            )
            Tab(
                selected = currentTab == LibraryTab.BOOK,
                onClick = { selected = LibraryTab.BOOK },
                text = { Text("독서") },
                modifier = Modifier.testTag(libraryBookTabTag)
            )
            Tab(
                selected = currentTab == LibraryTab.NOTE,
                onClick = { selected = lastNotePill },
                text = {
                    // 임시저장소가 탭이던 때는 이름에 개수를 달아 쌓인 것을 알렸습니다. 노트 안으로 들어가도
                    // 다른 탭에서 그것이 보여야, 공유로 담아 둔 글이 잊히지 않습니다.
                    if (inboxCount > 0) {
                        BadgedBox(badge = { Badge { Text("$inboxCount") } }) { Text("노트") }
                    } else {
                        Text("노트")
                    }
                },
                modifier = Modifier.testTag(libraryNoteTabTag)
            )
        }
        when (currentTab) {
            LibraryTab.NOTE -> NoteContent(
                selectedPill = selected,
                memoCount = memoCount,
                conceptCount = conceptCount,
                inboxCount = inboxCount,
                onSelectPill = { pill ->
                    selected = pill
                    lastNotePill = pill
                },
                memoContent = memoContent,
                conceptContent = conceptContent,
                inboxContent = inboxContent
            )
            LibraryTab.IDEA -> ideaContent()
            LibraryTab.BOOK -> bookContent()
            else -> quoteContent()
        }
    }
}

/** 노트 칸. 위에 알약 줄을 두고, 고른 것의 목록을 아래에 보여 줍니다. */
@Composable
private fun NoteContent(
    selectedPill: Int,
    memoCount: Int,
    conceptCount: Int,
    inboxCount: Int,
    onSelectPill: (Int) -> Unit,
    memoContent: @Composable () -> Unit,
    conceptContent: @Composable () -> Unit,
    inboxContent: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(LibraryTab.NOTE_PILLS) { pill ->
                CountFilterPill(
                    title = when (pill) {
                        LibraryTab.CONCEPT -> "개념"
                        LibraryTab.INBOX -> "임시저장소"
                        else -> "메모"
                    },
                    count = when (pill) {
                        LibraryTab.CONCEPT -> conceptCount
                        LibraryTab.INBOX -> inboxCount
                        else -> memoCount
                    },
                    selected = selectedPill == pill,
                    onClick = { onSelectPill(pill) },
                    modifier = Modifier.testTag(
                        when (pill) {
                            LibraryTab.CONCEPT -> libraryConceptTabTag
                            LibraryTab.INBOX -> libraryInboxTabTag
                            else -> libraryMemoTabTag
                        }
                    )
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (selectedPill) {
                LibraryTab.CONCEPT -> conceptContent()
                LibraryTab.INBOX -> inboxContent()
                else -> memoContent()
            }
        }
    }
}
