package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DailyCoachLine
import com.codex.appgoodwords.data.DailyCoachState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

internal const val coachTalkListTag = "coach_talk_list"
internal const val coachTalkInputTag = "coach_talk_input"
internal const val coachTalkSendTag = "coach_talk_send"
internal const val coachTalkTypingTag = "coach_talk_typing"
internal const val coachTalkRetryTag = "coach_talk_retry"
internal const val coachTalkQuoteTag = "coach_talk_keep_quote"
internal const val coachTalkRoutineTag = "coach_talk_keep_routine"
internal const val coachTalkTipActionTag = "coach_talk_keep_tip_action"

/** 코치의 답 하나에 딸린 "오늘 할 일로". 답이 온 때로 가립니다. */
internal fun coachTalkAnswerActionTag(at: Long): String = "coach_talk_keep_answer_action_$at"

/** 글자가 써지듯 나타나는 데 드는 시간. 카드와 같은 빠르기입니다. */
private const val TALK_REVEAL_MS = 1_400L

/** 새 한마디를 여기서 다 보여 준 뒤 본 것으로 적기까지. 카드와 같습니다. */
private const val TALK_SEEN_AFTER_MS = 2_500L

/**
 * 오늘의 한마디와 나눈 말(사용자가 정함, 2026-10-29 — 카드 + 대화 화면).
 *
 * 홈 카드는 훑는 자리라 주고받는 말은 여기서 합니다. 메신저처럼 코치의 말은 왼쪽, 내 말은 오른쪽에 쌓이고, 입력 칸이
 * 맨 아래 자판 바로 위에 붙습니다(하단 바를 두지 않습니다). **목록을 아래에서부터 쌓습니다** — 자판이 올라와 창이 줄어도
 * 가장 최근 말이 입력 칸 바로 위에 남습니다. 받은 한마디도 온 차례대로 끼어 있어 하루의 흐름이 한눈에 보입니다.
 *
 * 한마디처럼 이 기기에만 두고 자정에 비웁니다. 남길 것은 집어 가야 남습니다 — 지금 한마디의 글귀·루틴·해 볼 일과,
 * 코치의 답에 딸린 해 볼 일("오늘 할 일로").
 */
