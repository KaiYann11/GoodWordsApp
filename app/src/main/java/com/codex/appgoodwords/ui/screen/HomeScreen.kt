package com.codex.appgoodwords.ui.screen

import android.os.SystemClock
import android.view.SoundEffectConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Surface
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
import com.codex.appgoodwords.data.ComboLevel
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.data.ReadingCombo
import com.codex.appgoodwords.data.ReminderSettings
import kotlin.random.Random
import kotlinx.coroutines.delay

private enum class HomeReadTab(
    val label: String
) {
    ALL("전체"),
    UNREAD("안읽은 것"),
    READ("읽은 것")
}

/**
 * 스와이프 한 번에 대한 피드백.
 *
 * 개수는 여기에 담지 않습니다. 확인 결과가 DB를 거쳐 돌아오는 데 잠깐 걸려서,
 * 누를 때 세면 실제와 어긋납니다. 화면은 [ReadingCombo]로 그때그때 계산해 보여 줍니다.
 */
private data class ComboFeedback(
    val token: Long,
    val confirmed: Boolean
)

@Composable
fun HomeScreen(
    todayItems: List<ContentItemEntity>,
    settings: ReminderSettings,
    confirmedTodayIds: Set<Long>,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    /** 오늘의 세 걸음. null이면 카드를 두지 않습니다. */
    dailyLoop: DailyProgress? = null,
    /** 걸음을 누르면 그 화면으로 데려갑니다. 알려만 주면 다시 찾아 들어가야 합니다. */
    onOpenStep: (DailyStep) -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeReadTab.UNREAD.name) }
    var shuffleSeed by rememberSaveable { mutableLongStateOf(0L) }
    var comboFeedback by remember { mutableStateOf<ComboFeedback?>(null) }

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

    // 오늘 확인한 글귀 수가 곧 콤보입니다. 빨리 넘기는 것과는 상관이 없습니다.
    val combo = remember(confirmedTodayIds.size) { ReadingCombo.of(confirmedTodayIds.size) }

    LaunchedEffect(comboFeedback?.token) {
        val token = comboFeedback?.token ?: return@LaunchedEffect
        delay(1600)
        if (comboFeedback?.token == token) {
            comboFeedback = null
        }
    }

    fun registerSwipeFeedback(nowConfirmed: Boolean) {
        // 확인 결과가 아직 안 돌아왔으므로 이번 스와이프까지 더해 봅니다.
        // 진동 세기를 고르는 데만 쓰고, 화면에 보이는 숫자는 실제 상태에서 가져옵니다.
        val expected = if (nowConfirmed) confirmedTodayIds.size + 1 else confirmedTodayIds.size

        view.playSoundEffect(SoundEffectConstants.CLICK)
        // 이정표에서만 무겁게 울립니다. 매번 세게 울리면 금세 성가십니다.
        haptic.performHapticFeedback(
            if (nowConfirmed && ReadingCombo.of(expected).isMilestone) {
                HapticFeedbackType.LongPress
            } else {
                HapticFeedbackType.TextHandleMove
            }
        )

        comboFeedback = ComboFeedback(token = SystemClock.elapsedRealtime(), confirmed = nowConfirmed)
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

            // 앱을 열면 가장 먼저 보이는 자리입니다. 오늘 무엇부터 할지 여기서 정해집니다.
            dailyLoop?.let { progress ->
                item {
                    DailyLoopCard(progress = progress, onOpenStep = onOpenStep)
                }
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
    }
}

/**
 * 오늘 몇 개째인지 알려 주는 알약.
 *
 * 숫자는 실제 상태에서 오므로, 저장이 끝나면 자연스럽게 올라갑니다.
 * 막대는 다음 이정표까지의 거리라, 콤보가 "빠르기"가 아니라 "쌓임"으로 읽힙니다.
 */
@Composable
private fun ComboBanner(combo: ComboLevel, confirmed: Boolean) {
    val animatedCount by animateIntAsState(
        targetValue = combo.count,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "comboCount"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = combo.progressToNext,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "comboProgress"
    )
    val accent = if (combo.isMilestone) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = animatedCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (confirmed) combo.title else "읽음 취소",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = combo.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 다음 이정표까지 얼마나 왔는지. 다 지났으면 굳이 빈 막대를 두지 않습니다.
                if (combo.nextMilestone != null) {
                    Canvas(modifier = Modifier.size(width = 132.dp, height = 3.dp)) {
                        drawLine(
                            color = accent.copy(alpha = 0.18f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = size.height,
                            cap = StrokeCap.Round
                        )
                        if (animatedProgress > 0f) {
                            drawLine(
                                color = accent,
                                start = Offset(0f, size.height / 2f),
                                end = Offset(size.width * animatedProgress, size.height / 2f),
                                strokeWidth = size.height,
                                cap = StrokeCap.Round
                            )
                        }
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

/**
 * 확인했을 때 번지는 잔물결.
 *
 * 예전에는 여섯 가지 색의 폭죽이 터졌습니다. 한 번은 즐겁지만 하루에 수십 번 보면 시끄럽고,
 * 앱의 다른 화면과도 따로 놀았습니다. 지금은 테마 색으로 얇은 고리만 번지게 하고,
 * 이정표일 때만 고리를 늘려 무게를 줍니다.
 */
@Composable
private fun CelebrationPulse(
    token: Long,
    milestone: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val ringColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.tertiary

    LaunchedEffect(token) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (milestone) 1_100 else 760,
                easing = LinearOutSlowInEasing
            )
        )
    }

    Canvas(modifier = modifier.size(if (milestone) 260.dp else 200.dp)) {
        val centerPoint = Offset(size.width / 2f, size.height / 2f)
        val ringCount = if (milestone) 3 else 1

        repeat(ringCount) { index ->
            // 뒤 고리는 조금 늦게 출발해 물결처럼 번집니다.
            val head = index * 0.16f
            val local = ((progress.value - head) / (1f - head)).coerceIn(0f, 1f)
            if (local <= 0f) return@repeat

            val radius = size.minDimension * (0.10f + 0.40f * local)
            val fade = (1f - local) * (1f - index * 0.22f)
            drawCircle(
                color = (if (index == 0) ringColor else accentColor).copy(alpha = 0.38f * fade),
                radius = radius,
                center = centerPoint,
                // 번져 나가면서 선이 가늘어져야 사라지는 것처럼 보입니다.
                style = Stroke(width = size.minDimension * (0.018f - 0.012f * local))
            )
        }

        // 가운데에서 옅게 퍼지는 빛. 고리만 있으면 가운데가 비어 허전합니다.
        val glowRadius = size.minDimension * (0.18f + 0.26f * progress.value)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ringColor.copy(alpha = 0.20f * (1f - progress.value)),
                    Color.Transparent
                ),
                center = centerPoint,
                radius = glowRadius
            ),
            radius = glowRadius,
            center = centerPoint
        )
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
