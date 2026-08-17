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
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.AppSearch
import com.codex.appgoodwords.data.BookEntity
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.DiaryEntity
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
    diaries: List<DiaryEntity>,
    todos: List<TodoEntity>,
    books: List<BookEntity>,
    routines: List<RoutineEntity>,
    onOpenQuote: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }

    // 글자를 칠 때마다 다시 찾습니다. 목록이 기기 안에 있어 따로 늦출 이유가 없습니다.
    val results = remember(query, items, diaries, todos, books, routines) {
        AppSearch.search(
            query = query,
            items = items,
            diaries = diaries,
            todos = todos,
            books = books,
            routines = routines
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
                supportingText = { Text("글귀·일기·할 일·독서·루틴을 함께 찾습니다.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(searchInputTag)
            )
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
                HitCard(
                    hit = hit,
                    // 글귀만 열 곳이 있습니다. 나머지는 각 탭에서 봐야 해서 눌러도 갈 곳이 없습니다.
                    onClick = if (hit.kind == SearchKind.QUOTE) {
                        { onOpenQuote(hit.id) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun HitCard(hit: SearchHit, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
