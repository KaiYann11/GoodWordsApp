package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep

internal const val dailyLoopCardTag = "daily_loop_card"

internal fun dailyLoopStepTag(step: DailyStep) = "daily_loop_step_${step.name}"

/**
 * 오늘의 세 걸음.
 *
 * 글귀 읽기 → 루틴 → 일기를 한자리에 두고, 오늘 어디까지 왔는지 보여 줍니다.
 * 기능은 다 있었지만 서로 떨어져 있어서 무엇부터 할지 매번 정해야 했습니다.
 *
 * **못 한 것을 붉게 세지 않습니다.** 남은 것을 세어 보여 주면 할 일 목록이 되고, 하루라도
 * 빠뜨린 날에는 앱을 열기가 싫어집니다. 다음 한 걸음만 말하고 나머지는 조용히 둡니다.
 *
 * 걸음을 누르면 그 화면으로 갑니다. 알려만 주고 데려가지 않으면 다시 찾아 들어가야 합니다.
 */
@Composable
fun DailyLoopCard(
    progress: DailyProgress,
    onOpenStep: (DailyStep) -> Unit,
    modifier: Modifier = Modifier
) {
    // 걸음을 밟을 때 막대가 차오르는 것이 보여야 방금 한 일이 남았다는 느낌이 듭니다.
    val ratio by animateFloatAsState(targetValue = progress.ratio, label = "dailyLoopRatio")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(dailyLoopCardTag),
        colors = if (progress.isComplete) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("오늘의 세 걸음", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${progress.doneCount} / ${DailyStep.entries.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (progress.isComplete) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth()
            )

            Text(progress.message, style = MaterialTheme.typography.bodyMedium)

            DailyStep.entries.forEach { step ->
                StepRow(
                    step = step,
                    done = step in progress.doneSteps,
                    isNext = step == progress.nextStep,
                    onClick = { onOpenStep(step) }
                )
            }

            // 최고 기록은 이어 가는 중일 때만 둡니다. 0일인 사람에게 "최고 12일"을 보여 주면
            // 격려가 아니라 남과의 비교처럼 읽힙니다.
            if (progress.bestStreakDays > 1 && progress.streakDays > 0) {
                Text(
                    text = "가장 길게 이어 간 날 ${progress.bestStreakDays}일",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StepRow(
    step: DailyStep,
    done: Boolean,
    isNext: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(dailyLoopStepTag(step))
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyLarge,
                // 끝낸 걸음은 흐리게 두어, 남은 것이 저절로 눈에 들어오게 합니다.
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.Unspecified
                }
            )
            // 지금 할 것에만 안내를 답니다. 세 줄이 다 설명을 달고 있으면 어느 것부터인지 모릅니다.
            if (isNext) {
                Text(
                    text = step.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
