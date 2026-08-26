package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentShuffle
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ReadingCombo
import kotlinx.coroutines.delay

internal const val libraryAddButtonTag = "library_add_button"

/** 읽음 거르개. 개수가 같이 적혀서 글자만으로는 짚기 어렵습니다. */
internal fun libraryReadFilterTag(name: String): String = "library_read_filter_$name"

/** 나머지 거르개를 펴고 접는 칩. */
internal const val libraryFilterToggleTag = "library_filter_toggle"

private enum class RankSort(
    val label: String
) {
    /** 켤 때마다 다른 차례. 뒤쪽에 담아 둔 글귀도 앞자리에 서게 하려는 것입니다. */
    SHUFFLED("섞어서"),
    NEWEST("최신순"),
    MOST_VIEWED("많이 읽은 순"),
    LEAST_VIEWED("적게 읽은 순")
}

private enum class ContentFilter(
    val label: String
) {
    ALL("전체"),
    QUOTE("글귀"),
    IDEA("아이디어"),
    LINK("링크"),
    VIDEO("영상");

    fun matches(item: ContentItemEntity): Boolean {
        return when (this) {
            ALL -> true
            QUOTE -> item.type == ContentType.QUOTE
            IDEA -> item.type == ContentType.IDEA
            LINK -> item.type == ContentType.LINK
            VIDEO -> item.type == ContentType.VIDEO
        }
    }
}

/** 오늘 읽었는지로 거르기. 홈에 있던 거르개가 읽는 자리를 따라 여기로 왔습니다. */
private enum class ReadFilter(val label: String) {
    ALL("전체"),
    UNREAD("안읽은 것"),
    READ("읽은 것");

    fun matches(confirmedToday: Boolean): Boolean = when (this) {
        ALL -> true
        UNREAD -> !confirmedToday
        READ -> confirmedToday
    }
}

/**
 * 모아 둔 글귀를 찾고, 읽고, 새로 담는 자리.
 *
 * 예전에는 홈에도 같은 목록이 있어서 두 화면이 겹쳤습니다. 읽는 일은 글귀가 모여 있는
 * 여기로 모으고, 홈은 돌아보는 자리로 두었습니다.
 */
