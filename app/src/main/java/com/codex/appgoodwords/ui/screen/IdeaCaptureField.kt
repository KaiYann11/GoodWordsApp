package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

internal const val ideaCaptureFieldTag = "idea_capture_field"
internal const val ideaCaptureSaveTag = "idea_capture_save"

/**
 * 번뜩인 것을 그 자리에서 한 줄로 담습니다.
 *
 * 담는 화면까지 들어가면 여섯 걸음입니다. 번뜩인 것은 그 사이에 날아갑니다. 여기서는
 * **치고 누르면 끝**입니다. 자판의 완료(엔터)로도 담깁니다.
 *
 * 제목만 받습니다. 길게 풀 것이 있으면 담긴 뒤에 눌러 들어가 이어 쓰면 됩니다. 여기서
 * 본문·태그·분류까지 받으면 다시 여섯 걸음이 되고, 그러면 이 칸을 둔 뜻이 없어집니다.
 */
@Composable
fun IdeaCaptureField(
    onCapture: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "번뜩인 것 적기"
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
