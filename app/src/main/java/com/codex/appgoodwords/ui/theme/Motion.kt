package com.codex.appgoodwords.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 움직임.
 *
 * **한 곳에 모아 둡니다.** 화면마다 제각각 시간을 정하면, 같은 동작인데 어떤 화면에서는
 * 툭 끊기고 어떤 화면에서는 늘어집니다. 그 어긋남이 "만들다 만 앱" 같은 느낌을 만듭니다.
 *
 * 되돌림은 **용수철**로 줍니다. 시간으로 주면 끝에서 갑자기 멈춰 기계 같고, 용수철은 살짝
 * 넘쳤다 돌아와 손이 닿은 것처럼 보입니다. 다만 숫자와 막대에는 넘침을 주지 않습니다 —
 * 점수가 101까지 올랐다 내려오면 틀린 값을 본 것처럼 느껴집니다.
 */
object Motion {
    /** 누르는 것처럼 바로 따라와야 하는 것(눌림·크기). */
    fun <T> press(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** 나타나고 사라지는 것. 살짝 넘쳐야 얹히는 느낌이 납니다. */
    fun <T> enter(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessLow
    )

    /** 값이 차오르는 것(막대·숫자). 넘치면 틀린 값으로 보여서 넘기지 않습니다. */
    fun <T> value(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    /** 화면이 바뀌는 것. 너무 길면 앱이 느리게 느껴집니다. */
    fun <T> screen(): FiniteAnimationSpec<T> = tween(durationMillis = 260)

    /** 축하처럼 한 번 크게 도는 것. */
    fun <T> celebrate(): FiniteAnimationSpec<T> = tween(durationMillis = 900)

    /** 눌렀을 때 줄어드는 크기. 너무 줄이면 장난스러워 보입니다. */
    const val PRESSED_SCALE = 0.97f
}