@Composable
fun LibraryScreen(
    items: List<ContentItemEntity>,
    categories: List<String>,
    confirmedTodayIds: Set<Long>,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    onResetTodayConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 새 글귀를 담는 화면을 엽니다.
     *
     * 하단 바에 있던 +를 여기로 옮겼습니다. 담는 일은 글귀를 보다가 하게 되는 것이라
     * 그 화면 안에 있어야 하고, 하단 바는 "어디로 갈지"만 남는 편이 읽기 쉽습니다.
     */
    onAddContent: () -> Unit = {},
    /** 섞는 씨앗. 앱을 켤 때마다 달라집니다([ContentShuffle]). */
    shuffleSeed: Long = 0L,
    /** 지금 바로 다시 섞습니다. */
    onShuffle: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(ContentFilter.ALL.name) }
    // 여는 순간에는 오늘 읽을 것부터 보입니다. 읽는 자리가 여기로 왔으므로, 이미 넘긴 것을
    // 지나쳐 가며 읽을 것을 찾게 두지 않습니다. 한 번 바꾸면 그 고른 값이 유지됩니다.
    var selectedReadFilter by rememberSaveable { mutableStateOf(ReadFilter.UNREAD.name) }
    var sortMode by rememberSaveable { mutableStateOf(RankSort.SHUFFLED.name) }
    // 즐겨찾기는 유형과 별개 축이라 "즐겨찾기 + 글귀"처럼 겹쳐 쓸 수 있게 별도 토글로 둔다.
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var comboFeedback by remember { mutableStateOf<ComboFeedback?>(null) }

    val activeFilter = ContentFilter.valueOf(selectedFilter)
    val activeReadFilter = ReadFilter.valueOf(selectedReadFilter)
    // 읽음 거르개를 빼고 거른 것. 알약에 적을 개수는 여기서 셉니다.
    // 자기 자신까지 걸러서 세면 "안읽은 것"을 고른 순간 "읽은 것 0"이 됩니다.
    val baseItems = items.filter { item ->
        val matchesType = activeFilter.matches(item)
        val matchesCategory = selectedCategory.isBlank() || item.category == selectedCategory
        val matchesFavorite = !favoritesOnly || item.isFavorite
        val haystack = listOf(item.title, item.body, item.author, item.category, item.tags.joinToString(" "))
            .joinToString(" ")
            .lowercase()
        val matchesQuery = query.isBlank() || haystack.contains(query.trim().lowercase())
        matchesType && matchesCategory && matchesFavorite && matchesQuery
    }
    val readCount = baseItems.count { it.id in confirmedTodayIds }
    val unreadCount = baseItems.size - readCount
    val filteredItems = baseItems.filter { activeReadFilter.matches(it.id in confirmedTodayIds) }

    val sortedItems = when (RankSort.valueOf(sortMode)) {
        RankSort.SHUFFLED -> ContentShuffle.ordered(filteredItems, shuffleSeed)
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

    // 오늘 확인한 글귀 수가 곧 콤보입니다. 빨리 넘기는 것과는 상관이 없습니다.
    val combo = remember(confirmedTodayIds.size) { ReadingCombo.of(confirmedTodayIds.size) }
    val registerSwipeFeedback = rememberSwipeFeedback(confirmedTodayIds.size)

    LaunchedEffect(comboFeedback?.token) {
        val token = comboFeedback?.token ?: return@LaunchedEffect
        delay(1600)
        if (comboFeedback?.token == token) {
            comboFeedback = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 아래쪽을 더 비웁니다. 담기 버튼이 마지막 글귀를 가리면 그 글귀는 누를 수 없습니다.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ReadFilter.entries) { filter ->
                        CountFilterPill(
                            title = filter.label,
                            count = when (filter) {
                                ReadFilter.ALL -> baseItems.size
                                ReadFilter.UNREAD -> unreadCount
                                ReadFilter.READ -> readCount
                            },
                            selected = activeReadFilter == filter,
                            onClick = { selectedReadFilter = filter.name },
                            modifier = Modifier.testTag(libraryReadFilterTag(filter.name))
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 읽는 자리가 여기로 왔으므로 목록이 먼저 보여야 합니다. 나머지 거르개는
                    // 필요할 때만 폅니다. 넷을 늘 펼쳐 두면 첫 글귀가 화면 밖으로 밀립니다.
                    FilterChip(
                        selected = showFilters,
                        onClick = { showFilters = !showFilters },
                        label = { Text(if (showFilters) "거르개 접기" else "거르개") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showFilters) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.testTag(libraryFilterToggleTag)
                    )
                    // 접어 두어도 지금 무엇으로 걸러 보고 있는지는 알아야 합니다.
                    Text(
                        text = filterSummary(
                            type = activeFilter,
                            category = selectedCategory,
                            favoritesOnly = favoritesOnly,
                            sort = RankSort.valueOf(sortMode)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showFilters) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ContentFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter.name,
                                onClick = { selectedFilter = filter.name },
                                label = { Text(filter.label) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = favoritesOnly,
                                onClick = { favoritesOnly = !favoritesOnly },
                                label = { Text("즐겨찾기") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (favoritesOnly) {
                                            Icons.Outlined.Star
                                        } else {
                                            Icons.Outlined.StarBorder
                                        },
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (showFilters && categories.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedCategory.isBlank(),
                                onClick = { selectedCategory = "" },
                                label = { Text("모든 카테고리") }
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
            }

            if (showFilters) {
                item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(RankSort.entries) { rankSort ->
                        FilterChip(
                            selected = sortMode == rankSort.name,
                            onClick = { sortMode = rankSort.name },
                            label = { Text(rankSort.label) },
                            leadingIcon = if (rankSort == RankSort.SHUFFLED && sortMode == rankSort.name) {
                                {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                    // 섞어서 보는 중일 때만 "지금 한 번 더"를 답니다. 최신순에는 뜻이 없습니다.
                    if (sortMode == RankSort.SHUFFLED.name) {
                        item {
                            TextButton(onClick = onShuffle) {
                                Text("다시 섞기")
                            }
                        }
                    }
                }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${sortedItems.size}건",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = onResetTodayConfirmed,
                        enabled = confirmedTodayIds.isNotEmpty()
                    ) {
                        Text("오늘 읽음 초기화")
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    EmptyCard(
                        title = "아직 담아 둔 글귀가 없습니다.",
                        body = "오른쪽 아래 담기 버튼으로 글귀, 링크, 영상을 저장해 보세요."
                    )
                }
            } else if (sortedItems.isEmpty()) {
                item {
                    EmptyCard(
                        title = when (activeReadFilter) {
                            ReadFilter.UNREAD -> "오늘 읽을 글귀를 다 넘겼습니다."
                            ReadFilter.READ -> "오늘 읽은 글귀가 아직 없습니다."
                            ReadFilter.ALL -> "조건에 맞는 글귀가 없습니다."
                        },
                        body = "거르개를 전체로 돌리면 담아 둔 글귀가 다시 보입니다."
                    )
                }
            } else {
                items(sortedItems, key = { it.id }) { item ->
                    QuoteSwipeCard(
                        item = item,
                        confirmedToday = item.id in confirmedTodayIds,
                        // 전체 보기에서는 민 카드가 목록에 그대로 남습니다.
                        staysInList = activeReadFilter == ReadFilter.ALL,
                        onToggleFavorite = onToggleFavorite,
                        onConfirmItem = { confirmed ->
                            comboFeedback = registerSwipeFeedback(confirmed.id !in confirmedTodayIds)
                            onConfirmItem(confirmed)
                        },
                        onOpenItem = onOpenItem,
                        onSwipeToggle = { swipedItem, willBeConfirmed ->
                            comboFeedback = registerSwipeFeedback(willBeConfirmed)
                            onConfirmItem(swipedItem)
                        }
                    )
                }
            }
        }

        // 잔물결은 목록 한가운데에서 번집니다. 스와이프한 카드가 있는 자리입니다.
        comboFeedback?.let { feedback ->
            CelebrationPulse(
                token = feedback.token,
                milestone = feedback.confirmed && combo.isMilestone,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 알약은 스낵바 자리에 둡니다. 위에 두면 카드 제목을 가립니다.
        AnimatedVisibility(
            visible = comboFeedback != null,
            enter = fadeIn(tween(220)) +
                slideInVertically(spring(dampingRatio = 0.72f, stiffness = 420f)) { it / 2 } +
                scaleIn(tween(220), initialScale = 0.96f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        ) {
            ComboBanner(combo = combo, confirmed = comboFeedback?.confirmed ?: true)
        }

        // 글자를 함께 둡니다. +만 있으면 무엇이 담기는지 처음 보는 사람은 알 수 없습니다.
        ExtendedFloatingActionButton(
            onClick = onAddContent,
            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            text = { Text("글귀 담기") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                // 이 버튼 안의 글자는 접근성 정보로 합쳐지지 않아서, 이름을 따로 달지 않으면
                // 화면을 소리로 듣는 사람에게는 이름 없는 "버튼"으로만 읽힙니다.
                .semantics { contentDescription = "글귀 담기" }
                .testTag(libraryAddButtonTag)
        )
    }
}

/**
 * 접어 둔 거르개가 지금 무엇으로 걸러 보고 있는지 한 줄로 알려 줍니다.
 *
 * 이것이 없으면 즐겨찾기만 켜 둔 것을 잊고 "글귀가 사라졌다"고 여기게 됩니다.
 */
private fun filterSummary(
    type: ContentFilter,
    category: String,
    favoritesOnly: Boolean,
    sort: RankSort
): String {
    val parts = buildList {
        if (type != ContentFilter.ALL) add(type.label)
        if (category.isNotBlank()) add(category)
        if (favoritesOnly) add("즐겨찾기")
        add(sort.label)
    }
    return parts.joinToString(" · ")
}
