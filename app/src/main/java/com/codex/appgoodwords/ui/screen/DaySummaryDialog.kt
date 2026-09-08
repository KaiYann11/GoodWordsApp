package com.codex.appgoodwords.ui.screen

import android.app.DatePickerDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.DayDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal const val daySummaryDialogTag = "day_summary_dialog"
internal const val daySummaryTitleTag = "day_summary_title"
internal const val daySummaryScoreTag = "day_summary_score"
internal const val daySummaryPrevTag = "day_summary_prev"
internal const val daySummaryNextTag = "day_summary_next"
internal const val daySummaryPickTag = "day_summary_pick"

/**
 * 하루를 펼쳐 봅니다.
 *
 * 점수 옆에 **그날 한 일의 이름**을 둡니다. 숫자만 보면 "왜 60점이지?"를 알 수 없고,
 * 모르면 다음 날이 달라지지 않습니다.
 *
 * 날짜를 옮기는 길이 셋입니다. 대개 보고 싶은 것은 "어제"나 "그저께"라 화살표가 가장 빠르고,
 * 최근 네 주는 그래프의 막대를 눌러 바로 옵니다. 그보다 먼 날은 달력에서 고릅니다 —
 * 화살표로만 옮기면 석 달 전을 보려고 아흔 번을 눌러야 합니다.
 */
@Composable
fun DaySummaryDialog(
    digest: DayDigest,
    onMove: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    /** 오늘 뒤로는 갈 수 없습니다. 오지 않은 날에 점수를 매길 수는 없습니다. */
    today: LocalDate = LocalDate.now()
) {
    val context = LocalContext.current
    val date = digest.score.date

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(daySummaryDialogTag),
        title = {
            // 셋 다 제 몫을 주장하면 글자가 잘립니다. 화살표는 제 너비만 쓰고, 남는 자리를
            // 모두 날짜에 줍니다(weight). 글자 크기를 키워 둔 기기에서 특히 그렇습니다 —
            // "이전"·"다음"이라고 적어 두면 그 글자부터 자리를 차지해 정작 날짜가 잘립니다.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onMove(date.minusDays(1)) },
                    modifier = Modifier.testTag(daySummaryPrevTag)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "앞날로"
                    )
                }
                Text(
                    text = titleOf(date, today),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(daySummaryTitleTag),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    // 그래도 모자라면 줄을 바꿉니다. 자르는 것보다 낫습니다.
                    maxLines = 2
                )
                IconButton(
                    onClick = { onMove(date.plusDays(1)) },
                    enabled = date < today,
                    modifier = Modifier.testTag(daySummaryNextTag)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "다음 날로"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${digest.score.score}점",
                        modifier = Modifier.testTag(daySummaryScoreTag),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // 기분은 점수에 넣지 않고 곁에 둡니다. 어떤 기분의 날에 무엇을 했는지
                    // 스스로 보라는 것입니다.
                    digest.score.mood?.let { mood ->
                        Text(
                            text = "${mood.emoji} ${mood.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "걸음 ${digest.score.doneSteps.size}/${digest.score.steps.size}" +
                        " · 한 일 ${digest.score.deedCount}가지",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (digest.isEmpty) {
                    Text(
                        text = "이날은 남긴 것이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DigestSection(label = "밟은 루틴", items = digest.routineTitles)
                    DigestSection(label = "끝낸 할 일", items = digest.todoTitles)
                    DigestSection(label = "쓴 일기", items = digest.diaryTitles)
                    DigestSection(label = "읽은 글귀", items = digest.quoteTitles)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        dismissButton = {
            // 화살표로만 옮기면 석 달 전을 보려고 아흔 번을 눌러야 합니다.
            TextButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            onMove(LocalDate.of(year, month + 1, dayOfMonth))
                        },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth
                    ).apply {
                        // 오지 않은 날에 점수를 매길 수는 없습니다.
                        datePicker.maxDate = today.atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }.show()
                },
                modifier = Modifier.testTag(daySummaryPickTag)
            ) {
                Text("날짜 고르기")
            }
        }
    )
}

@Composable
private fun DigestSection(label: String, items: List<String>) {
    if (items.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label ${items.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        items.forEach { title ->
            Text(
                text = "· $title",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** 오늘·어제는 날짜보다 그 말이 빨리 읽힙니다. 요일은 늘 붙입니다. */
private fun titleOf(date: LocalDate, today: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
    val name = when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> date.format(dayTitleFormatter)
    }
    return "$name ($weekday)"
}

private val dayTitleFormatter = DateTimeFormatter.ofPattern("M월 d일")
