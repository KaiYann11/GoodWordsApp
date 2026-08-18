package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

/**
 * 검색에서 고른 기록을 찾아가게 도와줍니다.
 *
 * 탭만 바꿔 놓으면 찾아 놓고도 사용자가 다시 목록을 뒤져야 합니다.
 * 그 자리로 굴려 주고, 잠깐 테두리를 둘러 어느 것인지 알려 줍니다.
 *
 * 테두리는 몇 초 뒤에 사라집니다. 계속 남으면 다음에 그 화면을 열 때마다
 * 왜 강조돼 있는지 알 수 없습니다.
 */
private const val HIGHLIGHT_MILLIS = 2_600L

/** 그 자리로 굴립니다. 목록에 없으면(지웠거나 걸러졌으면) 아무것도 하지 않습니다. */
@Composable
fun ScrollToFocus(listState: LazyListState, index: Int?, key: Any?) {
    LaunchedEffect(key, index) {
        if (index == null || index < 0) return@LaunchedEffect
        // 화면이 그려질 틈을 줍니다. 바로 부르면 아직 높이를 몰라 엉뚱한 자리로 갑니다.
        delay(120)
        runCatching { listState.animateScrollToItem(index) }
    }
}

/**
 * 잠깐 테두리를 둘러 강조합니다.
 *
 * @param focused 이 카드가 찾던 것인지.
 */
@Composable
fun Modifier.focusHighlight(focused: Boolean, key: Any? = null): Modifier {
    var visible by remember(key, focused) { mutableStateOf(focused) }

    LaunchedEffect(key, focused) {
        if (!focused) return@LaunchedEffect
        visible = true
        delay(HIGHLIGHT_MILLIS)
        visible = false
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "focusHighlight"
    )
    if (alpha <= 0f) return this

    return border(
        width = 2.dp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        shape = RoundedCornerShape(12.dp)
    )
}
