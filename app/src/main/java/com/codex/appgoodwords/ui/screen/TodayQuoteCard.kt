package com.codex.appgoodwords.ui.screen

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ReadingCombo
import com.codex.appgoodwords.ui.theme.Motion
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val homeQuoteCardTag = "home_quote_card"
internal const val homeQuoteTextTag = "home_quote_text"
internal const val homeQuoteConfirmTag = "home_quote_confirm"
internal const val homeQuoteNextTag = "home_quote_next"
internal const val homeQuoteAddTag = "home_quote_add"
internal const val homeQuoteSwipeHintTag = "home_quote_swipe_hint"
internal const val homeQuoteRippleTag = "home_quote_ripple"

/**
 * 홈 맨 위의 글귀.
 *
 * @property item 지금의 글귀. 고른 것이 없거나 지워졌으면 null입니다.
 * @property hasCandidates 고를 글귀가 있는지(알림에 보낼 카테고리 안에서). 없으면 "글귀를 담아 보세요"를 띄웁니다.
 * @property filteredOut 글귀는 있는데 알림에 보낼 카테고리에 맞는 것이 없는지. 그때는 담으라고 하지 않고 카테고리를 짚습니다.
 */
data class HomeQuote(
    val item: ContentItemEntity?,
    val hasCandidates: Boolean,
    val filteredOut: Boolean = false
)

/**
 * 홈 글귀를 왼쪽으로 민 결과. 스낵바의 되돌리기가 이것으로 앞 글귀와 읽음을 되돌립니다(사용자가 정함, 2026-11-18).
 *
 * @property previousId 민 글귀.
 * @property readNow 이 밀기로 읽음이 적혔는지. 이미 읽은 글귀였으면 false이고, 되돌려도 읽음은 그대로입니다.
 * @property moved 다른 글귀로 넘어갔는지. 넘길 다른 글귀가 없으면 false입니다.
 */
data class HomeQuoteSwipe(
    val previousId: Long,
    val readNow: Boolean,
    val moved: Boolean
) {
    val message: String
        get() = when {
            readNow && moved -> "읽음으로 적고 다음 글귀로 넘겼습니다."
            moved -> "다음 글귀로 넘겼습니다."
            readNow -> "읽음으로 적었습니다. 넘길 다른 글귀가 없습니다."
            else -> "넘길 다른 글귀가 없습니다."
        }

    /** 되돌릴 것이 있는지. 이미 읽은 글귀 하나뿐이면 민 것으로 바뀐 것이 없습니다. */
    val canUndo: Boolean get() = readNow || moved

    val undoneMessage: String
        get() = when {
            readNow && moved -> "읽음을 거두고 앞 글귀로 돌아갔습니다."
            moved -> "앞 글귀로 돌아갔습니다."
            else -> "읽음을 거뒀습니다."
        }

    /**
     * 이 밀기로 읽었을 때 아래에 띄울 콤보 알약 — 보관함에서 읽을 때와 같은 것에 되돌리기를 답니다(사용자가 고름, 2026-11-18).
     * 넘기기만 했으면 null입니다. 새로 읽은 것이 없어 지금처럼 글로만 알립니다.
     *
     * @param readToday 이 밀기까지 센 오늘 읽은 수.
     */
    internal fun comboVisuals(readToday: Int): ComboSnackbarVisuals? =
        if (readNow) ComboSnackbarVisuals(lead = message, count = readToday, confirmed = true, actionLabel = "되돌리기") else null
}

