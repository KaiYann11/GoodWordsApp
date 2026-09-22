package com.codex.appgoodwords.ui.screen

import android.view.SoundEffectConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import com.codex.appgoodwords.ui.theme.Motion

/**
 * 손에 닿는 느낌.
 *
 * 화면마다 제각각 진동을 울리면 어떤 곳은 세고 어떤 곳은 없어서, 같은 무게의 일이 다르게
 * 느껴집니다. 그래서 **무게를 세 가지로만 나눠** 여기에 모읍니다.
 *
 * - [tap] 고르기·넘기기처럼 되돌리기 쉬운 것
 * - [commit] 저장·체크처럼 기록이 남는 것
 * - [celebrate] 하루를 다 채운 것처럼 드물고 기쁜 것
 *
 * 진동은 **한 동작에 한 번**입니다. 누를 때와 결과가 돌아올 때 두 번 울리면 고장 난 것처럼
 * 느껴집니다. 결과가 있는 동작은 결과 쪽에서 울립니다.
 */
class Haptics internal constructor(
    private val perform: (HapticFeedbackType) -> Unit,
    private val click: () -> Unit
) {
    fun tap() = perform(HapticFeedbackType.TextHandleMove)

    fun commit() {
        click()
        perform(HapticFeedbackType.LongPress)
    }

    fun celebrate() {
        click()
        perform(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(haptic, view) {
        Haptics(
            perform = { type -> haptic.performHapticFeedback(type) },
            click = { view.playSoundEffect(SoundEffectConstants.CLICK) }
        )
    }
}

/**
 * 누르면 살짝 눌립니다.
 *
 * 탭 하나에 화면이 아무 반응도 하지 않으면, 눌렸는지 알 수 없어 한 번 더 누르게 됩니다.
 * 물결(ripple)은 손가락에 가려 잘 안 보이지만, 카드 전체가 줄어드는 것은 가려지지 않습니다.
 *
 * 누름을 받는 [interactionSource]를 함께 넘겨야 합니다. clickable에 준 것과 같은 것이어야
 * 눌린 것을 압니다.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) Motion.PRESSED_SCALE else 1f,
        animationSpec = Motion.press(),
        label = "pressScale"
    )
    return this.scale(scale)
}
