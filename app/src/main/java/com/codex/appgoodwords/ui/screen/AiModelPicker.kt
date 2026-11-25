package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.AiFeedbackSettings
import com.codex.appgoodwords.data.AiModelCheck
import com.codex.appgoodwords.data.AiModelInfo
import com.codex.appgoodwords.data.AiModelList
import com.codex.appgoodwords.data.AiModels
import com.codex.appgoodwords.data.AiProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal const val aiModelOtherTag = "ai_model_other"
internal const val aiModelNowTag = "ai_model_now"
internal const val aiModelDialogSourceTag = "ai_model_dialog_source"
internal const val aiModelDialogFieldTag = "ai_model_dialog_field"
internal const val aiModelDialogRefreshTag = "ai_model_dialog_refresh"
internal const val aiModelDialogConfirmTag = "ai_model_dialog_confirm"
internal const val aiModelCheckTag = "ai_model_check"
internal const val aiModelCheckTimeTag = "ai_model_check_time"
internal const val aiModelCheckErrorTag = "ai_model_check_error"

/** 칸 하나. 빈 값은 서버 쪽 기본, `latest:opus`는 계열 최신, 그 밖은 모델 이름입니다. */
internal fun aiModelChoiceTag(choice: String): String = "ai_model_" + choice.ifBlank { "default" }

internal fun aiModelRowTag(id: String): String = "ai_model_row_$id"

/** "모델 정보 받아오기" 아래 한 줄(칸 하나). */
internal fun aiModelCheckRowTag(choice: String): String = "ai_model_check_row_" + choice.ifBlank { "default" }

/**
 * 모델 고르기(설정 → AI).
 *
 * 계열 칸("Sonnet 최신")이 앞에 섭니다 — 골라 두면 다음 세대가 나왔을 때 앱을 고치지 않고 그 모델로 넘어갑니다(사용자가 고름,
 * 2026-11-25). 이름을 콕 집어 고르려면 "다른 모델…"에서 받아 온 목록을 고르거나 적습니다. 계열 칸은 받아 온 목록에서 셉니다
 * ([AiModels.families]) — 새 계열이 나오면 칸도 저절로 생깁니다.
 *
 * 칸 아래 "모델 정보 받아오기"를 누르면 칸마다 지금 답하는 모델을 한 줄씩 보여 줍니다(사용자가 고름, 2026-11-25 — 단추 아래
 * 목록). CLI·Ollama 기본과 Claude CLI 칸은 서버가 모델을 골라 앱이 미리 알 수 없어, 서버가 CLI에 짧게 물어 알아 옵니다.
 * 몇 초 걸리고 구독 사용량이 조금 들어 누를 때만 받습니다. 목록은 다시 누를 때까지 그대로 둡니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiModelPicker(
    settings: AiFeedbackSettings,
    list: AiModelList,
    check: AiModelCheck,
    loading: Boolean,
    error: String?,
    checking: Boolean,
    checkError: String?,
    onChanged: (AiFeedbackSettings) -> Unit,
    onRefresh: () -> Unit,
    onCheck: () -> Unit
) {
    val provider = settings.activeProvider
    val choice = settings.modelChoice
    // 줄마다 열쇠(이름)가 달라야 목록 칸이 그려집니다. 같은 이름이 두 번 오면 하나만 둡니다.
    val models = list.models.ifEmpty { provider.builtInModels }.distinctBy { it.id }
    val families = if (provider.familyAliases) provider.familyChoices else AiModels.families(models, provider.familyChoices)
    var showDialog by rememberSaveable { mutableStateOf(false) }
    // 이름으로 고른 것이나 칸에 없는 계열은 따로 한 칸 더 보여 줍니다. 안 보이면 무엇을 골라 두었는지 알 수 없습니다.
    val chosenFamily = AiModels.familyOfChoice(choice)
    val shownAsChip = (choice.isEmpty() && provider.defaultFamily == null) || (chosenFamily != null && chosenFamily in families)

    Text("모델", style = MaterialTheme.typography.titleSmall)
    // 줄을 넘겨 모두 보이게 둡니다. 옆으로 밀어야 보이면 "다른 모델…"과 새로 생긴 계열 칸이 있는지 모릅니다.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (provider.defaultFamily == null) {
            // CLI·Ollama는 빈 값이 "서버 쪽 기본"입니다(CLI 기본·서버 기본).
            ModelChip(selected = choice.isEmpty(), label = provider.modelLabel(""), tag = aiModelChoiceTag("")) {
                onChanged(settings.withModel(""))
            }
        }
        families.forEach { family ->
            val value = AiModels.latest(family)
            ModelChip(selected = choice == value, label = AiModels.familyLabel(family), tag = aiModelChoiceTag(value)) {
                onChanged(settings.withModel(value))
            }
        }
        if (!shownAsChip) {
            ModelChip(selected = true, label = provider.modelLabel(choice), tag = aiModelChoiceTag(choice)) {}
        }
        ModelChip(selected = false, label = "다른 모델…", tag = aiModelOtherTag) { showDialog = true }
    }
    Text(
        text = nowLine(settings, list, check),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(aiModelNowTag)
    )
    OutlinedButton(
        onClick = onCheck,
        enabled = !checking,
        modifier = Modifier.testTag(aiModelCheckTag)
    ) {
        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (checking) "받는 중…" else "모델 정보 받아오기")
    }
    if (!checkError.isNullOrBlank()) {
        Text(
            text = "모델 정보를 받지 못했습니다: $checkError",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(aiModelCheckErrorTag)
        )
    }
    if (check.checkedAt > 0L) {
        // 줄 사이를 좁게 둡니다. 바깥 칸 간격으로 벌리면 한 덩이로 읽히지 않습니다.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = checkedLine(check.checkedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(aiModelCheckTimeTag)
            )
            checkRows(settings, models, families, check).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                        .semantics(mergeDescendants = true) {}
                        .testTag(aiModelCheckRowTag(row.choice))
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(104.dp))
                    Text(
                        text = row.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.model.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showDialog) {
        AiModelDialog(
            provider = provider,
            current = choice.takeIf { AiModels.familyOfChoice(it) == null }.orEmpty(),
            list = list,
            models = models,
            loading = loading,
            error = error,
            onRefresh = onRefresh,
            onPick = { name ->
                showDialog = false
                onChanged(settings.withModel(name))
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ModelChip(selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(tag)
    )
}

/**
 * "지금 답하는 모델" 줄.
 *
 * 계열을 골랐으면 그 계열에서 지금 가장 새 것과, 다음 세대가 나오면 저절로 바뀐다는 것을 적습니다. 서버가 모델을 고르는 칸
 * (CLI·Ollama 기본, Claude CLI 칸 — [AiModels.isServerPicked])은 알아 둔 이름("모델 정보 받아오기"나 그 칸으로 받은 마지막 답)을
 * 적고, 아직 모르면 누가 고르는지만 적습니다.
 */
