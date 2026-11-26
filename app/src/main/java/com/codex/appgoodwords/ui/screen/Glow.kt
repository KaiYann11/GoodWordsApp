package com.codex.appgoodwords.ui.screen

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/*
 * 은은한 빛과 깊이(사용자가 고름, 2026-11-27). 화면마다 제각각 빛을 그리면 어떤 곳은 번쩍이고 어떤 곳은 밋밋해서, 빛과 떠오름을
 * 여기에 모읍니다.
 *
 * - [softShadow] 카드 아래에 색이 스민 옅은 그림자 — 흰 카드가 바탕에서 떠 보입니다.
 * - [aurora] 글귀 카드에 천천히 흐르는 빛.
 * - [riseIn] 칸을 넘기면 카드가 차례로 떠오름.
 * - [burst] 끝낸 자리에서 빛이 한 번 터짐(루틴 수행 +1, 할 일 끝냄).
 *
 * **움직임을 끈 기기에서는 멈춥니다**([animationsEnabled]). 흐르는 빛은 `rememberInfiniteTransition`으로만 그립니다 — Compose
 * 시험은 이 무한 움직임을 기다리지 않고, 값은 그리기 단계에서만 읽어 화면을 다시 짜지 않습니다.
 */

/** 기기의 애니메이션 배율이 0(움직임 줄이기)이 아니면 true. */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f
    }
}

/**
 * 색이 스민 옅은 그림자. 안드로이드 9 아래에서는 색 없이 회색 그림자입니다(색 그림자를 그릴 수 없음).
 * 카드와 같은 [shape]를 줘야 모서리를 따라 번집니다.
 */
fun Modifier.softShadow(color: Color, shape: Shape, elevation: Dp = 14.dp): Modifier =
    this.shadow(elevation = elevation, shape = shape, clip = false, ambientColor = color, spotColor = color)

/** 글귀 카드에 깔리는 빛의 빛깔. 글자를 가리지 않게 옅게, 위쪽에 모입니다. */
val auroraColors: List<Color> = listOf(
    Color(0xFF3B5BFF).copy(alpha = 0.22f),
    Color(0xFF8B5CF6).copy(alpha = 0.18f),
    Color(0xFF0EA5E9).copy(alpha = 0.16f)
)

/**
 * 천천히 흐르는 빛. 큰 빛 세 덩이가 [cycleMillis]에 한 바퀴씩 카드 위쪽을 떠다닙니다. [base]를 먼저 칠하고 그 위에 그립니다.
 */
@Composable
fun Modifier.aurora(base: Brush, colors: List<Color> = auroraColors, cycleMillis: Int = 18_000): Modifier {
    val phase: State<Float>? = if (animationsEnabled()) {
        rememberInfiniteTransition(label = "aurora").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = cycleMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "auroraPhase"
        )
    } else {
        null
    }
    return this.drawBehind {
        drawRect(base)
        drawAuroraBlobs(phase?.value ?: 0.12f, colors)
    }
}

private fun DrawScope.drawAuroraBlobs(phase: Float, colors: List<Color>) {
    val turn = phase * 2f * PI.toFloat()
    val width = size.width
    val height = size.height
    val radius = size.maxDimension * 0.62f
    // 사인·코사인이라 한 바퀴가 끝나도 툭 끊기지 않고 이어집니다.
    val centers = listOf(
        Offset(width * (0.18f + 0.16f * cos(turn)), height * (0.08f + 0.14f * sin(turn))),
        Offset(width * (0.84f + 0.12f * sin(turn + 1.3f)), height * (0.18f + 0.16f * cos(turn + 1.3f))),
        Offset(width * (0.52f + 0.20f * cos(turn + 2.6f)), height * (0.02f + 0.10f * sin(turn + 2.6f)))
    )
    colors.zip(centers).forEach { (color, center) ->
        drawCircle(
            brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
            radius = radius,
            center = center
        )
    }
}

/**
 * 떠오름. 처음 그려질 때 [index]번째만큼 늦게 아래에서 떠오릅니다(한 칸에 0.06초, 여덟째부터는 같게).
 * [key]가 바뀌면 다시 떠오릅니다.
 *
 * [shadowColor]를 주면 같은 층에 색 그림자를 함께 그립니다. 그림자를 다른 층에 두면 떠오르는 동안 잘리거나(반투명한 층은
 * 제 크기 밖을 그리지 않음) 카드보다 먼저 떠서, 그림자도 카드와 함께 짙어지게 한 층에 둡니다. [shape]는 카드와 같아야 합니다.
 */
@Composable
fun Modifier.riseIn(
    index: Int,
    key: Any? = Unit,
    shadowColor: Color? = null,
    shape: Shape? = null,
    elevation: Dp = 14.dp
): Modifier {
    val moving = animationsEnabled()
    val progress = remember(key) { Animatable(if (moving) 0f else 1f) }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            delay(index.coerceIn(0, 8) * 60L)
            progress.animateTo(1f, animationSpec = Motion.enter())
        }
    }
    return this.graphicsLayer {
        val value = progress.value
        val shown = value.coerceIn(0f, 1f)
        alpha = shown
        translationY = (1f - value) * 24.dp.toPx()
        if (shadowColor != null && shape != null) {
            this.shape = shape
            clip = false
            shadowElevation = elevation.toPx() * shown
            ambientShadowColor = shadowColor
            spotShadowColor = shadowColor
        }
    }
}

/**
 * [riseIn]을 받지 않는 카드를 감쌉니다. **카드가 그려질 때만** 부르세요 — 빈 상자도 칸 사이 간격을 차지합니다.
 */
@Composable
fun RiseIn(
    index: Int,
    key: Any? = Unit,
    shadowColor: Color? = null,
    shape: Shape? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.riseIn(index = index, key = key, shadowColor = shadowColor, shape = shape)) {
        content()
    }
}

/**
 * 끝낸 자리에서 빛이 한 번 터집니다. [trigger]가 바뀔 때마다 0.7초 — 번지는 고리, 흩어지는 반짝이 여덟, 가운데 빛.
 * 처음 그릴 때(0)는 터지지 않습니다. 자리 밖으로 번져도 되지만, 감싼 카드가 자르면 거기까지입니다.
 */
@Composable
fun Modifier.burst(trigger: Int, color: Color): Modifier {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing))
    }
    return this.drawWithContent {
        drawContent()
        val value = progress.value
        if (value >= 1f) return@drawWithContent
        val fade = 1f - value
        val center = Offset(size.width / 2f, size.height / 2f)
        val reach = (size.minDimension * 1.4f).coerceAtMost(72.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = 0.28f * fade), Color.Transparent), center = center, radius = reach * 0.7f),
            radius = reach * 0.7f,
            center = center
        )
        drawCircle(
            color = color.copy(alpha = 0.42f * fade),
            radius = reach * (0.35f + 0.65f * value),
            center = center,
            style = Stroke(width = 3.dp.toPx() * fade + 0.5f)
        )
        repeat(8) { index ->
            val angle = index / 8f * 2f * PI.toFloat() + 0.4f
            val distance = reach * (0.4f + 0.75f * value)
            drawCircle(
                color = color.copy(alpha = 0.85f * fade),
                radius = 2.5.dp.toPx() * (1f - 0.5f * value),
                center = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance)
            )
        }
    }
}
