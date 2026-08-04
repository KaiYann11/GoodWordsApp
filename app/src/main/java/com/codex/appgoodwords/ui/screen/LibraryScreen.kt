package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType

private enum class RankSort(
    val label: String
) {
    NEWEST("최신순"),
    MOST_VIEWED("많이 읽은 순"),
    LEAST_VIEWED("적게 읽은 순")
}

private enum class ContentFilter(
    val label: String
) {
    ALL("전체"),
    QUOTE("글귀"),
    LINK("링크"),
    VIDEO("영상");

    fun matches(item: ContentItemEntity): Boolean {
        return when (this) {
            ALL -> true
            QUOTE -> item.type == ContentType.QUOTE
            LINK -> item.type == ContentType.LINK
            VIDEO -> item.type == ContentType.VIDEO
        }
    }
}

@Composable
fun LibraryScreen(
    items: List<ContentItemEntity>,
    categories: List<String>,
    confirmedTodayIds: Set<Long>,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    onResetTodayConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(ContentFilter.ALL.name) }
    var sortMode by rememberSaveable { mutableStateOf(RankSort.NEWEST.name) }
    // 즐겨찾기는 유형과 별개 축이라 "즐겨찾기 + 글귀"처럼 겹쳐 쓸 수 있게 별도 토글로 둔다.
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }

    val activeFilter = ContentFilter.valueOf(selectedFilter)
    val filteredItems = items.filter { item ->
        val matchesType = activeFilter.matches(item)
        val matchesCategory = selectedCategory.isBlank() || item.category == selectedCategory
        val matchesFavorite = !favoritesOnly || item.isFavorite
        val haystack = listOf(item.title, item.body, item.author, item.category, item.tags.joinToString(" "))
            .joinToString(" ")
            .lowercase()
        val matchesQuery = query.isBlank() || haystack.contains(query.trim().lowercase())
        matchesType && matchesCategory && matchesFavorite && matchesQuery
    }

    val sortedItems = when (RankSort.valueOf(sortMode)) {
        RankSort.NEWEST -> filteredItems.sortedByDescending { it.createdAt }
        RankSort.MOST_VIEWED -> filteredItems.sortedWith(
            compareByDescending<ContentItemEntity> { it.showCount }
                .thenByDescending { it.lastShownAt ?: it.createdAt }
                .thenBy { it.title }
        )

        RankSort.LEAST_VIEWED -> filteredItems.sortedWith(
            compareBy<ContentItemEntity> { it.showCount }
                .thenByDescending { it.createdAt }
                .thenBy { it.title }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "글귀, 링크, 영상을 한 화면에서 검색하고 정렬합니다.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { value -> query = value },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("검색") },
                supportingText = { Text("제목, 본문, 태그, 카테고리로 검색") }
            )
        }

        item {
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("즐겨찾기만") },
                leadingIcon = {
                    Icon(
                        imageVector = if (favoritesOnly) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = null
                    )
                }
            )
        }

        item {
            Text(
                text = "콘텐츠 유형",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ContentFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter.name,
                        onClick = { selectedFilter = filter.name },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        item {
            Text(
                text = "카테고리",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory.isBlank(),
                        onClick = { selectedCategory = "" },
                        label = { Text("전체") }
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
        }

        item {
            Text(
                text = "정렬",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RankSort.entries) { rankSort ->
                    FilterChip(
                        selected = sortMode == rankSort.name,
                        onClick = { sortMode = rankSort.name },
                        label = { Text(rankSort.label) }
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onResetTodayConfirmed,
                modifier = Modifier.fillMaxWidth(),
                enabled = confirmedTodayIds.isNotEmpty()
            ) {
                Text("오늘 읽음 전체 초기화")
            }
        }

        item {
            Text(
                text = "버튼을 누르면 오늘 읽음 표시만 수동으로 초기화됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text(
                text = "보관함 ${sortedItems.size}건",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (sortedItems.isEmpty()) {
            item {
                Text(
                    text = "조건에 맞는 항목이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(sortedItems, key = { it.id }) { item ->
                ContentItemCard(
                    item = item,
                    confirmedToday = item.id in confirmedTodayIds,
                    onToggleFavorite = onToggleFavorite,
                    onConfirmItem = onConfirmItem,
                    onOpenItem = onOpenItem
                )
            }
        }
    }
}
