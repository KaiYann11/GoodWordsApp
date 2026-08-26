package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import kotlinx.coroutines.delay

/**
 * 좌우로 밀어 읽음을 바꾸는 글귀 카드.
 *
 * 버튼을 누르는 것보다 손이 덜 갑니다. 글귀는 하루에 여러 장을 넘기게 되므로
 * 그 차이가 쌓입니다.
 *
 * [staysInList]는 민 뒤에도 이 카드가 목록에 남는지입니다. 남는다면(전체 보기) 곧바로
 * 제자리로 돌려놓고, 사라진다면(안읽은 것·읽은 것만 보기) 잠깐 기다렸다 되돌립니다.
 * 곧바로 되돌리면 사라지는 카드가 한 번 튀어 보입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuoteSwipeCard(
    item: ContentItemEntity,
    confirmedToday: Boolean,
    staysInList: Boolean,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    onSwipeToggle: (ContentItemEntity, Boolean) -> Unit
) {
    var swipeHandled by remember(item.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.28f },
        confirmValueChange = { value ->
            when {
                value == SwipeToDismissBoxValue.Settled -> true
                swipeHandled -> true
                else -> {
                    swipeHandled = true
                    onSwipeToggle(item, !confirmedToday)
                    true
                }
            }
        }
    )

    LaunchedEffect(dismissState.currentValue, staysInList) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) {
            swipeHandled = false
            return@LaunchedEffect
        }

        if (staysInList) {
            dismissState.reset()
            swipeHandled = false
        } else {
            delay(320)
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                dismissState.reset()
                swipeHandled = false
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            SwipeStatusBackground(
                confirmedToday = confirmedToday,
                dismissValue = dismissState.targetValue
            )
        }
    ) {
        ContentItemCard(
            item = item,
            confirmedToday = confirmedToday,
            onToggleFavorite = onToggleFavorite,
            onConfirmItem = onConfirmItem,
            onOpenItem = onOpenItem
        )
    }
}

@Composable
private fun SwipeStatusBackground(
    confirmedToday: Boolean,
    dismissValue: SwipeToDismissBoxValue
) {
    val targetLabel = if (confirmedToday) "읽음 취소" else "읽음 완료"
    val targetIcon = if (confirmedToday) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.DoneAll
    val backgroundBrush = if (confirmedToday) {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer
            )
        )
    }
    val alignment = when (dismissValue) {
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(backgroundBrush)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = targetIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = targetLabel,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
