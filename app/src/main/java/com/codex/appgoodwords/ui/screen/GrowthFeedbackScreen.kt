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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.GrowthReportEntity
import com.codex.appgoodwords.data.PendingGrowthPrompt
import com.codex.appgoodwords.data.ReportPeriod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val growthRunButtonTag = "growth_run_button"
internal const val growthPreviewButtonTag = "growth_preview_button"
internal const val growthPasteButtonTag = "growth_paste_button"
internal const val growthAnswerFieldTag = "growth_answer_field"
internal const val growthTidyButtonTag = "growth_tidy_button"

/** 목록 거르개. 쌓인 것을 단위로 좁혀 봅니다. */
internal fun growthListFilterTag(name: String): String = "growth_list_filter_" + name.ifBlank { "all" }

/**
 * 목록에서 볼 단위.
 *
 * 화면 위쪽 칩은 "무엇을 새로 받을지" 고르는 것이라 목록과 상관이 없습니다. 쌓이고 나면
 * 그 둘이 헷갈리므로 목록 거르개를 따로 둡니다.
 */
private enum class ReportListFilter(val label: String, val period: ReportPeriod?) {
    ALL("전체", null),
    DAILY("하루", ReportPeriod.DAILY),
    WEEKLY("한 주", ReportPeriod.WEEKLY),
    MONTHLY("한 달", ReportPeriod.MONTHLY);

    fun matches(report: GrowthReportEntity): Boolean =
        period == null || ReportPeriod.of(report.period) == period
}

/**
 * AI가 써 준 돌아보기를 받고 읽는 자리.
 *
 * 받는 일은 값이 드는 바깥 요청이라, 무엇이 나가는지 먼저 볼 수 있게 두었습니다.
 * 받은 글은 읽고 마는 대신 글귀나 루틴으로 바로 옮길 수 있습니다.
 *
 * **앱이 AI에 붙지 못해도 막다른 길이 아닙니다.** 열쇠가 없거나 서버가 꺼져 있어도,
 * 물음을 복사해 쓰던 채팅창에 붙여넣고 받아 온 답을 여기에 도로 붙여넣으면 똑같이 남습니다.
 */
