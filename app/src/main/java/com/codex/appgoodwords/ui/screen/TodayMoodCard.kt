package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DayMood
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.MoodLogEntity

internal const val todayMoodCardTag = "today_mood_card"

internal fun todayMoodChipTag(mood: DiaryMood) = "today_mood_${mood.name}"
internal fun moodLogDeleteTag(syncId: String) = "mood_log_delete_$syncId"
internal fun moodLogTag(syncId: String) = "mood_log_$syncId"
internal const val moodTimelineTag = "mood_timeline"
internal const val moodLogDeleteConfirmTag = "mood_log_delete_confirm"
internal const val moodLogDeleteCancelTag = "mood_log_delete_cancel"

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
 * 사용자는 같은 것을 두 번 말하게 됩니다. 누르면 지금 기분이 흐름 끝에 새로 남습니다
 * (사용자가 정함, 2026-10-29).
 */
@Composable
fun TodayMoodCard(
    mood: DiaryMood?,
    onPick: (DiaryMood) -> Unit,
    modifier: Modifier = Modifier,
    /** DayMood가 시간순으로 놓은 오늘 기록입니다. */
    logs: List<MoodLogEntity> = emptyList(),
    /** 찍어 둔 것이 있을 때만 지울 수 있습니다. 일기에서 온 기분은 일기에서 고칩니다. */
    onClear: ((String) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(todayMoodCardTag),
        // 홈의 다른 카드와 같은 모서리·여백입니다. 카드마다 조금씩 다르면 화면이 어수선해 보입니다.
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (mood == null) "오늘 기분은 어떠세요?" else "지금은 ${mood.label}",
                style = MaterialTheme.typography.titleMedium
            )

            if (mood == null) {
                Text(
                    text = "지금 기분을 하나 골라 주세요. 옆으로 넘기면 더 있어요.",
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

            if (logs.isNotEmpty()) {
                Text(
                    text = "오늘의 흐름 · 옆으로 넘겨 보세요",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MoodTimeline(logs = logs, onDelete = onClear)
            }
        }
    }
}

/**
 * 홈과 그날 요약은 같은 흐름을 한 줄로 봅니다. 기록마다 줄을 늘리면 빠르게 찍는 카드가
 * 긴 목록이 됩니다. ✕는 지우는 길을 보이되 48dp 터치 영역을 두고, 누른 기록을 한 번 확인합니다.
 * 옆으로 넘기다 잘못 닿아도 바로 지워지지 않고, 기분을 새로 찍는 길에는 확인을 붙이지 않습니다.
 */
@Composable
internal fun MoodTimeline(logs: List<MoodLogEntity>, onDelete: ((String) -> Unit)? = null) {
    if (logs.isEmpty()) return
    val haptics = rememberHaptics()
    // 날짜를 옮기면 앞날에서 고른 삭제 대상을 거둡니다. 동기화로 숫자 id가 바뀌어도 syncId를 따릅니다.
    var deletingSyncId by remember(logs.first().entryDate) { mutableStateOf<String?>(null) }
    LazyRow(
        modifier = Modifier.fillMaxWidth().testTag(moodTimelineTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs, key = { it.syncId }) { log ->
            val time = DayMood.timeLabel(log)
            val label = moodLabel(log)
            Surface(
                modifier = Modifier.testTag(moodLogTag(log.syncId)),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$time  $label",
                        modifier = Modifier.padding(start = 12.dp, end = if (onDelete == null) 12.dp else 0.dp, top = 8.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    if (onDelete != null) {
                        IconButton(
                            onClick = { haptics.tap(); deletingSyncId = log.syncId },
                            modifier = Modifier.size(48.dp).testTag(moodLogDeleteTag(log.syncId))
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "$time $label 기록 지우기",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    logs.firstOrNull { it.syncId == deletingSyncId }?.let { log ->
        if (onDelete != null) {
            AlertDialog(
                onDismissRequest = { deletingSyncId = null },
                title = { Text("이 기분 기록을 지울까요?") },
                text = { Text("${DayMood.timeLabel(log)}  ${moodLabel(log)}") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            // 화면에서 먼저 거둡니다. DB가 바뀌기를 기다리면 두 번 누를 수 있습니다.
                            deletingSyncId = null
                            onDelete(log.syncId)
                        },
                        modifier = Modifier.testTag(moodLogDeleteConfirmTag)
                    ) { Text("지우기") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { deletingSyncId = null },
                        modifier = Modifier.testTag(moodLogDeleteCancelTag)
                    ) { Text("취소") }
                }
            )
        }
    }
}

private fun moodLabel(log: MoodLogEntity): String =
    log.moodOption?.let { "${it.emoji} ${it.label}" } ?: log.mood.ifBlank { "알 수 없는 기분" }
