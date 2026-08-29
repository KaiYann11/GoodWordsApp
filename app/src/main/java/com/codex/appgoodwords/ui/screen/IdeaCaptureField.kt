package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

internal const val ideaCaptureFieldTag = "idea_capture_field"
internal const val ideaCaptureSaveTag = "idea_capture_save"
internal const val ideaCaptureDialogTag = "idea_capture_dialog"

/**
 * 번뜩인 것을 그 자리에서 한 줄로 담습니다.
 *
 * 담는 화면까지 들어가면 여섯 걸음입니다. 번뜩인 것은 그 사이에 날아갑니다. 여기서는
 * **치고 누르면 끝**입니다. 자판의 완료(엔터)로도 담깁니다.
 *
 * 제목만 받습니다. 길게 풀 것이 있으면 담긴 뒤에 눌러 들어가 이어 쓰면 됩니다. 여기서
 * 본문·태그·분류까지 받으면 다시 여섯 걸음이 되고, 그러면 이 칸을 둔 뜻이 없어집니다.
 */
/**
 * 어디서든 바로 담는 자리.
 *
 * 홈에서 왼쪽으로 밀거나 위젯의 적기를 누르면 뜹니다. 화면을 옮기지 않는 것이 요점입니다.
 * 보던 자리를 잃지 않고, 담고 나면 있던 곳으로 그대로 돌아옵니다.
 *
 * 열자마자 자판이 올라옵니다. 한 번 더 눌러 칸을 깨워야 하면 걸음이 하나 늘어납니다.
 */
@Composable
fun IdeaCaptureDialog(
    onCapture: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focus = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ideaCaptureDialogTag),
        title = { Text("번뜩인 것") },
        text = {
            // 칸이 만들어진 뒤에 불러야 합니다. 바깥에서 부르면 아직 없는 것을 가리킵니다.
            // 그래도 못 잡으면 자판만 안 올라올 뿐, 담는 일은 그대로 됩니다.
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            IdeaCaptureField(
                onCapture = { title ->
                    onCapture(title)
                    onDismiss()
                },
                fieldModifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

@Composable
fun IdeaCaptureField(
    onCapture: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "번뜩인 것 적기",
    /** 칸에만 걸 것. 다이얼로그가 열자마자 자판을 올리려고 씁니다. */
    fieldModifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    fun save() {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        onCapture(trimmed)
        // 담고 나면 비웁니다. 연달아 떠오를 때 지우고 시작하지 않아도 됩니다.
        text = ""
        keyboard?.hide()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .then(fieldModifier)
                .testTag(ideaCaptureFieldTag),
            label = { Text(label) },
            singleLine = true,
            // 자판을 내렸다 담기 버튼을 누르는 것보다 완료 한 번이 빠릅니다.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() })
        )
        Button(
            onClick = ::save,
            enabled = text.isNotBlank(),
            modifier = Modifier.testTag(ideaCaptureSaveTag)
        ) {
            Text("담기")
        }
    }
}
