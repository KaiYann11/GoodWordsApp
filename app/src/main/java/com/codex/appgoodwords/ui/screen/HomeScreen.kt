package com.codex.appgoodwords.ui.screen

import android.os.SystemClock
import android.view.SoundEffectConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ReminderSettings
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

private enum class HomeReadTab(
    val label: String
) {
    ALL("전체"),
    UNREAD("안읽은 것"),
    READ("읽은 것")
}

private data class ComboFeedback(
    val token: Long,
    val title: String,
    val subtitle: String,
    val comboCount: Int
)

private data class CelebrationBurstState(
    val token: Long,
    val intense: Boolean
)

@Composable
fun HomeScreen(
    todayItems: List<ContentItemEntity>,
    settings: ReminderSettings,
    confirmedTodayIds: Set<Long>,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeReadTab.UNREAD.name) }
    var lastSwipeAt by rememberSaveable { mutableLongStateOf(0L) }
    var comboCount by rememberSaveable { mutableLongStateOf(0L) }
    var shuffleSeed by rememberSaveable { mutableLongStateOf(0L) }
    var comboFeedback by remember { mutableStateOf<ComboFeedback?>(null) }
    var burstState by remember { mutableStateOf<CelebrationBurstState?>(null) }

    val currentTab = HomeReadTab.valueOf(selectedTab)
    val readCount = remember(todayItems, confirmedTodayIds) {
        todayItems.count { it.id in confirmedTodayIds }
    }
    val unreadCount = todayItems.size - readCount
    val filteredItems = remember(todayItems, confirmedTodayIds, currentTab, shuffleSeed) {
        val baseItems = when (currentTab) {
            HomeReadTab.ALL -> todayItems
            HomeReadTab.UNREAD -> todayItems.filterNot { it.id in confirmedTodayIds }
            HomeReadTab.READ -> todayItems.filter { it.id in confirmedTodayIds }
        }
        if (shuffleSeed == 0L) baseItems else baseItems.shuffled(Random(shuffleSeed))
    }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    LaunchedEffect(comboFeedback?.token) {
        val token = comboFeedback?.token ?: return@LaunchedEffect
        delay(1100)
        if (comboFeedback?.token == token) {
            comboFeedback = null
        }
    }

    LaunchedEffect(burstState?.token) {
        val token = burstState?.token ?: return@LaunchedEffect
        delay(750)
        if (burstState?.token == token) {
            burstState = null
        }
    }

    fun registerSwipeFeedback(nowConfirmed: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val nextCombo = if (now - lastSwipeAt <= 2_000L) comboCount + 1L else 1L
        lastSwipeAt = now
        comboCount = nextCombo

        view.playSoundEffect(SoundEffectConstants.CLICK)
        haptic.performHapticFeedback(
            if (nextCombo >= 2L) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
        )

        comboFeedback = ComboFeedback(
            token = now,
            title = if (nextCombo >= 2L) "${nextCombo} COMBO" else if (nowConfirmed) "읽음 완료" else "읽음 취소",
            subtitle = if (nextCombo >= 2L) "속도감 있게 정리하고 있습니다" else if (nowConfirmed) "오늘의 글귀 하나를 정리했습니다" else "다시 읽을 글귀로 되돌렸습니다",
            comboCount = nextCombo.toInt()
        )
        burstState = CelebrationBurstState(
            token = now,
            intense = nextCombo >= 2L || nowConfirmed
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                HomeHeroCard(
                    totalCount = todayItems.size,
                    unreadCount = unreadCount,
                    readCount = readCount,
                    onShuffle = {
                        shuffleSeed = SystemClock.elapsedRealtimeNanos()
                    }
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(HomeReadTab.entries) { tab ->
                        val count = when (tab) {
                            HomeReadTab.ALL -> todayItems.size
                            HomeReadTab.UNREAD -> unreadCount
                            HomeReadTab.READ -> readCount
                        }
                        ReadFilterPill(
                            title = tab.label,
                            count = count,
                            selected = currentTab == tab,
                            onClick = { selectedTab = tab.name }
                        )
                    }
                }
            }

            if (todayItems.isEmpty()) {
                item {
                    EmptyCard(
                        title = "오늘 노출된 글귀가 아직 없습니다.",
                        body = "아직 저장된 게시글이 없습니다. 추가 버튼으로 글귀, 링크, 영상을 먼저 저장해 주세요."
                    )
                }
            } else if (filteredItems.isEmpty()) {
                item {
                    EmptyCard(
                        title = when (currentTab) {
                            HomeReadTab.ALL -> "표시할 글귀가 없습니다."
                            HomeReadTab.UNREAD -> "안읽은 글귀를 모두 정리했습니다."
                            HomeReadTab.READ -> "아직 읽은 글귀가 없습니다."
                        },
                        body = when (currentTab) {
                            HomeReadTab.ALL -> "저장된 게시글이 생기면 여기에 나타납니다."
                            HomeReadTab.UNREAD -> "읽음으로 정리한 항목은 읽은 것 탭에서 다시 볼 수 있습니다."
                            HomeReadTab.READ -> "글귀를 밀거나 확인 버튼을 눌러 읽음으로 바꿔보세요."
                        }
                    )
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    TodaySwipeCard(
                        item = item,
                        confirmedToday = item.id in confirmedTodayIds,
                        currentTab = currentTab,
                        onToggleFavorite = onToggleFavorite,
                        onConfirmItem = {
                            registerSwipeFeedback(it.id !in confirmedTodayIds)
                            onConfirmItem(it)
                        },
                        onOpenItem = onOpenItem,
                        onSwipeToggle = { swipedItem, willBeConfirmed ->
                            registerSwipeFeedback(willBeConfirmed)
                            onConfirmItem(swipedItem)
                        }
                    )
                }
            }

            item {
                ReminderInfoCard(settings = settings)
            }
        }

        burstState?.let { state ->
            CelebrationBurst(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
            )
        }

        AnimatedVisibility(
            visible = comboFeedback != null,
            enter = fadeIn() + scaleIn(initialScale = 0.88f),
            exit = fadeOut() + scaleOut(targetScale = 0.88f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp)
        ) {
            comboFeedback?.let { feedback ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (feedback.comboCount >= 2) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = feedback.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = feedback.subtitle,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeroCard(
    totalCount: Int,
    unreadCount: Int,
    readCount: Int,
    onShuffle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFF7FC3FF),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "오늘의 글귀",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = "밝은 온보딩 카드처럼 오늘의 글귀를 빠르게 정리할 수 있게 구성했습니다. 좌우 스와이프로 읽음 상태를 바로 바꿀 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill(label = "전체", value = totalCount.toString())
                    MetricPill(label = "안읽음", value = unreadCount.toString())
                    MetricPill(label = "읽음", value = readCount.toString())
                }
                Button(onClick = onShuffle) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null
                    )
                    Text("오늘의 글귀 보기")
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$label $value",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun ReadFilterPill(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "$title $count",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReminderInfoCard(settings: ReminderSettings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "반복 노출 설정",
                style = MaterialTheme.typography.titleMedium
            )
            Text("반복 간격: ${formatInterval(settings.intervalMinutes)}")
            Text(
                text = "반복 시간대: ${formatTime(settings.preferredHour, settings.preferredMinute)} ~ " +
                    formatTime(settings.repeatEndHour, settings.repeatEndMinute)
            )
            Text("카테고리 필터: ${settings.categoryFilter.ifBlank { "전체" }}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodaySwipeCard(
    item: ContentItemEntity,
    confirmedToday: Boolean,
    currentTab: HomeReadTab,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    onSwipeToggle: (ContentItemEntity, Boolean) -> Unit
) {
    var swipeHandled by remember(item.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.28f },
        confirmValueChange = { value ->
            when {
                value == SwipeToDismissBoxValue.Settled -> true
                swipeHandled -> true
                else -> {
                    swipeHandled = true
                    onSwipeToggle(item, !confirmedToday)
                    true
                }
            }
        }
    )

    LaunchedEffect(dismissState.currentValue, currentTab) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
            swipeHandled = false
            return@LaunchedEffect
        }

        if (currentTab == HomeReadTab.ALL) {
            dismissState.reset()
            swipeHandled = false
        } else {
            delay(320)
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                dismissState.reset()
                swipeHandled = false
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            SwipeStatusBackground(
                confirmedToday = confirmedToday,
                dismissValue = dismissState.targetValue
            )
        }
    ) {
        ContentItemCard(
            item = item,
            confirmedToday = confirmedToday,
            onToggleFavorite = onToggleFavorite,
            onConfirmItem = onConfirmItem,
            onOpenItem = onOpenItem
        )
    }
}

