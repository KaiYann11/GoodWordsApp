package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DailyCoachState
import com.codex.appgoodwords.data.RoutineNudge
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val dailyCoachCardTag = "home_daily_coach"
internal const val dailyCoachRefreshTag = "home_daily_coach_refresh"
internal const val dailyCoachQuoteTag = "home_daily_coach_quote"
internal const val dailyCoachRoutineTag = "home_daily_coach_routine"
internal const val dailyCoachActionTag = "home_daily_coach_action"

/** AI를 부르지 않고 기기에서 센 한 줄. */
internal const val dailyCoachNudgeTag = "home_daily_coach_nudge"
internal const val dailyCoachNudgeOpenTag = "home_daily_coach_nudge_open"

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
 */
@Composable
fun DailyCoachCard(
    state: DailyCoachState,
    /** 물어보는 중. 기다리는 표시를 띄웁니다. */
    loading: Boolean = false,
    /** 오늘 한 것이 있는지. 없으면 물을 거리가 없다고 적어 둡니다. */
    hasRecordToday: Boolean = true,
    /**
     * 아직 안 밟은 루틴 한 줄. 꺼 두었거나 루틴이 없으면 null입니다.
     *
     * **AI를 부르지 않고 기기에서 센 것입니다.** 그래서 연결이 없어도, 하루 물을 횟수를
     * 다 썼어도 뜹니다 — 이 카드가 값을 치러야만 쓸모 있는 자리가 되지 않게 합니다.
     */
    nudge: RoutineNudge? = null,
    onOpenRoutines: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onKeepQuote: () -> Unit = {},
    onKeepRoutine: () -> Unit = {},
    onKeepAction: () -> Unit = {}
) {
    val tip = state.tip

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(dailyCoachCardTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "오늘의 한마디",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
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
                    text = tip.message,
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

            if (tip != null) {
                Text(
                    text = listOf(formatTime(tip.createdAt), tip.model)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
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
private fun KeepRow(
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
