package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DiaryMood

internal const val todayMoodCardTag = "today_mood_card"

internal fun todayMoodChipTag(mood: DiaryMood) = "today_mood_${mood.name}"

/**
 * 오늘 기분 한 번.
 *
 * 예전에는 기분이 일기에만 붙어 있어서 **일기를 써야만 기분이 남았습니다.** 그런데 바쁘고
 * 힘든 날일수록 일기를 못 씁니다. 정작 가장 알고 싶은 날이 가장 확실하게 비어 있었습니다.
 *
 * 그래서 여기서는 **누르는 것 말고는 아무것도 요구하지 않습니다.** 적을 것을 하나라도 붙이면
 * 3초에 안 끝나고, 3초에 안 끝나면 바쁜 날 또 비어 버립니다. 한 줄 적고 싶어지면 그것은 일기입니다.
 *
 * 이미 그날 일기에 기분을 골라 두었으면 그것이 보입니다. 물어봐 놓고 답이 있는 줄 모르면
 * 사용자는 같은 것을 두 번 말하게 됩니다. 눌러서 바꾸면 그때부터 이쪽이 그날의 기분입니다.
 */
@Composable
fun TodayMoodCard(
    mood: DiaryMood?,
    onPick: (DiaryMood) -> Unit,
    modifier: Modifier = Modifier,
    /** 찍어 둔 것이 있을 때만 지울 수 있습니다. 일기에서 온 기분은 일기에서 고칩니다. */
    onClear: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(todayMoodCardTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (mood == null) "오늘 기분은 어떠세요?" else "오늘은 ${mood.label}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (mood != null && onClear != null) {
                    TextButton(onClick = onClear) { Text("지우기") }
                }
            }

            if (mood == null) {
                Text(
                    text = "하나만 누르면 됩니다. 일기를 쓰지 않은 날도 남습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DiaryMood.entries) { choice ->
                    FilterChip(
                        selected = mood == choice,
                        onClick = { onPick(choice) },
                        label = { Text("${choice.emoji} ${choice.label}") },
                        modifier = Modifier.testTag(todayMoodChipTag(choice))
                    )
                }
            }
        }
    }
}