@Composable
private fun SwipeStatusBackground(
    confirmedToday: Boolean,
    dismissValue: SwipeToDismissBoxValue
) {
    val targetLabel = if (confirmedToday) "읽음 취소" else "읽음 완료"
    val targetIcon = if (confirmedToday) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.DoneAll
    val backgroundBrush = if (confirmedToday) {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer
            )
        )
    }
    val alignment = when (dismissValue) {
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(backgroundBrush)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = targetIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = targetLabel,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CelebrationBurst(
    state: CelebrationBurstState,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val colors = listOf(
        Color(0xFFFFB74D),
        Color(0xFFFF8A65),
        Color(0xFFFFD54F),
        Color(0xFF67C7FF),
        Color(0xFF7BE3D0),
        Color(0xFFFF6F91)
    )

    LaunchedEffect(state.token) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (state.intense) 720 else 560,
                easing = FastOutSlowInEasing
            )
        )
    }

    Canvas(
        modifier = modifier.size(if (state.intense) 220.dp else 180.dp)
    ) {
        val p = progress.value
        val centerPoint = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = size.minDimension * (0.12f + 0.34f * p)
        val alpha = (1f - p).coerceAtLeast(0f)

        drawCircle(
            color = Color.White.copy(alpha = 0.18f * alpha),
            radius = ringRadius,
            center = centerPoint,
            style = Stroke(width = size.minDimension * 0.03f)
        )

        repeat(if (state.intense) 18 else 12) { index ->
            val angle = Math.toRadians(index * (360.0 / if (state.intense) 18 else 12))
            val radius = size.minDimension * (0.08f + 0.34f * p) + (index % 3) * 6f
            val end = Offset(
                x = centerPoint.x + cos(angle).toFloat() * radius,
                y = centerPoint.y + sin(angle).toFloat() * radius
            )
            val start = Offset(
                x = centerPoint.x + cos(angle).toFloat() * (radius * 0.42f),
                y = centerPoint.y + sin(angle).toFloat() * (radius * 0.42f)
            )
            val color = colors[index % colors.size].copy(alpha = alpha)
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = if (state.intense) 8f else 6f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color,
                radius = if (state.intense) 7f else 5f,
                center = end
            )
        }
    }
}

@Composable
private fun EmptyCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun formatInterval(intervalMinutes: Int): String {
    return when {
        intervalMinutes % 60 == 0 -> "${intervalMinutes / 60}시간"
        intervalMinutes > 60 -> "${intervalMinutes / 60}시간 ${intervalMinutes % 60}분"
        else -> "${intervalMinutes}분"
    }
}
