package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DailyProgress
import com.codex.appgoodwords.data.DailyStep
import com.codex.appgoodwords.ui.theme.Motion

internal const val dailyLoopCardTag = "daily_loop_card"

internal fun dailyLoopStepTag(step: DailyStep) = "daily_loop_step_${step.name}"

/**
 * 오늘의 걸음.
 *
 * 하루의 축으로 삼기로 한 걸음을 한자리에 두고, 오늘 어디까지 왔는지 보여 줍니다.
 * 기능은 다 있었지만 서로 떨어져 있어서 무엇부터 할지 매번 정해야 했습니다.
 * 무엇을 축으로 삼을지는 설정에서 고릅니다. 여기서는 [DailyProgress.steps]를 그대로 놓습니다.
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(dailyLoopCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "오늘의 ${progress.stepCount}걸음",
                        style = MaterialTheme.typography.titleLarge
                    )
                    // 한 줄 격려는 걸음을 밟을 때마다 바뀝니다. 올라오면서 바뀌어야 눈에 띕니다.
                    RisingText(
                        text = progress.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ProgressRing(progress = progress)
            }

            // 고른 걸음만, 고른 순서대로. 무엇을 축으로 삼을지는 사람마다 다릅니다.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                progress.steps.forEach { step ->
                    StepRow(
                        step = step,
                        done = step in progress.doneSteps,
                        isNext = step == progress.nextStep,
                        onClick = { onOpenStep(step) }
                    )
                }
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

/**
 * 오른쪽 위의 고리.
 *
 * 가로 막대를 고리로 바꿨습니다. 막대는 카드를 가로질러 글과 걸음 사이를 잘라 놓는데,
 * 고리는 제목 옆에 놓여 한 덩어리로 읽힙니다. 가운데 숫자가 "몇 걸음 중 몇"을 대신합니다.
 *
 * 다 밟으면 고리가 색으로 차고 [CelebrationPulse]가 한 번 번집니다. 하루를 채운 순간은
 * 이 앱에서 가장 드물고 기쁜 일인데, 그동안 막대가 끝까지 찬 것 말고는 아무 일도 없었습니다.
 */
@Composable
private fun ProgressRing(progress: DailyProgress) {
    val ratio by animateFloatAsState(
        targetValue = progress.ratio,
        animationSpec = Motion.value(),
        label = "dailyLoopRatio"
    )
    val accent by animateColorAsState(
        targetValue = if (progress.isComplete) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = Motion.value(),
        label = "dailyLoopAccent"
    )
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .size(72.dp)
            // 고리와 숫자를 한 덩어리로 읽어 줍니다. 조각조각 읽히면 "1", "/3"이 따로 들립니다.
            .semantics(mergeDescendants = true) {
                contentDescription = "${progress.stepCount}걸음 중 ${progress.doneCount}걸음"
            },
        contentAlignment = Alignment.Center
    ) {
        // 다 밟은 순간에만 번집니다. 걸음마다 번지면 금세 성가십니다.
        AnimatedVisibility(
            visible = progress.isComplete,
            enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.6f),
            exit = fadeOut()
        ) {
            CelebrationPulse(
                token = progress.doneCount.toLong(),
                milestone = progress.streakDays > 1,
                modifier = Modifier.size(72.dp)
            )
        }
        Canvas(modifier = Modifier.size(72.dp)) {
            val stroke = size.minDimension * 0.11f
            val inset = stroke / 2f
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (ratio > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            CountUpNumber(
                value = progress.doneCount,
                style = MaterialTheme.typography.titleLarge,
                color = accent
            )
            Text(
                text = "/${progress.stepCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
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
    val colors = stepColors(step)
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = when {
            isNext -> colors.soft
            else -> Color.Transparent
        },
        animationSpec = Motion.value(),
        label = "stepBackground"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .testTag(dailyLoopStepTag(step))
            .heightIn(min = 56.dp)
            .semantics { stateDescription = if (done) "완료" else if (isNext) "다음 걸음" else "아직 시작 전" }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        StepMark(colors = colors, done = done, isNext = isNext)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyLarge,
                // 끝낸 걸음은 흐리게 두어, 남은 것이 저절로 눈에 들어오게 합니다.
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
            )
            // 지금 할 것에만 안내를 답니다. 모든 줄이 설명을 달고 있으면 어느 것부터인지 모릅니다.
            if (isNext) {
                Text(
                    text = step.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = if (isNext) colors.accent else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 걸음 앞의 동그라미. 밟으면 색이 차고 체크가 튀어 올라옵니다. */
@Composable
private fun StepMark(colors: StepColors, done: Boolean, isNext: Boolean) {
    val fill by animateColorAsState(
        targetValue = if (done) colors.accent else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = Motion.value(),
        label = "stepMarkFill"
    )
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = 1.5.dp,
                color = if (done) colors.accent else if (isNext) colors.accent else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = done,
            enter = scaleIn(Motion.press()) + fadeIn(Motion.press()),
            exit = fadeOut()
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
