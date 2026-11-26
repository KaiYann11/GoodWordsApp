package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DayComparisons
import com.codex.appgoodwords.data.DayStrip
import com.codex.appgoodwords.ui.theme.Indigo
import com.codex.appgoodwords.ui.theme.IndigoDeep
import com.codex.appgoodwords.ui.theme.IndigoSoft
import com.codex.appgoodwords.ui.theme.Ink
import com.codex.appgoodwords.ui.theme.VioletSoft

internal const val homeCompareStripTag = "home_compare_strip"

/**
 * 어제보다 띠(사용자가 고름, 2026-11-27 — 글귀 아래 한 줄 띠). 홈 글귀 카드와 칸 이름 줄 사이에 놓이고, 누르면 어제와 오늘
 * 화면입니다. 무엇을 적을지는 [DayComparisons.strip]이 정합니다 — 오늘이 어제보다 많거나 같은 것만("루틴 +1 · 글귀 = ·
 * 걸음 +1,240"), 없으면 "어제처럼 오늘도 …", 오늘 아직 아무것도 없으면 수 없이 "나란히 봅니다".
 *
 * 앞선 것이 있으면 빛이 띠를 한 번 스칩니다(글이 바뀔 때마다 한 번). 늘 반짝이면 금세 성가셔서 한 번만입니다.
 */
@Composable
fun DayCompareStrip(strip: DayStrip, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val text = DayComparisons.stripText(strip)
    val glowing = strip is DayStrip.Ahead && animationsEnabled()
    val sweep = remember { Animatable(1f) }
    LaunchedEffect(text, glowing) {
        if (!glowing) return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(1f, animationSpec = tween(durationMillis = 1_300, easing = FastOutSlowInEasing))
    }
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .softShadow(Indigo.copy(alpha = 0.5f), shape, elevation = 8.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(IndigoSoft, VioletSoft)))
            .drawWithContent {
                drawContent()
                val value = sweep.value
                if (value < 1f) {
                    // 비스듬한 빛줄기가 왼쪽에서 오른쪽으로 지나갑니다.
                    val x = size.width * (value * 1.4f - 0.2f)
                    val band = size.height * 2f
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent),
                            start = Offset(x - band, 0f),
                            end = Offset(x + band, size.height)
                        )
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.tap()
                onOpen()
            }
            .testTag(homeCompareStripTag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Indigo,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "어제보다",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = IndigoDeep
        )
        RisingText(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
        Text(text = "›", style = MaterialTheme.typography.titleMedium, color = IndigoDeep)
    }
}
