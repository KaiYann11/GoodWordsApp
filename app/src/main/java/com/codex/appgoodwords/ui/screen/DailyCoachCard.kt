package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DailyCoachLine
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.RoutineNudge
import com.codex.appgoodwords.ui.theme.Amber
import com.codex.appgoodwords.ui.theme.Indigo
import com.codex.appgoodwords.ui.theme.Mint
import com.codex.appgoodwords.ui.theme.Motion
import com.codex.appgoodwords.ui.theme.Violet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

internal const val dailyCoachCardTag = "home_daily_coach"
internal const val dailyCoachRefreshTag = "home_daily_coach_refresh"
internal const val dailyCoachQuoteTag = "home_daily_coach_quote"
internal const val dailyCoachRoutineTag = "home_daily_coach_routine"
internal const val dailyCoachActionTag = "home_daily_coach_action"

/** AI를 부르지 않고 기기에서 센 한 줄. */
internal const val dailyCoachNudgeTag = "home_daily_coach_nudge"
internal const val dailyCoachNudgeOpenTag = "home_daily_coach_nudge_open"

/** 아직 보지 않은 한마디라는 표시. */
internal const val dailyCoachNewBadgeTag = "home_daily_coach_new"

/** "다음 한마디 15:20쯤". */
internal const val dailyCoachNextTag = "home_daily_coach_next"

/** 대화 화면으로 가는 "답하기". */
internal const val dailyCoachReplyTag = "home_daily_coach_reply"

/** 지금 한마디 뒤로 마지막으로 주고받은 한 쌍. */
internal const val dailyCoachExchangeTag = "home_daily_coach_exchange"

/** 새 한마디가 다 써진 뒤 본 것으로 적기까지 보여 두는 시간. 다 읽기 전에 반짝임이 꺼지면 무엇이 새 것이었는지 놓칩니다. */
private const val SEEN_AFTER_MS = 2_500L

/** 글자가 써지듯 나타나는 데 드는 시간. 길면 기다리게 되고, 짧으면 효과로 보이지 않습니다. */
private const val REVEAL_MS = 1_400L

/**
 * 오늘의 한마디.
 *
 * 하루가 흘러가는 동안 오늘 한 것을 보고 건네는 말입니다. 돌아보기 카드([GrowthTeaserCard])는
 * 지난 기간을 정리해 둔 글로 가는 문이고, 이것은 **지금 이 자리에서 읽고 마는 말**입니다.
 * 그래서 눌러 들어갈 곳이 없습니다.
 *
 * **켜 둔 사람에게만 보입니다.** 값이 드는 길이라, 끄고 쓰는 사람의 홈에 매일 권유를
 * 띄우지 않습니다.
 *
 * 권해 받은 것은 집어 가야 남습니다 — 글귀는 보관함으로, 루틴은 루틴으로, 해 볼 일은
 * 오늘 할 일로. **한 번 담으면 버튼이 사라집니다.** 스치듯 누르는 자리라 두 번 눌리면
 * 같은 것이 두 벌 생깁니다.
 *
 * **새로 온 한마디는 눈에 띄게 알립니다**(사용자가 정함, 2026-10-19). 평범한 카드 하나로는 왔는지조차
 * 몰랐습니다. 상용 앱들이 "아직 안 본 것"을 알리는 방식을 빌렸습니다 — 둘레를 도는 빛(스토리 테두리처럼),
 * 색 그림자, 깜빡이는 "새 한마디" 점, 용수철로 얹히는 등장, 글자가 써지듯 나타나는 본문(채팅 앱의 답처럼).
 * 다 보여 준 뒤 본 것으로 적고([onSeen]) 거둡니다 — 늘 반짝이면 무엇이 새 것인지 알 수 없습니다.
 * 진동은 넣지 않습니다. 진동은 사용자가 한 동작에만 답합니다(`Feedback.kt`).
 *
 * **받은 한마디에 답할 수 있습니다**(사용자가 정함, 2026-10-29 — 카드 + 대화 화면). 주고받는 말은 대화 화면
 * ([DailyCoachTalkScreen])에서 하고, 카드에는 마지막 한 쌍만 두 줄씩 보입니다. 홈은 훑는 자리라 대화가 길어져도
 * 카드가 늘어나지 않습니다.
 */