/**
 * 오늘의 글귀 — 홈에서 가장 먼저 보이는 것.
 *
 * 앱 이름이 "오늘의 글귀"인데 홈에 글귀가 없어, 읽으려면 보관함까지 들어가야 했습니다(사용자 말, 2026-10-28:
 * "메인이 글귀인데 글귀가 잘 보이게"). 위젯·알림과 **같은 글귀**를 둡니다 — 세 곳이 다른 글귀를 말하면 무엇이
 * 오늘의 것인지 알 수 없습니다. "다음 글귀"를 누르면 위젯도 함께 넘어갑니다.
 *
 * **읽는 자리라 글이 주인입니다.** 테두리·그림자·꾸밈을 덜고 글귀만 크게(20sp, 줄 간격 넉넉히) 둡니다.
 * 읽음을 누르면 오늘의 걸음 "글귀 읽기"가 채워집니다 — 보관함에서 누르는 것과 같은 기록입니다.
 * 긴 글은 일곱 줄에서 자르고, 누르면 전체를 봅니다.
 *
 * **카드째 왼쪽으로 밀면 읽음과 다음 글귀입니다**(사용자가 고른 시안, 2026-11-18). 손가락을 따라 카드가 밀리고 뒤에 놓으면
 * 무엇이 되는지가 드러납니다. 3분의 1을 넘기거나 휙 튕기고 놓으면 카드가 왼쪽으로 빠지고 다음 글귀 카드가 오른쪽에서
 * 들어옵니다. 덜 밀면 제자리로 돌아옵니다. 이미 읽은 글귀는 넘기기만 합니다 — 읽음 단추는 다시 누르면 거두지만, 미는 것은
 * 넘기는 몸짓입니다. 오른쪽으로는 밀리지 않습니다. 단추는 그대로 두고, 화면 읽기 프로그램에는 같은 일을 하는 동작을 답니다.
 *
 * **읽으면 보관함처럼 콤보가 뜹니다**(사용자가 고름, 2026-11-18). 밀어 읽거나 읽음을 누르면 카드 자리에 잔물결이 번지고, 아래
 * 콤보 알약은 앱이 스낵바 자리에 띄웁니다([ComboSnackbarVisuals]) — 밀었을 때의 되돌리기와 한 자리를 써야 겹치지 않습니다.
 *
 * @param quote null이면 아직 읽는 중입니다. 자리만 잡아 두어 글귀가 들어올 때 아래가 밀려 내려가지 않게 합니다.
 * @param onSwipe 왼쪽으로 밀어 놓았습니다. 읽음과 넘기기는 받는 쪽이 합니다. 카드는 다음 글귀가 오기를 기다렸다 들입니다.
 */