internal fun nowLine(settings: AiFeedbackSettings, list: AiModelList, check: AiModelCheck): String {
    val provider = settings.activeProvider
    val choice = settings.modelChoice
    val serverPicked = AiModels.isServerPicked(provider, choice)
    val known = check.names[choice]?.takeIf { serverPicked && it.isNotBlank() }
    val family = AiModels.familyOfChoice(choice)
    return when {
        family != null -> {
            val name = family.replaceFirstChar { it.uppercase() }
            val current = when {
                known != null -> "지금 답하는 모델: $known"
                provider.familyAliases -> "서버의 ${provider.label}가 가장 새 $name 모델로 답합니다."
                else -> "지금 답하는 모델: ${settings.requestModel(list.models)}"
            }
            "$current\n$name 다음 세대가 나오면 저절로 바뀝니다."
        }
        choice.isEmpty() -> known?.let { "지금 답하는 모델: $it" } ?: when (provider) {
            AiProvider.OLLAMA -> "서버에 받아 둔 모델 가운데 가장 최근에 받은 것이 답합니다."
            else -> "서버의 ${provider.label}에 정해 둔 모델이 답합니다."
        }
        // Claude CLI에 적은 별칭(`fable`)은 CLI가 그 계열의 가장 새 모델로 바꿉니다. "바뀌지 않습니다"가 아닙니다.
        serverPicked -> (known?.let { "지금 답하는 모델: $it" } ?: "서버의 ${provider.label}가 이 별칭의 모델로 답합니다.") +
            "\n별칭이라 새 모델이 나오면 ${provider.label}가 바꿉니다."
        else -> "이 이름으로 묻습니다. 새 모델이 나와도 바뀌지 않습니다."
    }
}

/** "모델 정보 받아오기" 아래 한 줄 — 칸 이름과, 그 칸이 지금 답하는 모델(모르면 사유). */
internal data class CheckRow(val choice: String, val label: String, val model: String, val error: String) {
    val text: String
        get() = model.ifBlank { if (error.isNotBlank()) "알아내지 못함: $error" else "아직 모릅니다. 다시 받아 보세요." }
}

