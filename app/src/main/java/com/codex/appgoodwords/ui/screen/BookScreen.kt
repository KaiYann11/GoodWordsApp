package com.codex.appgoodwords.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.codex.appgoodwords.data.BookDraft
import com.codex.appgoodwords.data.BookEntity

internal const val bookAddButtonTag = "book_add_button"
internal const val bookTitleInputTag = "book_title_input"
internal const val bookSaveButtonTag = "book_save_button"
internal const val bookQuoteBodyTag = "book_quote_body"
internal const val bookQuoteSaveTag = "book_quote_save"

internal fun bookExtractButtonTag(bookId: Long) = "book_extract_$bookId"

internal fun bookProgressButtonTag(bookId: Long) = "book_progress_$bookId"

/**
 * 읽고 있는 책과 읽은 책.
 *
 * 읽는 중인 책이 위로 옵니다. 다 읽은 책은 아래로 내려가 목록을 가리지 않습니다.
 * 읽는 중인 책에서는 그 자리에서 글귀를 뽑아 보관함에 넣을 수 있습니다.
 */
@Composable
fun BookScreen(
    books: List<BookEntity>,
    onSaveBook: (BookDraft) -> Unit,
    onUpdateProgress: (Long, Int) -> Unit,
    onToggleFinished: (Long) -> Unit,
    onDeleteBook: (Long) -> Unit,
    onExtractQuote: (Long, String, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 책마다 그 책에서 뽑은 글귀가 몇 개인지. syncId로 셉니다. */
    quoteCountBySyncId: Map<String, Int> = emptyMap(),
    /** 검색에서 고른 책. 그 자리로 굴려 주고 잠깐 강조합니다. */
    focusId: Long? = null
) {
    var editing by remember { mutableStateOf<BookDraft?>(null) }
    var pendingDelete by remember { mutableStateOf<BookEntity?>(null) }
    var progressTarget by remember { mutableStateOf<BookEntity?>(null) }
    var quoteTarget by remember { mutableStateOf<BookEntity?>(null) }

    val reading = books.filterNot { it.isFinished }
    val finished = books.filter { it.isFinished }

    val listState = rememberLazyListState()
    // 안내 카드 1 + (읽는 중 제목 1 + 읽는 중 책들) + (읽은 책 제목 1 + 읽은 책들) 순서입니다.
    val focusIndex = remember(focusId, reading, finished) {
        val inReading = reading.indexOfFirst { it.id == focusId }
        if (inReading >= 0) return@remember 2 + inReading

        val inFinished = finished.indexOfFirst { it.id == focusId }
        if (inFinished < 0) return@remember null
        // 읽는 중이 없으면 그 묶음(제목 + 책들)이 통째로 빠집니다.
        val readingBlock = if (reading.isEmpty()) 0 else 1 + reading.size
        2 + readingBlock + inFinished
    }
    ScrollToFocus(listState = listState, index = focusIndex, key = focusId)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("독서", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "읽고 있는 책의 진도를 남기고, 그 자리에서 좋은 글귀를 뽑아 보관함에 넣을 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { editing = BookDraft() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(bookAddButtonTag)
                    ) {
                        Text("책 추가")
                    }
                }
            }
        }

        if (books.isEmpty()) {
            item {
                Text(
                    text = "아직 담아 둔 책이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (reading.isNotEmpty()) {
            item { SectionTitle("읽고 있는 책 ${reading.size}권") }
            items(reading, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    focused = book.id == focusId,
                    focusKey = focusId,
                    quoteCount = quoteCountBySyncId[book.syncId] ?: 0,
                    onEdit = { editing = BookDraft.from(book) },
                    onProgress = { progressTarget = book },
                    onToggleFinished = { onToggleFinished(book.id) },
                    onExtract = { quoteTarget = book },
                    onDelete = { pendingDelete = book }
                )
            }
        }

        if (finished.isNotEmpty()) {
            item { SectionTitle("읽은 책 ${finished.size}권") }
            items(finished, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    focused = book.id == focusId,
                    focusKey = focusId,
                    quoteCount = quoteCountBySyncId[book.syncId] ?: 0,
                    onEdit = { editing = BookDraft.from(book) },
                    onProgress = { progressTarget = book },
                    onToggleFinished = { onToggleFinished(book.id) },
                    onExtract = { quoteTarget = book },
                    onDelete = { pendingDelete = book }
                )
            }
        }
    }

    editing?.let { draft ->
        BookEditDialog(
            draft = draft,
            onDismiss = { editing = null },
            onSave = {
                onSaveBook(it)
                editing = null
            }
        )
    }

    progressTarget?.let { book ->
        ProgressDialog(
            book = book,
            onDismiss = { progressTarget = null },
            onSave = { page ->
                onUpdateProgress(book.id, page)
                progressTarget = null
            }
        )
    }

    quoteTarget?.let { book ->
        QuoteDialog(
            book = book,
            onDismiss = { quoteTarget = null },
            onSave = { body, page ->
                onExtractQuote(book.id, body, page)
                quoteTarget = null
            }
        )
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("책을 지울까요?") },
            text = { Text("${book.title}을(를) 목록에서 지웁니다. 이 책에서 뽑아 둔 글귀는 보관함에 남습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteBook(book.id)
                    pendingDelete = null
                }) {
                    Text("지우기")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BookCard(
    book: BookEntity,
    quoteCount: Int,
    onEdit: () -> Unit,
    onProgress: () -> Unit,
    onToggleFinished: () -> Unit,
    onExtract: () -> Unit,
    onDelete: () -> Unit,
    focused: Boolean = false,
    focusKey: Any? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(focused = focused, key = focusKey)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (book.isFinished) TextDecoration.LineThrough else null
                    )
                    Text(
                        text = book.displayAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "지우기")
                }
            }

            Text(
                text = progressText(book),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 전체 쪽수를 모르면 막대를 그리지 않습니다. 0%라고 단정하면 안 읽은 것처럼 보입니다.
            book.progress?.let { ratio ->
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (book.note.isNotBlank()) {
                Text(book.note, style = MaterialTheme.typography.bodyMedium)
            }
            if (quoteCount > 0) {
                Text(
                    text = "이 책에서 뽑은 글귀 ${quoteCount}개",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExtract,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(bookExtractButtonTag(book.id))
                ) {
                    Text("글귀 뽑기")
                }
                OutlinedButton(
                    onClick = onProgress,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(bookProgressButtonTag(book.id))
                ) {
                    Text("진도 기록")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("수정") }
                TextButton(onClick = onToggleFinished) {
                    Text(if (book.isFinished) "다시 읽는 중" else "다 읽음")
                }
            }
        }
    }
}

