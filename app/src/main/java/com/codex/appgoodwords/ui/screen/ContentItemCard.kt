package com.codex.appgoodwords.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentItemCard(
    item: ContentItemEntity,
    confirmedToday: Boolean,
    onToggleFavorite: (ContentItemEntity) -> Unit,
    onConfirmItem: (ContentItemEntity) -> Unit,
    onOpenItem: (ContentItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    showTypeChip: Boolean = true
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            // 카드 전체가 눌리는 것이 보여야 탭이 먹었는지 압니다. 물결은 손가락에 가립니다.
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) { onOpenItem(item) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (confirmedToday) "오늘 읽음 완료" else "오늘 읽을 후보",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showTypeChip) {
                            CapsuleTag(
                                text = item.type.displayLabel(),
                                backgroundColor = MaterialTheme.colorScheme.primaryContainer,
                                textColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        item.category.takeIf { it.isNotBlank() }?.let { category ->
                            CapsuleTag(
                                text = category,
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                                textColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (item.body.isNotBlank()) {
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.author.isNotBlank()) {
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoText("읽음 처리 ${item.showCount}회")
                    if (confirmedToday) {
                        InfoText("오늘 읽음")
                    }
                }

                if (item.tags.isNotEmpty()) {
                    Text(
                        text = "태그: ${item.tags.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.sourceUrl.isNotBlank()) {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Text(text = "링크 열기")
                    }
                }

                // 제목은 온전히 읽게 두고, 읽음·공유·즐겨찾기는 본문 아래에서 고릅니다.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            // 읽었다는 기록이 남는 일입니다. 취소는 고치는 일이라 가볍게 둡니다.
                            if (confirmedToday) haptics.tap() else haptics.commit()
                            onConfirmItem(item)
                        }
                    ) {
                        Icon(
                            imageVector = if (confirmedToday) Icons.Outlined.DoneAll else Icons.Outlined.Done,
                            contentDescription = if (confirmedToday) "오늘 확인 완료" else "오늘 확인"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (confirmedToday) "읽음 취소" else "읽음")
                    }
                    Row {
                        IconButton(onClick = { shareContentItem(context, item) }) {
                            Icon(Icons.Outlined.IosShare, contentDescription = "공유하기")
                        }
                        if (item.type == ContentType.QUOTE) {
                            IconButton(onClick = { shareQuoteImage(context, item) }) {
                                Icon(Icons.Outlined.Image, contentDescription = "이미지로 공유")
                            }
                        }
                        IconButton(onClick = { onToggleFavorite(item) }) {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                contentDescription = "즐겨찾기",
                                tint = if (item.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapsuleTag(
    text: String,
    backgroundColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun ContentType.displayLabel(): String {
    return when (this) {
        ContentType.QUOTE -> "글귀"
        ContentType.LINK -> "링크"
        ContentType.VIDEO -> "영상"
        ContentType.IDEA -> "아이디어"
        ContentType.INBOX -> "임시저장소"
        ContentType.MEMO -> "메모"
        ContentType.NOT_TO_DO -> "하지 않을 일"
        ContentType.CONCEPT -> "개념"
    }
}
