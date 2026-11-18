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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ComboLevel
import com.codex.appgoodwords.data.ReadingCombo

/** 콤보 알약과 잔물결이 떠 있는 시간(보관함·홈). 칭찬은 짧아야 하루에 수십 번 보아도 성가시지 않습니다. */
internal const val COMBO_PILL_MILLIS = 1_600L

internal const val comboSnackbarTag = "combo_snackbar"

/**
 * 글귀 한 장을 읽었을 때의 되돌림.
 *
 * 소리·진동·잔물결·콤보 띠가 한 덩어리로 움직여야 해서 한 파일에 둡니다.
 * 이 되돌림은 읽기를 따라갑니다 — 보관함에서 읽어도, 홈 맨 위의 글귀를 밀거나 읽음을 눌러도 뜹니다
 * (홈은 되돌리기와 한 자리를 쓰려고 스낵바로 — [ComboSnackbarVisuals]).
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
internal fun ComboBanner(
    combo: ComboLevel,
    confirmed: Boolean,
    modifier: Modifier = Modifier,
    /** 알약 오른쪽 끝에 둘 것. 홈에서 밀어 읽었을 때의 되돌리기가 여기에 옵니다. */
    action: (@Composable () -> Unit)? = null
) {
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
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            // 단추는 제 안쪽 여백이 있어 끝 여백을 줄입니다. 그대로 두면 되돌리기가 알약 가운데로 몰려 보입니다.
            modifier = Modifier.padding(start = 18.dp, end = if (action != null) 8.dp else 22.dp, top = 12.dp, bottom = 12.dp),
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
            action?.invoke()
        }
    }
}

/**
 * 콤보를 담은 아래 알림 — 홈에서 글귀를 읽었을 때 띄웁니다(사용자가 고름, 2026-11-18).
 *
 * 보관함은 알약을 목록 위에 따로 띄우지만, 홈에서 밀어 읽으면 같은 자리에 되돌리기 알림이 뜹니다. 둘이 따로 뜨면 겹쳐서,
 * 알약을 스낵바로 띄우고 되돌리기를 그 오른쪽 끝에 답니다. 그러면 아래 알림은 늘 하나입니다. 그리는 것은 [AppSnackbar]입니다.
 *
 * @property lead 무엇을 했는지. 화면 읽기 프로그램이 콤보와 함께 읽습니다. 알약에는 콤보만 보입니다.
 * @property count 이번 것까지 센 오늘 읽은 수.
 * @property confirmed 읽음을 적었는지. 거뒀으면 알약에 "읽음 취소"라고 적습니다.
 */
internal data class ComboSnackbarVisuals(
    val lead: String,
    val count: Int,
    val confirmed: Boolean,
    override val actionLabel: String? = null
) : SnackbarVisuals {
    val combo: ComboLevel get() = ReadingCombo.of(count)

    /** 알약의 숫자가 옮겨 오는 수. 보관함 알약처럼 하나 오르고(거뒀으면 내리고) 멈춥니다. */
    val countBefore: Int get() = if (confirmed) (count - 1).coerceAtLeast(0) else count + 1

    override val message: String get() = "$lead ${combo.title}, ${combo.message}."
    override val duration: SnackbarDuration get() = SnackbarDuration.Short
    override val withDismissAction: Boolean get() = false

    companion object {
        /** 홈의 읽음 단추. 되돌리기를 달지 않습니다 — 단추를 다시 누르면 거둡니다. */
        fun toggled(confirmed: Boolean, count: Int) = ComboSnackbarVisuals(
            lead = if (confirmed) "읽음으로 적었습니다." else "읽음을 거뒀습니다.",
            count = count,
            confirmed = confirmed
        )
    }
}

/** 앱의 아래 알림. 콤보를 담은 것([ComboSnackbarVisuals])은 보관함과 같은 알약으로, 나머지는 여느 스낵바로 그립니다. */
@Composable
internal fun AppSnackbar(data: SnackbarData) {
    val combo = data.visuals as? ComboSnackbarVisuals
    if (combo != null) ComboSnackbar(data, combo) else Snackbar(data)
}

@Composable
private fun ComboSnackbar(data: SnackbarData, visuals: ComboSnackbarVisuals) {
    // 앞의 수로 한 번 그리고 곧바로 지금의 수로 옮깁니다. 보관함 알약처럼 숫자가 오르는 것이 보입니다.
    var shown by remember(visuals) { mutableIntStateOf(visuals.countBefore) }
    LaunchedEffect(visuals) { shown = visuals.count }
    val label = visuals.actionLabel
    ComboBanner(
        combo = ReadingCombo.of(shown),
        confirmed = visuals.confirmed,
        modifier = Modifier
            .padding(12.dp)
            // 숫자와 글은 한 번에 읽고, 무엇을 했는지부터 들려줍니다. 되돌리기는 제 단추로 따로 짚입니다.
            .semantics(mergeDescendants = true) { contentDescription = visuals.message }
            .testTag(comboSnackbarTag),
        action = if (label == null) null else {
            { TextButton(onClick = { data.performAction() }) { Text(label) } }
        }
    )
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