@Composable
fun TodayQuoteCard(
    quote: HomeQuote?,
    confirmedToday: Boolean,
    onConfirm: (ContentItemEntity) -> Unit,
    onNext: () -> Unit,
    onSwipe: (ContentItemEntity) -> Unit,
    onOpen: (ContentItemEntity) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** 바뀔 때마다 잠깐 테두리를 둘러 어디인지 알려 줍니다(걸음의 "글귀 읽기"를 눌러 왔을 때). 0이면 두르지 않습니다. */
    pulseKey: Int = 0,
    /** 오늘 읽은 글귀 수(보관함 콤보와 같은 수). 이번에 읽어 이정표에 닿으면 잔물결이 크게 번집니다. */
    readToday: Int = 0
) {
    var lit by remember(pulseKey) { mutableStateOf(pulseKey > 0) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        delay(PULSE_MILLIS)
        lit = false
    }
    val ring by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "homeQuoteRing"
    )

    val item = quote?.item
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val gap = with(density) { SWIPE_GAP.toPx() }
    val flingVelocity = with(density) { FLING_VELOCITY.toPx() }
    val flingDistance = with(density) { FLING_MIN_DISTANCE.toPx() }
    // 카드가 손가락을 따라 옮겨 간 거리(px). 0이 제자리이고 왼쪽이 음수입니다.
    var offsetX by remember { mutableFloatStateOf(0f) }
    var cardWidth by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(SwipePhase.REST) }
    var springBack by remember { mutableStateOf<Job?>(null) }
    // 밀어 보낸 글귀. 카드가 화면 밖으로 다 나갈 때까지 이것을 보여 줍니다 — 다음 글귀가 일찍 와도 떠나는 카드에 비치지 않게.
    var leaving by remember { mutableStateOf<ContentItemEntity?>(null) }
    // 띠에 적을 말은 민 순간의 것입니다. 떠나는 사이에 읽음이 적혀도 "읽음"이 띠에서 빠지지 않게.
    var leavingReads by remember { mutableStateOf(false) }
    val latestItem by rememberUpdatedState(item)
    val latestConfirmed by rememberUpdatedState(confirmedToday)
    val showHint by remember { derivedStateOf { offsetX < 0f || phase != SwipePhase.REST } }

    val shown = leaving ?: item
    // 떠나는 카드는 읽은 모양입니다 — 이 밀기로 읽음이 적히거나 이미 읽은 글귀입니다.
    val shownConfirmed = leaving != null || confirmedToday

    // 읽었을 때 카드 자리에 번지는 잔물결(보관함과 같은 것). 카드가 떠나고 들어와도 그 자리에서 번집니다.
    var ripple by remember { mutableStateOf<QuoteRipple?>(null) }
    val latestReadToday by rememberUpdatedState(readToday)
    LaunchedEffect(ripple?.token) {
        val token = ripple?.token ?: return@LaunchedEffect
        delay(COMBO_PILL_MILLIS)
        if (ripple?.token == token) ripple = null
    }

    fun rippleForRead() {
        // 읽음이 DB를 거쳐 돌아오기 전이라 이번 것까지 더해 이정표에 닿는지 봅니다.
        ripple = QuoteRipple(
            token = SystemClock.elapsedRealtimeNanos(),
            milestone = ReadingCombo.of(latestReadToday + 1).isMilestone
        )
    }

    fun settle(velocity: Float) {
        val swiped = latestItem
        val width = cardWidth.toFloat()
        val farEnough = offsetX < -width * SWIPE_FRACTION
        val flung = velocity < -flingVelocity && offsetX < -flingDistance
        if (swiped == null || width <= 0f || !(farEnough || flung)) {
            springBack = scope.launch {
                animate(offsetX, 0f, velocity, Motion.enter()) { value, _ -> offsetX = value }
            }
            return
        }
        leavingReads = !latestConfirmed
        leaving = swiped
        phase = SwipePhase.LEAVING
        if (leavingReads) {
            haptics.commit()
            rippleForRead()
        } else {
            haptics.tap()
        }
        // 다음 글귀를 고르는 동안 카드가 빠집니다. 다 빠질 즈음이면 대개 이미 와 있습니다.
        onSwipe(swiped)
        scope.launch {
            animate(offsetX, -(width + gap), velocity, Motion.screen()) { value, _ -> offsetX = value }
            // 넘길 다른 글귀가 없으면 같은 글귀 그대로라, 잠깐 기다렸다 도로 들입니다.
            withTimeoutOrNull(NEXT_WAIT_MILLIS) { snapshotFlow { latestItem?.id }.first { it != swiped.id } }
            phase = SwipePhase.ARRIVING
            leaving = null
            offsetX = width + gap
            animate(offsetX, 0f, 0f, Motion.screen()) { value, _ -> offsetX = value }
            phase = SwipePhase.REST
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                state = rememberDraggableState { delta ->
                    if (phase == SwipePhase.REST) offsetX = (offsetX + delta).coerceAtMost(0f)
                },
                orientation = Orientation.Horizontal,
                enabled = item != null,
                onDragStarted = { if (phase == SwipePhase.REST) springBack?.cancel() },
                onDragStopped = { velocity -> if (phase == SwipePhase.REST) settle(velocity) }
            )
    ) {
        if (showHint) {
            SwipeHint(
                readsToo = if (phase == SwipePhase.REST) !confirmedToday else leavingReads,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        // 놓으면 넘어가는 자리(3분의 1)에서 다 보입니다. 떠나고 들어오는 동안은 그대로 둡니다.
                        alpha = when {
                            phase != SwipePhase.REST -> 1f
                            cardWidth == 0 -> 0f
                            else -> (-offsetX / (cardWidth * SWIPE_FRACTION)).coerceIn(0f, 1f)
                        }
                    }
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { cardWidth = it.width }
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = ring), MaterialTheme.shapes.large)
                .testTag(homeQuoteCardTag),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 위쪽만 옅은 파랑으로 물들여 다른 카드와 갈라 보이게 합니다. 글자 뒤는 흰 바탕이라 읽기에 거슬리지 않습니다.
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                        )
                    )
                    .heightIn(min = 168.dp)
            ) {
                Column(
                    modifier = Modifier.padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "오늘의 글귀",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (shownConfirmed && shown != null) {
                            ReadTodayMark()
                        }
                    }

                    when {
                        shown != null -> {
                            // 글귀가 바뀔 때 툭 갈리지 않고 스미듯 바뀝니다. 밀어서 넘긴 글귀는 카드째 들어오므로 안에서 또 스미지 않습니다.
                            AnimatedContent(
                                targetState = shown,
                                contentKey = { it.id },
                                transitionSpec = {
                                    if (phase == SwipePhase.ARRIVING) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        fadeIn(Motion.screen()) togetherWith fadeOut(Motion.screen())
                                    }
                                },
                                label = "homeQuote"
                            ) { target ->
                                QuoteBody(
                                    item = target,
                                    onOpen = onOpen,
                                    swipeLabel = if (shownConfirmed) "다음 글귀로 넘기기" else "읽음으로 적고 다음 글귀로 넘기기",
                                    onSwipe = { onSwipe(target) }
                                )
                            }
                            QuoteActions(
                                item = shown,
                                confirmedToday = shownConfirmed,
                                onConfirm = { tapped ->
                                    // 밀어 읽을 때와 같이 읽으면 기록이 남는 무게로 울리고 잔물결이 번집니다. 거둘 때는 가볍게만.
                                    if (shownConfirmed) {
                                        haptics.tap()
                                    } else {
                                        haptics.commit()
                                        rippleForRead()
                                    }
                                    onConfirm(tapped)
                                },
                                onNext = onNext
                            )
                        }

                        // 아직 읽는 중이거나 고르는 중입니다. 빈 안내를 잠깐 비추지 않습니다.
                        quote == null || quote.hasCandidates -> Unit

                        quote.filteredOut -> Text(
                            text = "알림에 보낼 카테고리에 맞는 글귀가 없습니다. 설정 › 알림에서 카테고리를 바꾸면 여기에 뜹니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp, bottom = 10.dp)
                        )

                        else -> Column(
                            modifier = Modifier.padding(end = 8.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "마음에 닿은 글귀를 담아 두면 여기서 날마다 다시 만납니다.",
                                style = QuoteTextStyle.copy(fontSize = 17.sp, lineHeight = 27.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(onClick = onAdd, modifier = Modifier.testTag(homeQuoteAddTag)) {
                                Text("글귀 담기")
                            }
                        }
                    }
                }
            }
        }
        // 카드 위에 번집니다. 카드 자리에 맞춘 상자 안에 두어 카드 크기를 바꾸지 않고, 상자를 넘치는 고리는 그대로 그립니다.
        ripple?.let { wave ->
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                CelebrationPulse(
                    token = wave.token,
                    milestone = wave.milestone,
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .testTag(homeQuoteRippleTag)
                )
            }
        }
    }
}

