package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.ReportPeriod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val growthRunButtonTag = "growth_run_button"
internal const val growthPreviewButtonTag = "growth_preview_button"

/**
 * AI가 써 준 돌아보기를 받고 읽는 자리.
 *
 * 받는 일은 값이 드는 바깥 요청이라, 무엇이 나가는지 먼저 볼 수 있게 두었습니다.
 * 받은 글은 읽고 마는 대신 글귀나 루틴으로 바로 옮길 수 있습니다.
 */
@Composable
fun GrowthFeedbackScreen(
    reports: List<GrowthReportEntity>,
    running: Boolean,
    errorMessage: String,
    onRequest: (ReportPeriod) -> Unit,
    onPreview: (ReportPeriod) -> Unit,
    onDeleteReport: (GrowthReportEntity) -> Unit,
    onKeepQuote: (GrowthReportEntity) -> Unit,
    onKeepRoutine: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 보내기 전 미리 보기. null이면 닫힌 상태입니다. */
    previewText: String? = null,
    onDismissPreview: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    var selectedPeriod by rememberSaveable { mutableStateOf(ReportPeriod.WEEKLY.name) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val period = ReportPeriod.of(selectedPeriod)
    val deletingReport = reports.firstOrNull { it.id == deleting }

    if (previewText != null) {
        PromptPreviewDialog(text = previewText, onDismiss = onDismissPreview)
    }

    if (deletingReport != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("돌아보기 삭제") },
            text = { Text("이 돌아보기를 지울까요? 다시 받으려면 AI를 한 번 더 불러야 합니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteReport(deletingReport)
                        deleting = null
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("취소") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "돌아보기 받기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "루틴·할 일·일기·글귀 기록을 AI에게 보내 잘한 점과 다음 걸음을 받아 옵니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReportPeriod.entries.filter { it != ReportPeriod.MANUAL }) { choice ->
                            FilterChip(
                                selected = period == choice,
                                onClick = { selectedPeriod = choice.name },
                                label = { Text(choice.label) }
                            )
                        }
                    }

                    if (errorMessage.isNotBlank()) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onRequest(period) },
                            enabled = !running,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(growthRunButtonTag)
                        ) {
                            if (running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("지금 받기")
                            }
                        }
                        // 보내기 전에 무엇이 나가는지 볼 수 있어야 합니다. 한번 나가면 되돌릴 수 없습니다.
                        OutlinedButton(
                            onClick = { onPreview(period) },
                            enabled = !running,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(growthPreviewButtonTag)
                        ) {
                            Text("보낼 내용")
                        }
                    }

                    TextButton(onClick = onOpenSettings) {
                        Text("AI 설정 열기")
                    }
                }
            }
        }

        if (reports.isEmpty()) {
            item {
                EmptyCard(
                    title = "아직 받은 돌아보기가 없습니다.",
                    body = "기간을 고르고 지금 받기를 누르면 첫 글이 도착합니다."
                )
            }
        } else {
            items(reports, key = { it.id }) { report ->
                GrowthReportCard(
                    report = report,
                    onDelete = { deleting = report.id },
                    onKeepQuote = { onKeepQuote(report) },
                    onKeepRoutine = onKeepRoutine
                )
            }
        }
    }
}

@Composable
private fun GrowthReportCard(
    report: GrowthReportEntity,
    onDelete: () -> Unit,
    onKeepQuote: () -> Unit,
    onKeepRoutine: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${ReportPeriod.of(report.period).label} 돌아보기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${report.periodStart} ~ ${report.periodEnd} · ${formatDay(report.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "돌아보기 삭제"
                    )
                }
            }

            ReportSection(title = "잘한 점", lines = report.strengths)
            ReportSection(title = "다음 걸음", lines = report.improvements)

            if (report.suggestedQuote.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "추천 글귀",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = report.suggestedQuote,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (report.suggestedQuoteAuthor.isNotBlank()) {
                        Text(
                            text = "- ${report.suggestedQuoteAuthor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onKeepQuote) {
                        Text("보관함에 담기")
                    }
                }
            }

            if (report.suggestedRoutines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "해 볼 만한 루틴",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    report.suggestedRoutines.forEach { routine ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = routine,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onKeepRoutine(routine) }) {
                                Text("루틴 추가")
                            }
                        }
                    }
                }
            }

            if (report.guide.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "가이드",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = report.guide, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (report.model.isNotBlank()) {
                Text(
                    text = report.model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReportSection(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        lines.forEach { line ->
            Text(text = "· $line", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * 나가는 글을 그대로 보여 줍니다.
 *
 * 실제로 보내는 글과 같은 함수로 만든 것입니다. 미리 보기만 따로 만들면
 * 화면에 보이는 것과 나가는 것이 언젠가 어긋납니다.
 */
@Composable
private fun PromptPreviewDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI에게 보낼 내용") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text.ifBlank { "보낼 기록이 없습니다." },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

private fun formatDay(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
