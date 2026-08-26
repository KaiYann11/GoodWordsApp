package com.codex.appgoodwords.ui.screen

import android.os.SystemClock
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.codex.appgoodwords.data.ReadingCombo

/**
 * 글귀 한 장을 읽었을 때의 되돌림.
 *
 * 소리·진동·잔물결·콤보 띠가 한 덩어리로 움직여야 해서 한 파일에 둡니다.
 * 읽는 자리가 홈에서 보관함으로 옮겨 갔지만, 이 되돌림은 읽기를 따라갑니다.
 */
internal data class ComboFeedback(
    val token: Long,
    val confirmed: Boolean
)

/**
 * 스와이프 한 번에 대한 소리·진동을 울리고, 띄울 되돌림을 만들어 줍니다.
 *
 * 개수는 여기에 담지 않습니다. 확인 결과가 DB를 거쳐 돌아오는 데 잠깐 걸려서,
 * 누를 때 세면 실제와 어긋납니다. 화면은 [ReadingCombo]로 그때그때 계산해 보여 줍니다.
 */
@Composable
internal fun rememberSwipeFeedback(confirmedCount: Int): (Boolean) -> ComboFeedback {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    return { nowConfirmed ->
        // 확인 결과가 아직 안 돌아왔으므로 이번 스와이프까지 더해 봅니다.
        // 진동 세기를 고르는 데만 쓰고, 화면에 보이는 숫자는 실제 상태에서 가져옵니다.
        val expected = if (nowConfirmed) confirmedCount + 1 else confirmedCount

        view.playSoundEffect(SoundEffectConstants.CLICK)
        // 이정표에서만 무겁게 울립니다. 매번 세게 울리면 금세 성가십니다.
        haptic.performHapticFeedback(
            if (nowConfirmed && ReadingCombo.of(expected).isMilestone) {
                HapticFeedbackType.LongPress
            } else {
                HapticFeedbackType.TextHandleMove
            }
        )

        ComboFeedback(token = SystemClock.elapsedRealtime(), confirmed = nowConfirmed)
    }
}

@Composable
internal fun ComboBanner(combo: ComboLevel, confirmed: Boolean) {
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

/**
 * 확인했을 때 번지는 잔물결.
 *
 * 예전에는 여섯 가지 색의 폭죽이 터졌습니다. 한 번은 즐겁지만 하루에 수십 번 보면 시끄럽고,
 * 앱의 다른 화면과도 따로 놀았습니다. 지금은 테마 색으로 얇은 고리만 번지게 하고,
 * 이정표일 때만 고리를 늘려 무게를 줍니다.
 */
@Composable
internal fun CelebrationPulse(
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