@Composable
fun DailyCoachCard(
    state: DailyCoachState,
    /** 물어보는 중. 기다리는 표시를 띄웁니다. */
    loading: Boolean = false,
    /** 내 말에 답을 받는 중. 마지막 한 쌍 자리에 "답을 쓰는 중"을 적습니다. */
    replying: Boolean = false,
    /** 오늘 한 것이 있는지. 없으면 물을 거리가 없다고 적어 둡니다. */
    hasRecordToday: Boolean = true,
    /**
     * 아직 안 밟은 루틴 한 줄. 꺼 두었거나 루틴이 없으면 null입니다.
     *
     * **AI를 부르지 않고 기기에서 센 것입니다.** 그래서 연결이 없어도, 하루 물을 횟수를
     * 다 썼어도 뜹니다 — 이 카드가 값을 치러야만 쓸모 있는 자리가 되지 않게 합니다.
     */
    nudge: RoutineNudge? = null,
    /**
     * 다음 한마디가 저절로 올 때([com.codex.appgoodwords.work.DailyCoachSchedule.nextAskAt]). 모르거나 지났으면 null입니다.
     *
     * 정한 주기마다 온다는 것이 카드에 보여야 앱을 닫아 두고도 기다릴 수 있습니다. 남은 횟수는 세지 않고 다음 하나만 말합니다.
     */
    nextAskAt: Long? = null,
    onOpenRoutines: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onKeepQuote: () -> Unit = {},
    onKeepRoutine: () -> Unit = {},
    onKeepAction: () -> Unit = {},
    /** 새로 온 한마디를 다 보여 주었습니다. 반짝임과 홈 탭의 점을 거둡니다. */
    onSeen: () -> Unit = {},
    /** 대화 화면을 엽니다. 받은 한마디가 있을 때만 버튼이 있습니다. */
    onReply: () -> Unit = {}
) {
    val tip = state.tip
    val isNew = state.hasNewTip
    val shape = MaterialTheme.shapes.large

    // 반짝임은 새 것일 때 차오르고 본 뒤에 사그라듭니다. 툭 꺼지면 눈이 뭔가 사라졌다고만 느낍니다.
    val highlight by animateFloatAsState(
        targetValue = if (isNew) 1f else 0f,
        animationSpec = Motion.value(),
        label = "coachHighlight"
    )
    val glow = rememberInfiniteTransition(label = "coachGlow")
    val sweep by glow.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2_400, easing = LinearEasing)),
        label = "coachGlowSweep"
    )
    val pulse by glow.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "coachPulse"
    )

    // 새 한마디가 오면 살짝 작아졌다가 용수철로 얹힙니다.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(tip?.createdAt, isNew) {
        if (isNew) {
            pop.snapTo(0.94f)
            pop.animateTo(1f, Motion.enter())
        }
    }

    // 본문은 새 것일 때만 글자가 써지듯 나타납니다. 이미 본 것은 처음부터 다 보입니다.
    var shownChars by remember(tip?.createdAt) { mutableIntStateOf(if (isNew) 0 else Int.MAX_VALUE) }
    LaunchedEffect(tip?.createdAt) {
        val length = tip?.message?.length ?: return@LaunchedEffect
        if (shownChars >= length) return@LaunchedEffect
        val step = (REVEAL_MS / length.coerceAtLeast(1)).coerceIn(8L, 45L)
        while (shownChars < length) {
            delay(step)
            shownChars += 1
        }
    }
    val revealed = tip == null || shownChars >= tip.message.length
    LaunchedEffect(tip?.createdAt, isNew, revealed) {
        if (isNew && revealed) {
            delay(SEEN_AFTER_MS)
            onSeen()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            // 잘라 내지 않습니다. 잘라 내면 뒤에 그리는 빛 테두리의 바깥 절반이 사라집니다.
            .shadow(
                elevation = (14 * highlight).dp,
                shape = shape,
                clip = false,
                ambientColor = Indigo,
                spotColor = Violet
            )
            .glowBorder(progress = sweep, alpha = highlight, shape = shape)
            .testTag(dailyCoachCardTag),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "오늘의 한마디",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isNew) {
                    NewTipBadge(pulse = pulse)
                }
                Box(modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag(dailyCoachRefreshTag)
                    ) {
                        Text(if (tip == null) "받아 보기" else "다시 받기")
                    }
                }
            }

            when {
                tip != null -> Text(
                    text = if (revealed) tip.message else tip.message.take(shownChars),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 받아 둔 한마디가 없어도 루틴 한 줄이 아래에서 제 몫을 합니다.
                // 그 위에 "받아 볼 수 있습니다"까지 적으면 같은 자리에서 두 번 말합니다.
                nudge != null -> Unit

                // 아직 아무것도 안 한 아침입니다. 못 했다고 말하지 않고, 무엇이 있어야
                // 말을 건넬 수 있는지만 적습니다.
                !hasRecordToday -> Text(
                    text = "오늘 한 걸음을 밟으면 그것을 보고 한마디 건네 드립니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> Text(
                    text = "오늘 한 것을 보고 한마디 받아 볼 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (tip != null && tip.quote.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "“${tip.quote}”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (tip.quoteAuthor.isNotBlank()) {
                        Text(
                            text = "- ${tip.quoteAuthor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    KeepRow(
                        label = "보관함에 담기",
                        kept = state.quoteKept,
                        keptLabel = "보관함에 담았습니다",
                        tag = dailyCoachQuoteTag,
                        onKeep = onKeepQuote
                    )
                }
            }

            if (tip != null && tip.action.isNotBlank()) {
                SuggestionRow(
                    title = "해 볼 만한 한 가지",
                    body = tip.action,
                    label = "오늘 할 일로",
                    keptLabel = "할 일에 담았습니다",
                    kept = state.actionKept,
                    tag = dailyCoachActionTag,
                    onKeep = onKeepAction
                )
            }

            if (tip != null && tip.routine.isNotBlank()) {
                SuggestionRow(
                    title = "새 루틴",
                    body = tip.routine,
                    label = "루틴 추가",
                    keptLabel = "루틴에 담았습니다",
                    kept = state.routineKept,
                    tag = dailyCoachRoutineTag,
                    onKeep = onKeepRoutine
                )
            }

            // 무엇에 답하는지가 있어야 대화가 됩니다. 받은 한마디가 없으면 버튼을 두지 않습니다.
            if (tip != null) {
                LastExchange(state = state, replying = replying)
                FilledTonalButton(
                    onClick = onReply,
                    modifier = Modifier.testTag(dailyCoachReplyTag)
                ) {
                    Text(
                        if (state.exchangeCount == 0) "답하기" else "답하기 · 주고받은 말 ${state.exchangeCount}"
                    )
                }
            }

            // AI가 써 준 것 아래에 둡니다. 읽고 나서 바로 할 수 있는 것이 마지막에 오도록.
            nudge?.let { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().testTag(dailyCoachNudgeTag),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = line.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.kind == RoutineNudge.Kind.STREAK) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                    // 다 밟은 날에는 데려갈 곳이 없습니다. 칭찬 한 줄로 끝냅니다.
                    if (line.hasPlaceToGo) {
                        TextButton(
                            onClick = onOpenRoutines,
                            modifier = Modifier.testTag(dailyCoachNudgeOpenTag)
                        ) {
                            Text("밟으러 가기")
                        }
                    }
                }
            }

            if (state.lastError.isNotBlank()) {
                Text(
                    text = state.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val upcoming = nextAskAt?.takeIf { it > System.currentTimeMillis() }
            if (tip != null || upcoming != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = listOf(tip?.let { formatTime(it.createdAt) }.orEmpty(), tip?.model.orEmpty())
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    upcoming?.let { at ->
                        Text(
                            text = "다음 한마디 ${formatTime(at)}쯤",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag(dailyCoachNextTag)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 지금 한마디 뒤로 마지막으로 주고받은 한 쌍 — 내 말 하나와 그 답. 주고받은 것이 없으면 아무것도 두지 않습니다.
 *
 * 두세 줄에서 자릅니다. 나머지는 대화 화면에서 봅니다.
 */
@Composable
private fun LastExchange(state: DailyCoachState, replying: Boolean) {
    val since = state.talkSinceTip
    val replyIndex = since.indexOfLast { it.kind == DailyCoachLine.Kind.REPLY }
    if (replyIndex < 0) return
    val reply = since[replyIndex]
    val answer = since.drop(replyIndex + 1).firstOrNull { it.kind == DailyCoachLine.Kind.ANSWER }
    val failed = answer == null && !replying && state.replyError.isNotBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(dailyCoachExchangeTag),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "나: ${reply.text}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "코치: " + when {
                answer != null -> answer.text
                replying -> "답을 쓰는 중…"
                failed -> "답을 받지 못했습니다. 대화에서 다시 받을 수 있습니다."
                else -> "답을 기다리고 있습니다."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "새 한마디" 표시. 점이 숨 쉬듯 깜빡여 아직 안 본 것이 있다고 알립니다. */
@Composable
private fun NewTipBadge(pulse: Float) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag(dailyCoachNewBadgeTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .alpha(pulse)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text = "새 한마디",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 카드 둘레를 도는 빛.
 *
 * 색은 앱이 이미 쓰는 넷(파랑·보라·민트·주황)만 씁니다. 새 색을 들이면 이 카드만 다른 앱처럼 보입니다.
 * [alpha]가 0이면 아무것도 그리지 않습니다.
 */
private fun Modifier.glowBorder(progress: Float, alpha: Float, shape: Shape): Modifier =
    if (alpha <= 0f) this else drawWithContent {
        drawContent()
        val shift = size.width * 2f * progress
        val brush = Brush.linearGradient(
            colors = listOf(Indigo, Violet, Mint, Amber, Indigo),
            start = Offset(shift, 0f),
            end = Offset(shift + size.width, size.height),
            tileMode = TileMode.Mirror
        )
        drawOutline(
            outline = shape.createOutline(size, layoutDirection, this),
            brush = brush,
            alpha = alpha,
            style = Stroke(width = 2.5.dp.toPx())
        )
    }

/** 권해 받은 것 한 줄과 담기 버튼. 대화 화면의 한마디·답도 같은 꼴을 씁니다. */
@Composable
internal fun SuggestionRow(
    title: String,
    body: String,
    label: String,
    keptLabel: String,
    kept: Boolean,
    tag: String,
    onKeep: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
        KeepRow(label = label, kept = kept, keptLabel = keptLabel, tag = tag, onKeep = onKeep)
    }
}

/** 담은 뒤에는 버튼을 거두고 담았다고만 적습니다. 두 번 눌러 두 벌이 생기지 않게 합니다. */
@Composable
internal fun KeepRow(
    label: String,
    kept: Boolean,
    keptLabel: String,
    tag: String,
    onKeep: () -> Unit
) {
    if (kept) {
        Text(
            text = keptLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        OutlinedButton(onClick = onKeep, modifier = Modifier.testTag(tag)) {
            Text(label)
        }
    }
}

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