private fun progressText(book: BookEntity): String {
    val percent = book.progress?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    return if (book.totalPages > 0) {
        "${book.currentPage} / ${book.totalPages}쪽$percent"
    } else {
        // 전체 쪽수를 모를 때도 어디까지 읽었는지는 남길 수 있어야 합니다.
        "${book.currentPage}쪽까지"
    }
}

@Composable
private fun BookEditDialog(
    draft: BookDraft,
    onDismiss: () -> Unit,
    onSave: (BookDraft) -> Unit
) {
    var current by remember { mutableStateOf(draft) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == 0L) "책 추가" else "책 수정") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { current = current.copy(title = it) },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(bookTitleInputTag)
                )
                OutlinedTextField(
                    value = current.author,
                    onValueChange = { current = current.copy(author = it) },
                    label = { Text("저자 (선택)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = current.currentPageText,
                        onValueChange = { value -> current = current.copy(currentPageText = value.digitsOnly()) },
                        label = { Text("읽은 쪽") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = current.totalPagesText,
                        onValueChange = { value -> current = current.copy(totalPagesText = value.digitsOnly()) },
                        label = { Text("전체 쪽 (선택)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = current.note,
                    onValueChange = { current = current.copy(note = it) },
                    label = { Text("메모 (선택)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.canSave,
                onClick = { onSave(current) },
                modifier = Modifier.testTag(bookSaveButtonTag)
            ) {
                Text("저장")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun ProgressDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var pageText by remember { mutableStateOf(if (book.currentPage > 0) book.currentPage.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어디까지 읽었나요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.digitsOnly() },
                    label = { Text("읽은 쪽") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (book.totalPages > 0) {
                    Text(
                        text = "전체 ${book.totalPages}쪽. 마지막 쪽에 닿으면 다 읽은 책으로 옮깁니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pageText.trim().toIntOrNull() ?: 0) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun QuoteDialog(
    book: BookEntity,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    var body by remember { mutableStateOf("") }
    var pageText by remember { mutableStateOf(if (book.currentPage > 0) book.currentPage.toString() else "") }

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
                        .testTag(bookQuoteBodyTag)
                )
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.digitsOnly() },
                    label = { Text("쪽 (선택)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "저자와 제목은 책에서 채웁니다. 적은 쪽이 지금 진도보다 뒤면 진도도 함께 옮깁니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = body.isNotBlank(),
                onClick = { onSave(body, pageText.trim().toIntOrNull() ?: 0) },
                modifier = Modifier.testTag(bookQuoteSaveTag)
            ) {
                Text("보관함에 넣기")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 숫자만 남깁니다. 문자가 섞이면 쪽수를 못 읽어 진도가 0으로 떨어집니다. */
private fun String.digitsOnly(): String = filter { it.isDigit() }.take(6)