/** 밀어 넘기는 카드가 어디쯤인지. 떠나고 들어오는 동안은 다시 밀리지 않습니다. */
private enum class SwipePhase { REST, LEAVING, ARRIVING }

/** 읽었을 때 카드 자리에 번지는 잔물결 한 번. 이정표에 닿았으면 크게 번집니다. */
private data class QuoteRipple(val token: Long, val milestone: Boolean)

/**
 * 카드를 밀 때 뒤에서 드러나는 띠. 놓으면 무엇이 되는지 적습니다.
 *
 * 카드가 3분의 1쯤 비켜났을 때 다 보이도록 글을 좁게 쌓습니다 — 거기서 놓으면 넘어갑니다.
 */
@Composable
private fun SwipeHint(readsToo: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.horizontalGradient(
                    listOf(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.primaryContainer)
                )
            )
            .semantics(mergeDescendants = true) {}
            .testTag(homeQuoteSwipeHintTag)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (readsToo) Icons.Outlined.DoneAll else Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = if (readsToo) "읽음" else "다음 글귀",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (readsToo) {
                Text(
                    text = "+ 다음 글귀",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** 글귀 본문. 누르면 전체를 봅니다. */
@Composable
private fun QuoteBody(
    item: ContentItemEntity,
    onOpen: (ContentItemEntity) -> Unit,
    swipeLabel: String,
    onSwipe: () -> Unit
) {
    // 글귀는 본문이 주인이고, 링크·영상은 제목이 주인입니다(본문은 설명이나 주소인 경우가 많습니다).
    val isQuote = item.type == ContentType.QUOTE
    val main = if (isQuote) item.body.ifBlank { item.title } else item.title.ifBlank { item.body }
    val heading = item.title.trim().takeIf { isQuote && it.isNotBlank() && item.body.isNotBlank() && !item.body.trim().startsWith(it.removeSuffix("…")) }
    val detail = item.body.trim().takeIf { !isQuote && item.title.isNotBlank() && it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "글귀 전체 보기") { onOpen(item) }
            // 밀기는 화면 읽기 프로그램으로 할 수 없어 같은 일을 하는 동작을 글귀에 답니다.
            .semantics { customActions = listOf(CustomAccessibilityAction(swipeLabel) { onSwipe(); true }) }
            // 누르는 자리에 답니다. 안의 글자들은 누르는 자리 하나로 합쳐져 따로 짚이지 않습니다.
            .testTag(homeQuoteTextTag)
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        heading?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = main,
            style = QuoteTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = MAX_QUOTE_LINES,
            overflow = TextOverflow.Ellipsis
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.author.isNotBlank()) {
            Text(
                text = "— ${item.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuoteActions(
    item: ContentItemEntity,
    confirmedToday: Boolean,
    onConfirm: (ContentItemEntity) -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 읽음이 이 카드의 할 일입니다. 읽고 나면 눌러 둔 모양으로 남고, 다시 누르면 거둡니다(잘못 눌렀을 때).
        if (confirmedToday) {
            OutlinedButton(onClick = { onConfirm(item) }, modifier = Modifier.testTag(homeQuoteConfirmTag)) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("읽었어요")
            }
        } else {
            Button(onClick = { onConfirm(item) }, modifier = Modifier.testTag(homeQuoteConfirmTag)) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("읽음")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onNext, modifier = Modifier.testTag(homeQuoteNextTag)) {
            Text("다음 글귀")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** 오늘 읽은 글귀라는 표시. 못 읽은 것을 붉게 세지 않고, 읽은 것에만 조용히 답니다. */
@Composable
private fun ReadTodayMark() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "오늘 읽음",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * 글귀 글자.
 *
 * 본문(14~16sp)보다 한 단 크고 굵기는 중간입니다. 굵게 하면 긴 글귀가 덩어리로 보여 오히려 읽히지 않고,
 * 줄 간격을 넉넉히 두어야 한글 문장이 숨을 쉽니다.
 */
private val QuoteTextStyle = TextStyle(
    fontSize = 20.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.3).sp
)

/** 이보다 길면 자르고 눌러서 봅니다. 홈의 아래 칸이 화면 밖으로 밀려나지 않게. */
private const val MAX_QUOTE_LINES = 7

/** 테두리를 두르고 있는 시간. 계속 남으면 다음에 홈을 열 때 왜 둘려 있는지 알 수 없습니다. */
private const val PULSE_MILLIS = 1_600L

/** 카드 너비의 이만큼을 밀고 놓으면 넘어갑니다(사용자가 고른 시안, 2026-11-18). 덜 밀면 제자리로 돌아옵니다. */
private const val SWIPE_FRACTION = 1f / 3f

/** 1초에 이보다 빠르게 튕기면 덜 밀었어도 넘어갑니다. */
private val FLING_VELOCITY = 800.dp

/** 튕겨 넘기려면 적어도 이만큼은 밀어야 합니다. 위아래로 굴리다 옆으로 살짝 쏠린 손이 글귀를 넘기지 않게. */
private val FLING_MIN_DISTANCE = 48.dp

/** 화면 밖으로 다 나가도록 카드 너비에 더하는 거리. 홈의 옆 여백(20dp)보다 넉넉히 둡니다. */
private val SWIPE_GAP = 32.dp

/** 다음 글귀를 기다리는 가장 긴 시간. 넘길 다른 글귀가 없으면 같은 글귀가 이만큼 뒤에 돌아옵니다. */
private const val NEXT_WAIT_MILLIS = 1_000L
