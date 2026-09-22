package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.codex.appgoodwords.ui.theme.Motion

/**
 * 숫자가 올라가는 것이 보이게.
 *
 * 값이 툭 바뀌면 방금 무엇이 달라졌는지 못 보고 지나칩니다. 차오르는 것을 보아야 "내가 한 일"로
 * 느껴집니다. 점수·연속 날수처럼 **오르는 것이 보람인 숫자**에만 씁니다 — 목록 개수까지 굴리면
 * 화면이 쉴 새 없이 움직여 어지럽습니다.
 */
@Composable
fun CountUpNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    suffix: String = ""
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = Motion.value(),
        label = "countUp"
    )
    Text(text = "$animated$suffix", modifier = modifier, style = style, color = color)
}

/**
 * 바뀐 글이 아래에서 올라옵니다.
 *
 * 한 줄 안내가 소리 없이 갈리면 못 봅니다. 올라오면서 바뀌면 "방금 달라졌다"가 보입니다.
 * 올라오는 쪽은 아래에서, 나가는 쪽은 위로 — 쌓여 올라가는 방향이라야 나아가는 느낌이 납니다.
 */
@Composable
fun RisingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(Motion.enter()) { height -> height / 2 } + fadeIn(Motion.enter()))
                .togetherWith(
                    slideOutVertically(Motion.enter()) { height -> -height / 2 } + fadeOut(Motion.enter())
                )
                // 글 길이가 달라도 칸이 툭 늘지 않게 함께 늘립니다.
                .using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "risingText"
    ) { value ->
        Text(text = value, style = style, color = color)
    }
}
