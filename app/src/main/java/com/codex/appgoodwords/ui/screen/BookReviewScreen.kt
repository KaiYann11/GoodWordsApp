package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.codex.appgoodwords.data.BookEntity

internal const val bookReviewFieldTag = "book_review_field"
internal const val bookReviewSaveTag = "book_review_save"
internal const val bookReviewExtractTag = "book_review_extract"
internal const val bookReviewQuoteBodyTag = "book_review_quote_body"
internal const val bookReviewQuoteSaveTag = "book_review_quote_save"

/**
 * 독후감.
 *
 * 책에는 진도만 남습니다. 무엇을 읽었는지는 남지만 **무엇을 생각했는지**는 남지 않아서,
 * 다 읽고 나면 쪽수 말고는 남는 것이 없었습니다.
 *
 * 쓴 글에서 문장을 골라 바로 글귀로 뽑을 수 있습니다. 좋았던 문장을 보관함에 넣으려고
 * 다시 찾아 옮겨 적는 일이 없어야, 읽으면서 쓰는 것과 담아 두는 것이 한 흐름이 됩니다.
 * 고른 자리는 [TextFieldValue.selection]로 읽습니다 — 드래그로 고른 그대로입니다.
 */
@Composable
fun BookReviewScreen(
    book: BookEntity,
    onSave: (String) -> Unit,
    onExtractQuote: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 화면을 처음 열 때의 글에서 시작합니다. 저장할 때마다 새 책이 흘러들어와 덮으면
    // 방금 친 글자가 사라집니다.
    var draft by remember(book.id) { mutableStateOf(TextFieldValue(book.review)) }
    var quoting by remember { mutableStateOf<String?>(null) }
    val haptics = rememberHaptics()

    // 드래그로 고른 자리. TextRange의 min·max로 읽어야 거꾸로 끌어도 같은 곳이 잡힙니다.
    val selected = draft.text.substring(draft.selection.min, draft.selection.max).trim()
    val changed = draft.text.trimEnd() != book.review.trimEnd()

    quoting?.let { body ->
        QuoteFromReviewDialog(
            book = book,
            initialBody = body,
            onDismiss = { quoting = null },
            onSave = { text, page ->
                quoting = null
                haptics.commit()
                onExtractQuote(text, page)
            }
        )
    }

    Column(
        // 자판 자리는 창이 줄면서 Scaffold가 비켜 줍니다(manifest의 adjustResize).
        // 여기서 한 번 더 비키면 글 칸 아래에 쓰지 못하는 빈자리가 생깁니다.
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(book.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = book.displayAuthor,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 버튼을 글 위에 둡니다. 아래에 두면 자판이 올라왔을 때 가려서, 한창 쓰는 중에는
        // 정작 저장도 뽑기도 누를 수 없습니다.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                // 고른 곳이 없으면 누를 수 없습니다. 글 전체가 글귀로 들어가면 되돌리기 번거롭습니다.
                enabled = selected.isNotBlank(),
                onClick = { quoting = selected },
                modifier = Modifier
                    .weight(1f)
                    .testTag(bookReviewExtractTag)
            ) { Text("고른 문장 글귀로") }
            Button(
                enabled = changed,
                onClick = {
                    haptics.commit()
                    onSave(draft.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(bookReviewSaveTag)
            ) { Text(if (changed) "저장" else "저장됨") }
        }

        Text(
            text = if (selected.isBlank()) {
                "글 안에서 문장을 길게 눌러 고르면 그 문장을 글귀로 뽑을 수 있습니다."
            } else {
                "고른 ${selected.length}자를 글귀로 뽑습니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("독후감") },
            placeholder = { Text("읽으며 남기고 싶은 것을 적어 두세요. 다 읽고 한 번에 쓰지 않아도 됩니다.") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(bookReviewFieldTag)
        )
    }
}

/**
 * 고른 문장을 보관함에 넣기 전에 한 번 보여 줍니다.
 *
 * 고른 자리가 한 글자 어긋나는 일이 흔해서, 넣기 전에 고칠 수 있어야 합니다.
 * 쪽수는 지금 진도를 미리 넣어 둡니다 — 대개 방금 읽은 자리입니다.
 */
@Composable
private fun QuoteFromReviewDialog(
    book: BookEntity,
    initialBody: String,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var pageText by remember(initialBody) {
        mutableStateOf(if (book.currentPage > 0) book.currentPage.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("글귀 뽑기") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${book.title} · ${book.displayAuthor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("마음에 남은 문장") },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(bookReviewQuoteBodyTag)
                )
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { value -> pageText = value.filter { it.isDigit() }.take(6) },
                    label = { Text("쪽 (선택)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "독후감은 그대로 둡니다. 뽑은 문장은 보관함에도 함께 남습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = body.isNotBlank(),
                onClick = { onSave(body, pageText.trim().toIntOrNull() ?: 0) },
                modifier = Modifier.testTag(bookReviewQuoteSaveTag)
            ) { Text("보관함에 넣기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
