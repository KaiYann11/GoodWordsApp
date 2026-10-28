package com.codex.appgoodwords.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.ui.theme.Motion
import kotlinx.coroutines.delay

internal const val homeQuoteCardTag = "home_quote_card"
internal const val homeQuoteTextTag = "home_quote_text"
internal const val homeQuoteConfirmTag = "home_quote_confirm"
internal const val homeQuoteNextTag = "home_quote_next"
internal const val homeQuoteAddTag = "home_quote_add"

/**
 * 홈 맨 위의 글귀.
 *
 * @property item 지금의 글귀. 고른 것이 없거나 지워졌으면 null입니다.
 * @property hasCandidates 고를 글귀가 있는지(알림에 보낼 카테고리 안에서). 없으면 "글귀를 담아 보세요"를 띄웁니다.
 * @property filteredOut 글귀는 있는데 알림에 보낼 카테고리에 맞는 것이 없는지. 그때는 담으라고 하지 않고 카테고리를 짚습니다.
 */
data class HomeQuote(
    val item: ContentItemEntity?,
    val hasCandidates: Boolean,
    val filteredOut: Boolean = false
)

/**
 * 오늘의 글귀 — 홈에서 가장 먼저 보이는 것.
 *
 * 앱 이름이 "오늘의 글귀"인데 홈에 글귀가 없어, 읽으려면 보관함까지 들어가야 했습니다(사용자 말, 2026-10-28:
 * "메인이 글귀인데 글귀가 잘 보이게"). 위젯·알림과 **같은 글귀**를 둡니다 — 세 곳이 다른 글귀를 말하면 무엇이
 * 오늘의 것인지 알 수 없습니다. "다음 글귀"를 누르면 위젯도 함께 넘어갑니다.
 *
 * **읽는 자리라 글이 주인입니다.** 테두리·그림자·꾸밈을 덜고 글귀만 크게(20sp, 줄 간격 넉넉히) 둡니다.
 * 읽음을 누르면 오늘의 걸음 "글귀 읽기"가 채워집니다 — 보관함에서 누르는 것과 같은 기록입니다.
 * 긴 글은 일곱 줄에서 자르고, 누르면 전체를 봅니다.
 *
 * @param quote null이면 아직 읽는 중입니다. 자리만 잡아 두어 글귀가 들어올 때 아래가 밀려 내려가지 않게 합니다.
 */
@Composable
fun TodayQuoteCard(
    quote: HomeQuote?,
    confirmedToday: Boolean,
    onConfirm: (ContentItemEntity) -> Unit,
    onNext: () -> Unit,
    onOpen: (ContentItemEntity) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** 바뀔 때마다 잠깐 테두리를 둘러 어디인지 알려 줍니다(걸음의 "글귀 읽기"를 눌러 왔을 때). 0이면 두르지 않습니다. */
    pulseKey: Int = 0
) {
    var lit by remember(pulseKey) { mutableStateOf(pulseKey > 0) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        delay(PULSE_MILLIS)
        lit = false
    }
    val ring by animateFloatAsState(
        targetValue = if (lit) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "homeQuoteRing"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = ring), MaterialTheme.shapes.large)
            .testTag(homeQuoteCardTag),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 위쪽만 옅은 파랑으로 물들여 다른 카드와 갈라 보이게 합니다. 글자 뒤는 흰 바탕이라 읽기에 거슬리지 않습니다.
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                    )
                )
                .heightIn(min = 168.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 22.dp, end = 14.dp, top = 18.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "오늘의 글귀",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (confirmedToday && quote?.item != null) {
                        ReadTodayMark()
                    }
                }

                val item = quote?.item
                when {
                    item != null -> {
                        // 글귀가 바뀔 때 툭 갈리지 않고 스미듯 바뀝니다.
                        AnimatedContent(
                            targetState = item,
                            contentKey = { it.id },
                            transitionSpec = { fadeIn(Motion.screen()) togetherWith fadeOut(Motion.screen()) },
                            label = "homeQuote"
                        ) { shown ->
                            QuoteBody(item = shown, onOpen = onOpen)
                        }
                        QuoteActions(
                            item = item,
                            confirmedToday = confirmedToday,
                            onConfirm = onConfirm,
                            onNext = onNext
                        )
                    }

                    // 아직 읽는 중이거나 고르는 중입니다. 빈 안내를 잠깐 비추지 않습니다.
                    quote == null || quote.hasCandidates -> Unit

                    quote.filteredOut -> Text(
                        text = "알림에 보낼 카테고리에 맞는 글귀가 없습니다. 설정 › 알림에서 카테고리를 바꾸면 여기에 뜹니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp, bottom = 10.dp)
                    )

                    else -> Column(
                        modifier = Modifier.padding(end = 8.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "마음에 닿은 글귀를 담아 두면 여기서 날마다 다시 만납니다.",
                            style = QuoteTextStyle.copy(fontSize = 17.sp, lineHeight = 27.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(onClick = onAdd, modifier = Modifier.testTag(homeQuoteAddTag)) {
                            Text("글귀 담기")
                        }
                    }
                }
            }
        }
    }
}

