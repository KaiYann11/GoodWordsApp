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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import com.codex.appgoodwords.data.RoutineMemoEntity
import com.codex.appgoodwords.data.SearchHit
import com.codex.appgoodwords.data.SearchKind
import com.codex.appgoodwords.data.TodoEntity

internal const val searchInputTag = "search_input"

/** 갈래 거르개. null은 전체입니다. */
internal fun searchKindChipTag(kind: SearchKind?) = "search_kind_${kind?.name ?: "ALL"}"

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
    routineMemos: List<RoutineMemoEntity> = emptyList(),
    onOpenHit: (SearchHit) -> Unit,
    growthReports: List<GrowthReportEntity> = emptyList(),
    modifier: Modifier = Modifier,
    diaryLocked: Boolean = false,
    onUnlockDiaries: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    /** 좁혀 둔 갈래. null이면 전체입니다. */
    var onlyKind by rememberSaveable { mutableStateOf<SearchKind?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    // 글자를 칠 때마다 다시 찾습니다. 목록이 기기 안에 있어 따로 늦출 이유가 없습니다.
    val results = remember(
        query, items, contentMemos, diaries, todos, books, routines, routineMemos, growthReports, diaryLocked
    ) {
        AppSearch.search(
            query = query,
            items = items,
            contentMemos = contentMemos,
            diaries = if (diaryLocked) emptyList() else diaries,
            todos = todos,
            books = books,
            routines = routines,
            routineMemos = routineMemos,
            growthReports = if (diaryLocked) emptyList() else growthReports
        )
    }

    // 찾은 것이 있는 갈래만 거르개에 내놓습니다. 없는 갈래를 눌러 빈 화면을 보게 하지 않습니다.
    val kinds = results.byKind.map { (kind, _) -> kind }
    // 좁혀 둔 갈래가 이번 결과에 없으면 전체로 돌립니다. 글자를 더 치는 사이에 결과가
    // 통째로 비어 "안 나온다"고 여기게 되기 때문입니다.
    val activeKind = onlyKind?.takeIf { it in kinds }
    val shownGroups = results.byKind.filter { (kind, _) -> activeKind == null || kind == activeKind }

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
                supportingText = {
                    Text("글귀·메모·일기·할 일·루틴과 그 메모·독후감·돌아보기까지 한 번에 찾습니다.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(searchInputTag)
            )
        }

        if (diaryLocked) {
            item { DiaryUnlockCard(onUnlock = onUnlockDiaries) }
        }

        // 갈래가 하나뿐이면 거르개가 할 일이 없습니다.
        if (kinds.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = activeKind == null,
                            onClick = { onlyKind = null },
                            label = { Text("전체 ${results.hits.size}") },
                            modifier = Modifier.testTag(searchKindChipTag(null))
                        )
                    }
                    items(kinds, key = { it.name }) { kind ->
                        val count = results.hits.count { it.kind == kind }
                        FilterChip(
                            selected = activeKind == kind,
                            // 누른 것을 다시 누르면 전체로 돌아옵니다.
                            onClick = { onlyKind = if (activeKind == kind) null else kind },
                            label = { Text("${kind.label} $count") },
                            modifier = Modifier.testTag(searchKindChipTag(kind))
                        )
                    }
                }
            }
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

        shownGroups.forEach { (kind, hits) ->
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