@Composable
fun GrowthFeedbackScreen(
    reports: List<GrowthReportEntity>,
    running: Boolean,
    errorMessage: String,
    onRequest: (ReportPeriod) -> Unit,
    onPreview: (ReportPeriod) -> Unit,
    onDeleteReport: (GrowthReportEntity) -> Unit,
    /** 여러 편을 한 번에 지웁니다. 한 장씩 지우게 두면 쌓인 것을 치울 방법이 없습니다. */
    onDeleteReports: (List<Long>) -> Unit = {},
    onKeepQuote: (GrowthReportEntity) -> Unit,
    onKeepRoutine: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 보내기 전 미리 보기. null이면 닫힌 상태입니다. */
    previewText: String? = null,
    onDismissPreview: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** 물음을 복사해 갔다고 적어 둡니다. 붙여넣은 답을 어느 구간으로 넣을지 정할 때 씁니다. */
    onPromptCopied: (ReportPeriod) -> Unit = {},
    onSaveAnswer: (String) -> Unit = {},
    /** 복사해 두고 아직 답을 받아 적지 않은 물음. */
    pendingPrompt: PendingGrowthPrompt? = null
) {
    var selectedPeriod by rememberSaveable { mutableStateOf(ReportPeriod.WEEKLY.name) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var pasting by rememberSaveable { mutableStateOf(false) }
    var listFilter by rememberSaveable { mutableStateOf(ReportListFilter.ALL.name) }
    var tidying by rememberSaveable { mutableStateOf(false) }
    val period = ReportPeriod.of(selectedPeriod)
    val deletingReport = reports.firstOrNull { it.id == deleting }
    val shownFilter = ReportListFilter.entries.firstOrNull { it.name == listFilter } ?: ReportListFilter.ALL
    val shownReports = reports.filter(shownFilter::matches)
    val clipboard = LocalClipboardManager.current

    if (previewText != null) {
        PromptPreviewDialog(
            text = previewText,
            onCopy = {
                clipboard.setText(AnnotatedString(previewText))
                onPromptCopied(period)
                onDismissPreview()
            },
            onDismiss = onDismissPreview
        )
    }

    if (pasting) {
        AnswerPasteDialog(
            pendingPrompt = pendingPrompt,
            onPaste = { clipboard.getText()?.text.orEmpty() },
            onSave = { text ->
                onSaveAnswer(text)
                pasting = false
            },
            onDismiss = { pasting = false }
        )
    }

    if (tidying) {
        TidyDialog(
            reports = reports,
            onDismiss = { tidying = false },
            onTidy = { keep ->
                onDeleteReports(reports.drop(keep).map { it.id })
                tidying = false
            }
        )
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
                            Text("보낼 내용 보기")
                        }
                    }

                    // AI에 못 붙어도 막다른 길이 아닙니다. 물음만 들고 가서 받아 온 답을 도로 넣습니다.
                    Text(
                        text = "AI 연결이 안 되면 보낼 내용을 복사해 쓰던 채팅창에 붙여넣고, " +
                            "받은 답을 아래에서 도로 붙여넣으면 똑같이 남습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { pasting = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(growthPasteButtonTag)
                    ) {
                        Text(
                            if (pendingPrompt != null) {
                                "받아 온 답 붙여넣기 (${pendingPrompt.period.label})"
                            } else {
                                "받아 온 답 붙여넣기"
                            }
                        )
                    }

                    TextButton(onClick = onOpenSettings) {
                        Text("AI 설정 열기")
                    }
                }
            }
        }

        // 쌓이고 나면 목록이 한 줄로 끝없이 이어집니다. 하루 주기로 돌리면 한 해에 삼백 장이
        // 넘습니다. 단위로 좁혀 보고, 오래된 것은 한 번에 치울 수 있어야 합니다.
        if (reports.size >= LIST_TOOLS_FROM) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ReportListFilter.entries) { choice ->
                            FilterChip(
                                selected = shownFilter == choice,
                                onClick = { listFilter = choice.name },
                                label = { Text(choice.label) },
                                modifier = Modifier.testTag(growthListFilterTag(choice.name))
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${shownReports.size}편",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { tidying = true },
                            modifier = Modifier.testTag(growthTidyButtonTag)
                        ) {
                            Text("오래된 것 정리")
                        }
                    }
                }
            }
        }

        if (shownReports.isEmpty()) {
            item {
                EmptyCard(
                    title = if (reports.isEmpty()) {
                        "아직 받은 돌아보기가 없습니다."
                    } else {
                        "${shownFilter.label} 돌아보기는 아직 없습니다."
                    },
                    body = if (reports.isEmpty()) {
                        "기간을 고르고 지금 받기를 누르면 첫 글이 도착합니다."
                    } else {
                        "거르개를 전체로 두면 받아 둔 것이 모두 보입니다."
                    }
                )
            }
        } else {
            items(shownReports, key = { it.id }) { report ->
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
 *
 * 여기 보이는 글이 복사되는 글이기도 합니다. 그래서 답의 형식을 정하는 첫 대목까지
 * 함께 보여 줍니다. 그 대목을 빼고 붙여넣으면 읽어 낼 수 없는 답이 돌아옵니다.
 */
@Composable
private fun PromptPreviewDialog(text: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
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
            TextButton(onClick = onCopy, enabled = text.isNotBlank()) { Text("복사") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

/**
 * 채팅창에서 받아 온 답을 도로 넣는 자리.
 *
 * 붙여넣기 버튼을 따로 둔 것은, 긴 글을 손으로 붙여넣다 보면 앞이나 뒤가 잘리기 때문입니다.
 * 형식이 어긋난 글도 받습니다. 갈래를 나눠 읽지 못하면 통째로 가이드에 담습니다.
 * 사람이 옮겨 온 글을 "못 읽었다"며 되돌려 주면 그 자리에서 사라집니다.
 */
@Composable
private fun AnswerPasteDialog(
    pendingPrompt: PendingGrowthPrompt?,
    onPaste: () -> String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var answer by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("받아 온 답 붙여넣기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (pendingPrompt != null) {
                        "${pendingPrompt.from} ~ ${pendingPrompt.to} " +
                            "(${pendingPrompt.period.label}) 구간으로 남깁니다."
                    } else {
                        "복사해 둔 물음이 없어 오늘까지 한 주 구간으로 남깁니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { answer = onPaste().ifBlank { answer } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("클립보드에서 가져오기")
                }
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        // 긴 답도 들어옵니다. 다이얼로그를 밀어내지 않게 위아래를 함께 정합니다.
                        .heightIn(min = 120.dp, max = 220.dp)
                        .testTag(growthAnswerFieldTag),
                    label = { Text("AI가 준 답") },
                    supportingText = { Text("JSON이 아니어도 됩니다. 그때는 통째로 가이드에 담깁니다.") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(answer) },
                enabled = answer.isNotBlank()
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/**
 * 오래된 것을 한 번에 치웁니다.
 *
 * **최근 몇 편을 남길지**로 묻습니다. "몇 편을 지울지"로 물으면 무엇이 사라지는지 세어 봐야
 * 알 수 있고, 잘못 세면 되돌릴 수 없습니다. 남는 쪽을 말하면 손이 미끄러져도 최근 것은
 * 그대로입니다.
 */
@Composable
private fun TidyDialog(
    reports: List<GrowthReportEntity>,
    onTidy: (keep: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var keep by rememberSaveable { mutableStateOf(KEEP_CHOICES.first()) }
    val removed = (reports.size - keep).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오래된 돌아보기 정리") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("최근 몇 편을 남길까요?", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(KEEP_CHOICES) { choice ->
                        FilterChip(
                            selected = keep == choice,
                            onClick = { keep = choice },
                            label = { Text("${choice}편") }
                        )
                    }
                }
                Text(
                    text = if (removed == 0) {
                        "지울 것이 없습니다. 받아 둔 것이 ${reports.size}편입니다."
                    } else {
                        "${reports.size}편 중 오래된 ${removed}편을 지웁니다. 되돌릴 수 없습니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (removed == 0) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onTidy(keep) }, enabled = removed > 0) { Text("정리") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/** 거르개와 정리를 언제부터 보여 줄지. 몇 편 없을 때는 자리만 차지합니다. */
private const val LIST_TOOLS_FROM = 4

private val KEEP_CHOICES = listOf(10, 30, 50)

private fun formatDay(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