/** 글귀 본문. 누르면 전체를 봅니다. */
@Composable
private fun QuoteBody(item: ContentItemEntity, onOpen: (ContentItemEntity) -> Unit) {
    // 글귀는 본문이 주인이고, 링크·영상은 제목이 주인입니다(본문은 설명이나 주소인 경우가 많습니다).
    val isQuote = item.type == ContentType.QUOTE
    val main = if (isQuote) item.body.ifBlank { item.title } else item.title.ifBlank { item.body }
    val heading = item.title.trim().takeIf { isQuote && it.isNotBlank() && item.body.isNotBlank() && !item.body.trim().startsWith(it.removeSuffix("…")) }
    val detail = item.body.trim().takeIf { !isQuote && item.title.isNotBlank() && it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "글귀 전체 보기") { onOpen(item) }
            // 누르는 자리에 답니다. 안의 글자들은 누르는 자리 하나로 합쳐져 따로 짚이지 않습니다.
            .testTag(homeQuoteTextTag)
            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        heading?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = main,
            style = QuoteTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = MAX_QUOTE_LINES,
            overflow = TextOverflow.Ellipsis
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.author.isNotBlank()) {
            Text(
                text = "— ${item.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuoteActions(
    item: ContentItemEntity,
    confirmedToday: Boolean,
    onConfirm: (ContentItemEntity) -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 읽음이 이 카드의 할 일입니다. 읽고 나면 눌러 둔 모양으로 남고, 다시 누르면 거둡니다(잘못 눌렀을 때).
        if (confirmedToday) {
            OutlinedButton(onClick = { onConfirm(item) }, modifier = Modifier.testTag(homeQuoteConfirmTag)) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("읽었어요")
            }
        } else {
            Button(onClick = { onConfirm(item) }, modifier = Modifier.testTag(homeQuoteConfirmTag)) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("읽음")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onNext, modifier = Modifier.testTag(homeQuoteNextTag)) {
            Text("다음 글귀")
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** 오늘 읽은 글귀라는 표시. 못 읽은 것을 붉게 세지 않고, 읽은 것에만 조용히 답니다. */
@Composable
private fun ReadTodayMark() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "오늘 읽음",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * 글귀 글자.
 *
 * 본문(14~16sp)보다 한 단 크고 굵기는 중간입니다. 굵게 하면 긴 글귀가 덩어리로 보여 오히려 읽히지 않고,
 * 줄 간격을 넉넉히 두어야 한글 문장이 숨을 쉽니다.
 */
private val QuoteTextStyle = TextStyle(
    fontSize = 20.sp,
    lineHeight = 32.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.3).sp
)

/** 이보다 길면 자르고 눌러서 봅니다. 홈의 아래 칸이 화면 밖으로 밀려나지 않게. */
private const val MAX_QUOTE_LINES = 7

/** 테두리를 두르고 있는 시간. 계속 남으면 다음에 홈을 열 때 왜 둘려 있는지 알 수 없습니다. */
private const val PULSE_MILLIS = 1_600L
