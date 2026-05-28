package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity

@Composable
fun VideoScreen(
    items: List<ContentItemEntity>,
    categories: List<String>,
    confirmedTodayIds: Set<Long>,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableStateOf("") }

    val filteredItems = items.filter { item ->
        selectedCategory.isBlank() || item.category == selectedCategory
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "영상도 일반 항목처럼 카드에서 바로 확인 체크를 할 수 있고, 상세 화면에서 수정과 삭제가 가능합니다.",
                style = MaterialTheme.typography.bodyMedium
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

        if (filteredItems.isEmpty()) {
            item {
                Text("등록한 영상이 없습니다.")
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
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
