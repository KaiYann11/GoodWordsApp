package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 개수를 달고 있는 거르개 알약.
 *
 * 글귀(읽음)와 루틴(수행)이 같은 모양을 씁니다. 두 화면에서 "무엇을 걸렀는지"와
 * "몇 개나 되는지"를 같은 자리에서 읽게 하려는 것입니다.
 * 개수를 함께 적는 이유는, 눌러 보기 전에 빈 목록인지 알 수 있어야 하기 때문입니다.
 */
@Composable
internal fun CountFilterPill(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "$title $count",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}