@Composable
fun DailyCoachTalkScreen(
    state: DailyCoachState,
    modifier: Modifier = Modifier,
    /** 내 말에 답을 받는 중. "답을 쓰는 중"을 띄우고 보내기를 잠급니다 — 답 하나에 물음 하나입니다. */
    replying: Boolean = false,
    /** 한마디를 꺼 두었으면 거짓입니다. 보낼 곳이 없으니 입력 칸을 잠급니다. */
    canReply: Boolean = true,
    onSend: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onKeepAnswerAction: (Long) -> Unit = {},
    onKeepQuote: () -> Unit = {},
    onKeepRoutine: () -> Unit = {},
    onKeepAction: () -> Unit = {},
    /** 새로 온 한마디를 여기서 다 보여 주었습니다. 카드의 반짝임과 홈 탭의 점을 거둡니다. */
    onSeen: () -> Unit = {}
) {
    val lines = state.conversation
    val pending = state.unanswered
    val listState = rememberLazyListState()
    // 화면을 연 뒤에 온 코치의 말만 써지듯 보여 줍니다. 이미 있던 말까지 다시 써지면 읽던 자리를 놓칩니다.
    val openedAt = remember { System.currentTimeMillis() }
    // 다 써진 말은 굴려서 화면 밖에 나갔다 돌아와도 다시 써지지 않게 기억해 둡니다.
    val revealed = remember { mutableSetOf<String>() }
    var draft by rememberSaveable { mutableStateOf("") }
    val canWrite = canReply && state.tip != null

    // 새 말이 붙거나 답을 기다리기 시작하면 맨 아래(가장 최근)로 내립니다.
    LaunchedEffect(lines.size, replying, state.replyError) {
        if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(0)
    }
    // 홈 카드를 거치지 않고 여기서 새 한마디를 보았으면 그것으로 본 것입니다.
    LaunchedEffect(state.tip?.createdAt, state.hasNewTip) {
        if (state.hasNewTip) {
            delay(TALK_REVEAL_MS + TALK_SEEN_AFTER_MS)
            onSeen()
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isBlank() || replying || !canWrite) return
        onSend(text)
        // 보내고 나면 비웁니다. 자판은 내리지 않습니다 — 이어 말하는 일이 많습니다.
        draft = ""
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(coachTalkListTag),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 아래에서부터 쌓으므로 맨 먼저 둔 것이 맨 아래에 옵니다.
            if (pending != null) {
                item(key = "status") {
                    if (replying) {
                        TypingBubble()
                    } else {
                        RetryRow(
                            message = state.replyError.ifBlank { "아직 답을 받지 못했습니다." },
                            onRetry = onRetry,
                            enabled = canWrite
                        )
                    }
                }
            }
            items(items = talkKeys(lines).asReversed(), key = { (key, _) -> key }) { (key, line) ->
                val animate = line.kind != DailyCoachLine.Kind.REPLY && line.at > openedAt && key !in revealed
                Box(modifier = Modifier.padding(vertical = 6.dp)) {
                    when (line.kind) {
                        DailyCoachLine.Kind.REPLY -> MyBubble(line)
                        DailyCoachLine.Kind.TIP -> CoachBubble(line, animate = animate, onRevealed = { revealed += key }) {
                            TipExtras(
                                line = line,
                                current = state.isCurrentTip(line),
                                state = state,
                                onKeepQuote = onKeepQuote,
                                onKeepRoutine = onKeepRoutine,
                                onKeepAction = onKeepAction
                            )
                        }
                        DailyCoachLine.Kind.ANSWER -> CoachBubble(line, animate = animate, onRevealed = { revealed += key }) {
                            if (line.action.isNotBlank()) {
                                SuggestionRow(
                                    title = "해 볼 만한 한 가지",
                                    body = line.action,
                                    label = "오늘 할 일로",
                                    keptLabel = "할 일에 담았습니다",
                                    kept = line.actionKept,
                                    tag = coachTalkAnswerActionTag(line.at),
                                    onKeep = { onKeepAnswerAction(line.at) }
                                )
                            }
                        }
                    }
                }
            }
            item(key = "intro") {
                Text(
                    text = if (lines.isEmpty()) {
                        "오늘 받은 한마디가 아직 없습니다. 홈의 한마디 칸에서 받으면 여기서 답할 수 있습니다."
                    } else {
                        "오늘 받은 한마디와 나눈 말입니다. 이 폰에만 두고 자정에 비웁니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(DailyCoachLine.REPLY_LIMIT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(coachTalkInputTag),
                enabled = canWrite,
                placeholder = {
                    Text(if (canWrite) "코치에게 답하기" else "받은 한마디가 있어야 답할 수 있습니다")
                },
                // 줄을 바꿔 쓸 수 있게 둡니다. 보내기는 옆 버튼이 맡습니다.
                maxLines = 4,
                shape = MaterialTheme.shapes.medium,
                supportingText = if (draft.length > DailyCoachLine.REPLY_LIMIT - 50) {
                    { Text("${draft.length}/${DailyCoachLine.REPLY_LIMIT}") }
                } else {
                    null
                }
            )
            Button(
                onClick = ::send,
                enabled = canWrite && !replying && draft.isNotBlank(),
                modifier = Modifier.testTag(coachTalkSendTag)
            ) {
                Text("보내기")
            }
        }
    }
}

/** 같은 말이 두 줄 생겨도 목록의 열쇠가 겹치지 않게 합니다. 겹치면 목록이 그리다 멈춥니다. */
private fun talkKeys(lines: List<DailyCoachLine>): List<Pair<String, DailyCoachLine>> {
    val used = mutableSetOf<String>()
    return lines.map { line ->
        var key = "${line.kind.name}-${line.at}"
        while (!used.add(key)) key += "+"
        key to line
    }
}

/** 코치의 말. 왼쪽에 붙고 바탕은 카드와 같은 색입니다. 받은 한마디에는 이름을 붙여 답과 가립니다. */
@Composable
private fun CoachBubble(
    line: DailyCoachLine,
    animate: Boolean,
    onRevealed: () -> Unit,
    extras: @Composable () -> Unit
) {
    var shownChars by remember(line.at) { mutableIntStateOf(if (animate) 0 else Int.MAX_VALUE) }
    LaunchedEffect(line.at) {
        val length = line.text.length
        if (shownChars >= length) return@LaunchedEffect
        val step = (TALK_REVEAL_MS / length.coerceAtLeast(1)).coerceIn(8L, 45L)
        while (shownChars < length) {
            delay(step)
            shownChars += 1
        }
        onRevealed()
    }
    val done = shownChars >= line.text.length

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (line.kind == DailyCoachLine.Kind.TIP) {
                Text(
                    text = "오늘의 한마디",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = if (done) line.text else line.text.take(shownChars),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 덧붙은 것(글귀·해 볼 일)은 말이 다 써진 뒤에 보입니다. 쓰는 중에 버튼이 먼저 뜨면 말보다 버튼을 먼저 읽습니다.
            if (done) extras()
        }
        Text(
            text = listOf(talkTime(line.at), line.model).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** 받은 한마디에 딸린 것. 지금 한마디에만 담기 버튼이 있습니다 — 앞선 한마디의 담기 표시는 남아 있지 않습니다. */
@Composable
private fun TipExtras(
    line: DailyCoachLine,
    current: Boolean,
    state: DailyCoachState,
    onKeepQuote: () -> Unit,
    onKeepRoutine: () -> Unit,
    onKeepAction: () -> Unit
) {
    if (line.quote.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "“${line.quote}”",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (line.quoteAuthor.isNotBlank()) {
                Text(
                    text = "- ${line.quoteAuthor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (current) {
                KeepRow(
                    label = "보관함에 담기",
                    kept = state.quoteKept,
                    keptLabel = "보관함에 담았습니다",
                    tag = coachTalkQuoteTag,
                    onKeep = onKeepQuote
                )
            }
        }
    }
    if (line.action.isNotBlank()) {
        if (current) {
            SuggestionRow(
                title = "해 볼 만한 한 가지",
                body = line.action,
                label = "오늘 할 일로",
                keptLabel = "할 일에 담았습니다",
                kept = state.actionKept,
                tag = coachTalkTipActionTag,
                onKeep = onKeepAction
            )
        } else {
            PlainSuggestion(title = "해 볼 만한 한 가지", body = line.action)
        }
    }
    if (line.routine.isNotBlank()) {
        if (current) {
            SuggestionRow(
                title = "새 루틴",
                body = line.routine,
                label = "루틴 추가",
                keptLabel = "루틴에 담았습니다",
                kept = state.routineKept,
                tag = coachTalkRoutineTag,
                onKeep = onKeepRoutine
            )
        } else {
            PlainSuggestion(title = "새 루틴", body = line.routine)
        }
    }
}

@Composable
private fun PlainSuggestion(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = body, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 내 말. 오른쪽에 붙고 앱의 파란 바탕을 엷게 씁니다. */
@Composable
private fun MyBubble(line: DailyCoachLine) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
        Text(
            text = talkTime(line.at),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp)
        )
    }
}

/** 코치가 답을 쓰는 동안. 점 셋이 차례로 숨 쉽니다(메신저의 "입력 중"처럼). */
@Composable
private fun TypingBubble() {
    val pulse = rememberInfiniteTransition(label = "coachTyping")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200), RepeatMode.Restart),
        label = "coachTypingPhase"
    )
    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(coachTalkTypingTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "답을 쓰는 중",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(if (phase.toInt() == index) 1f else 0.3f)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

/** 답을 받지 못한 마지막 말. 왜인지와 다시 받기를 그 말 바로 아래에 둡니다. */
@Composable
private fun RetryRow(message: String, onRetry: () -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.testTag(coachTalkRetryTag)
        ) {
            Text("다시 받기")
        }
    }
}

private fun talkTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