/**
 * 칸이 보이는 차례대로 한 줄씩.
 *
 * 서버가 고르는 칸은 알아 둔 이름으로 적습니다 — 지난번에 알아내지 못했으면 지난 이름 대신 그 사유를 적습니다(지금 모르는
 * 것을 아는 것처럼 보이지 않게). 나머지 계열 칸은 받아 둔 목록에서 지금 보낼 이름을 셉니다(목록을 새로 받으면 그 자리에서
 * 바뀝니다). 이름을 그대로 고른 칸은 칸에 이름이 적혀 있어 줄을 두지 않습니다(Claude CLI에 적은 별칭만 둡니다).
 */
internal fun checkRows(
    settings: AiFeedbackSettings,
    models: List<AiModelInfo>,
    families: List<String>,
    check: AiModelCheck
): List<CheckRow> {
    val provider = settings.activeProvider
    val choices = buildList {
        if (provider.defaultFamily == null) add("")
        families.forEach { add(AiModels.latest(it)) }
        val current = settings.modelChoice
        if (current !in this && AiModels.isServerPicked(provider, current)) add(current)
    }
    return choices.map { choice ->
        val label = provider.modelLabel(choice)
        if (AiModels.isServerPicked(provider, choice)) {
            val error = check.errors[choice].orEmpty()
            CheckRow(choice, label, model = if (error.isEmpty()) check.names[choice].orEmpty() else "", error = error)
        } else {
            CheckRow(choice, label, model = settings.withModel(choice).requestModel(models), error = "")
        }
    }
}

/** 단추를 누른 때. "11월 25일 15:30에 받음". */
internal fun checkedLine(checkedAt: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("M월 d일 HH:mm").format(Instant.ofEpochMilli(checkedAt).atZone(zoneId)) + "에 받음"

/**
 * 받아 온 목록에서 고르거나 이름을 적는 창.
 *
 * 줄을 누르면 아래 칸에 그 이름이 들어갑니다 — 고르는 것과 적는 것이 한 칸이라, 무엇을 고를지 헷갈리지 않습니다.
 */
@Composable
private fun AiModelDialog(
    provider: AiProvider,
    current: String,
    list: AiModelList,
    models: List<AiModelInfo>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var typed by rememberSaveable { mutableStateOf(current) }
    val valid = AiModels.isValidName(typed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("모델 고르기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sourceLine(provider, list),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(aiModelDialogSourceTag)
                    )
                    if (!provider.familyAliases) {
                        TextButton(
                            onClick = onRefresh,
                            enabled = !loading,
                            modifier = Modifier.testTag(aiModelDialogRefreshTag)
                        ) {
                            Text(if (loading) "받는 중…" else "새로 받기")
                        }
                    }
                }
                if (!error.isNullOrBlank()) {
                    Text(
                        text = "목록을 받지 못했습니다: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(models, key = { it.id }) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { typed = model.id }
                                .testTag(aiModelRowTag(model.id))
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = typed == model.id, onClick = { typed = model.id })
                            Column {
                                Text(model.label, style = MaterialTheme.typography.bodyMedium)
                                if (model.label != model.id) {
                                    Text(
                                        model.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.trim() },
                    label = { Text("직접 적기") },
                    singleLine = true,
                    isError = typed.isNotEmpty() && !valid,
                    supportingText = {
                        Text(
                            if (provider.familyAliases) "목록에 없으면 이름이나 별칭(fable 같은)을 적습니다."
                            else "목록에 없으면 공급자 문서의 모델 이름을 그대로 적습니다."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(aiModelDialogFieldTag)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(typed.trim()) },
                enabled = valid,
                modifier = Modifier.testTag(aiModelDialogConfirmTag)
            ) {
                Text("고르기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/** 창 맨 위의 한 줄 — 이 목록이 어디서 언제 온 것인지. */
internal fun sourceLine(provider: AiProvider, list: AiModelList, zoneId: ZoneId = ZoneId.systemDefault()): String {
    if (provider.familyAliases) return "${provider.label}는 목록을 알려 주지 않아 앱에 든 이름입니다."
    if (list.fetchedAt <= 0L) return "아직 목록을 받지 못해 앱에 든 이름입니다."
    val at = DateTimeFormatter.ofPattern("M월 d일 HH:mm").format(Instant.ofEpochMilli(list.fetchedAt).atZone(zoneId))
    val where = when (provider) {
        AiProvider.CODEX_CLI -> "서버의 Codex"
        AiProvider.OLLAMA -> "서버의 Ollama"
        else -> provider.label
    }
    if (list.models.isEmpty()) return "$where 목록이 비어 있습니다 · $at 받음"
    return "$where 목록 · $at 받음"
}
