package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.AppSearch
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentMemoEntity
import com.codex.appgoodwords.data.DiaryEntity
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.RoutineEntity
import com.codex.appgoodwords.data.SearchHit
import com.codex.appgoodwords.data.SearchKind
import com.codex.appgoodwords.data.TodoEntity

internal const val searchInputTag = "search_input"

/**
 * 앱에 쌓인 모든 기록을 한 곳에서 찾습니다.
 *
 * 예전에는 보관함(글귀)에만 검색이 있어서, 일기가 쌓이면 "작년 여름에 뭐라고 썼더라"를
 * 찾을 방법이 없었습니다.
 */
@Composable
fun SearchScreen(
    items: List<ContentItemEntity>,
    contentMemos: List<ContentMemoEntity> = emptyList(),
    diaries: List<DiaryEntity>,
    todos: List<TodoEntity>,
    books: List<BookEntity>,
    routines: List<RoutineEntity>,
    onOpenHit: (SearchHit) -> Unit,
    growthReports: List<GrowthReportEntity> = emptyList(),
    modifier: Modifier = Modifier,
    diaryLocked: Boolean = false,
    onUnlockDiaries: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    // 글자를 칠 때마다 다시 찾습니다. 목록이 기기 안에 있어 따로 늦출 이유가 없습니다.
    val results = remember(query, items, contentMemos, diaries, todos, books, routines, growthReports, diaryLocked) {
        AppSearch.search(
            query = query,
            items = items,
            contentMemos = contentMemos,
            diaries = if (diaryLocked) emptyList() else diaries,
            todos = todos,
            books = books,
            routines = routines,
            growthReports = if (diaryLocked) emptyList() else growthReports
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("검색") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "검색어 지우기")
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                supportingText = { Text("글귀와 메모, 루틴·하지 않을 일·원칙 등 저장한 기록을 찾습니다.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(searchInputTag)
            )
        }

        if (diaryLocked) {
            item { DiaryUnlockCard(onUnlock = onUnlockDiaries) }
        }

        if (query.isBlank()) {
            item {
                EmptyCard(
                    title = "어떤 기록을 찾고 있나요?",
                    body = "기억나는 단어나 문장을 입력해 보세요. 기록을 누르면 해당 화면으로 바로 이동합니다."
                )
            }
        }

        if (query.isNotBlank() && results.isEmpty) {
            item {
                Text(
                    text = "\"$query\"에 맞는 기록이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        results.byKind.forEach { (kind, hits) ->
            item(key = "header-${kind.name}") {
                Text(
                    text = "${kind.label} ${hits.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(hits, key = { "${kind.name}-${it.id}" }) { hit ->
                // 어느 종류든 눌러서 갈 수 있습니다. 찾아 놓고 다시 손으로 뒤지게 하면 안 됩니다.
                HitCard(hit = hit, onClick = { onOpenHit(hit) })
            }
        }
    }
}

@Composable
private fun HitCard(hit: SearchHit, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
            }
            if (hit.meta.isNotBlank()) {
                Text(
                    text = hit.meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hit.snippet.isNotBlank()) {
                Text(text = hit.snippet, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
