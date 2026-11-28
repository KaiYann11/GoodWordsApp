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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codex.appgoodwords.data.QuoteCandidate
import com.codex.appgoodwords.data.ReviewPiece
import com.codex.appgoodwords.data.ReviewQuotes
import kotlinx.coroutines.delay

internal const val bookReviewFieldTag = "book_review_field"
internal const val bookReviewSaveTag = "book_review_save"
internal const val bookReviewExtractTag = "book_review_extract"
internal const val bookReviewQuoteBodyTag = "book_review_quote_body"
internal const val bookReviewQuotePageTag = "book_review_quote_page"
internal const val bookReviewQuoteSaveTag = "book_review_quote_save"
internal const val bookReviewWriteModeTag = "book_review_mode_write"
internal const val bookReviewPickModeTag = "book_review_mode_pick"
internal const val bookReviewPickBarTag = "book_review_pick_bar"
internal const val bookReviewPickClearTag = "book_review_pick_clear"
internal const val bookReviewCandidatesTag = "book_review_candidates"

internal fun bookReviewSentenceTag(index: Int): String = "book_review_sentence_$index"

internal fun bookReviewCandidateTag(index: Int): String = "book_review_candidate_$index"

/** 치다가 이만큼 멈추면 저절로 저장합니다. */
private const val AUTOSAVE_DELAY_MS = 1_500L

/**
 * 독후감.
 *
 * 책에는 진도만 남습니다. 무엇을 읽었는지는 남지만 **무엇을 생각했는지**는 남지 않아서,
 * 다 읽고 나면 쪽수 말고는 남는 것이 없었습니다.
 *
 * 쓴 글에서 문장을 골라 바로 글귀로 뽑을 수 있습니다. 좋았던 문장을 보관함에 넣으려고
 * 다시 찾아 옮겨 적는 일이 없어야, 읽으면서 쓰는 것과 담아 두는 것이 한 흐름이 됩니다.
 *
 * 고르는 길이 셋입니다(사용자가 고름, 2026-11-28 — 휴대폰에서 손잡이를 끌어 고르기가 어려움).
 * - **글귀 고르기**: 위의 칸을 바꾸면 글이 문장으로 나뉘고, 문장을 톡톡 눌러 고른 뒤 아래 단추 하나로 글귀 창입니다. 쪽은
 *   그 문장 위의 진도 머리 줄(`11월 9일 · 120쪽`)에서 가져옵니다([ReviewQuotes]).
 * - **따옴표 속 문장**: “…”·「…」 안이나 `>`로 시작한 줄은 글 위에 후보로 놓이고, 누르면 바로 글귀 창입니다. 이미 뽑은
 *   것에는 ✓가 붙습니다.
 * - 쓰기 칸에서 길게 눌러 고르기(예전 그대로).
 *
 * **쓴 글은 저절로 저장합니다**(사용자가 고름, 2026-11-28). 전에는 *저장*을 누르지 않고 나가면 친 글이 소리 없이 사라졌습니다
 * (트렌드코리아 2026 독후감이 그렇게 사라진 것으로 봄). 치다가 1.5초 멈추면, 화면을 떠나면, 앱이 뒤로 가면 [onAutoSave]로
 * 알림 없이 저장합니다. 앱이 꺼져도 친 글은 되살아납니다(rememberSaveable).
 */
@Composable
fun BookReviewScreen(
    book: BookEntity,
    onSave: (String) -> Unit,
    onExtractQuote: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** 이 책에서 이미 뽑은 글귀([ReviewQuotes.normalized]로 다듬은 글). 후보에 ✓를 답니다. */
    savedQuotes: Set<String> = emptySet(),
    /** 알림 없이 저장합니다. 화면을 떠난 뒤에도 끝까지 하도록 화면 밖(뷰모델)에서 돌려야 합니다. */
    onAutoSave: (String) -> Unit = {}
) {
    // 화면을 처음 열 때의 글에서 시작합니다. 저장할 때마다 새 책이 흘러들어와 덮으면
    // 방금 친 글자가 사라집니다. 앱이 꺼졌다 켜져도 친 글이 남도록 저장해 둡니다.
    var draft by rememberSaveable(book.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(book.review)) }
    var quoting by remember { mutableStateOf<QuoteDraft?>(null) }
    var picking by rememberSaveable(book.id) { mutableStateOf(false) }
    var picked by remember(book.id) { mutableStateOf(emptySet<Int>()) }
    val haptics = rememberHaptics()

    // 드래그로 고른 자리. TextRange의 min·max로 읽어야 거꾸로 끌어도 같은 곳이 잡힙니다.
    val selected = draft.text.substring(draft.selection.min, draft.selection.max).trim()
    val changed = draft.text.trimEnd() != book.review.trimEnd()

    // 저절로 저장 — 치다가 멈추면, 화면을 떠나면, 앱이 뒤로 가면. 바뀐 것이 없으면 아무것도 하지 않습니다.
    val latestDraft by rememberUpdatedState(draft.text)
    val latestReview by rememberUpdatedState(book.review)
    val latestAutoSave by rememberUpdatedState(onAutoSave)
    val saveIfChanged = remember {
        { if (latestDraft.trimEnd() != latestReview.trimEnd()) latestAutoSave(latestDraft) }
    }
    LaunchedEffect(draft.text) {
        delay(AUTOSAVE_DELAY_MS)
        saveIfChanged()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) saveIfChanged() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveIfChanged()
        }
    }

    val pieces = remember(draft.text) { ReviewQuotes.pieces(draft.text) }
    val sentences = remember(pieces) { pieces.filterIsInstance<ReviewPiece.Sentence>().map { it.sentence } }
    val candidates = remember(draft.text) { ReviewQuotes.candidates(draft.text) }
    // 글이 바뀌어 문장 차례가 달라지면 고른 것을 풉니다. 다른 문장이 골라진 채로 남으면 엉뚱한 글귀가 됩니다.
    LaunchedEffect(sentences.size) { picked = picked.filter { it < sentences.size }.toSet() }

    quoting?.let { quote ->
        QuoteFromReviewDialog(
            book = book,
            initialBody = quote.body,
            initialPage = quote.page,
            onDismiss = { quoting = null },
            onSave = { text, page ->
                quoting = null
                picked = emptySet()
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !picking,
                onClick = { picking = false },
                label = { Text("쓰기") },
                modifier = Modifier.testTag(bookReviewWriteModeTag)
            )
            FilterChip(
                selected = picking,
                onClick = { picking = true },
                label = { Text("글귀 고르기") },
                enabled = sentences.isNotEmpty(),
                modifier = Modifier.testTag(bookReviewPickModeTag)
            )
            Spacer(modifier = Modifier.weight(1f))
            // 저절로 저장되므로 대개 "저장됨"입니다. 바로 저장하고 싶을 때 누릅니다.
            Button(
                enabled = changed,
                onClick = {
                    haptics.commit()
                    onSave(draft.text)
                },
                modifier = Modifier.testTag(bookReviewSaveTag)
            ) { Text(if (changed) "저장" else "저장됨") }
        }

        if (candidates.isNotEmpty()) {
            QuoteCandidates(
                candidates = candidates,
                saved = savedQuotes,
                onPick = { candidate -> quoting = QuoteDraft(candidate.text, candidate.page) }
            )
        }

        if (picking) {
            PickSentences(
                pieces = pieces,
                picked = picked,
                onToggle = { index ->
                    haptics.tap()
                    picked = if (index in picked) picked - index else picked + index
                },
                modifier = Modifier.weight(1f)
            )
            if (picked.isNotEmpty()) {
                val page = ReviewQuotes.pageOf(sentences, picked)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picked = emptySet() }, modifier = Modifier.testTag(bookReviewPickClearTag)) {
                        Text("모두 풀기")
                    }
                    Button(
                        onClick = { quoting = QuoteDraft(ReviewQuotes.quoteText(draft.text, sentences, picked), page) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(bookReviewPickBarTag)
                    ) {
                        Text("${picked.size}문장" + (page?.let { " · ${it}쪽" } ?: "") + " — 글귀로 ›")
                    }
                }
            } else {
                Text(
                    text = "문장을 누르면 골라집니다. 이어진 문장 여러 개도 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selected.isBlank()) {
                        "쓴 글은 저절로 저장됩니다. 글귀는 위 ‘글귀 고르기’에서 문장을 눌러 뽑거나, 글 안을 길게 눌러 골라 뽑습니다."
                    } else {
                        "고른 ${selected.length}자를 글귀로 뽑습니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // 고른 곳이 없으면 누를 수 없습니다. 글 전체가 글귀로 들어가면 되돌리기 번거롭습니다.
                OutlinedButton(
                    enabled = selected.isNotBlank(),
                    onClick = { quoting = QuoteDraft(selected, null) },
                    modifier = Modifier.testTag(bookReviewExtractTag)
                ) { Text("고른 문장 글귀로") }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("독후감") },
                placeholder = { Text("읽으며 남기고 싶은 것을 적어 두세요. 진도 기록에 남긴 말도 날짜·쪽과 함께 여기에 붙습니다.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(bookReviewFieldTag)
            )
        }
    }
}

/** 글귀 창에 넣을 글과 쪽. 쪽이 null이면 창이 지금 진도를 넣습니다. */
private data class QuoteDraft(val body: String, val page: Int?)

/** 따옴표 속 문장 후보. 옆으로 밀어 봅니다. 이미 뽑은 것에는 ✓. */
@Composable
private fun QuoteCandidates(candidates: List<QuoteCandidate>, saved: Set<String>, onPick: (QuoteCandidate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag(bookReviewCandidatesTag)) {
        Text(
            text = "따옴표 속 문장 ${candidates.size}개 — 누르면 글귀로",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(candidates) { index, candidate ->
                val done = ReviewQuotes.normalized(candidate.text) in saved
                AssistChip(
                    onClick = { onPick(candidate) },
                    label = {
                        Text(
                            text = (if (done) "✓ " else "") + "“${candidate.text}”",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 240.dp)
                        )
                    },
                    modifier = Modifier.testTag(bookReviewCandidateTag(index))
                )
            }
        }
    }
}

/** 문장으로 나눈 독후감. 문장을 누르면 골라지고, 고른 문장은 빛깔과 왼쪽 막대로 보입니다. */
@Composable
private fun PickSentences(
    pieces: List<ReviewPiece>,
    picked: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(pieces) { _, piece ->
            when (piece) {
                is ReviewPiece.Header -> Text(
                    text = piece.text,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                ReviewPiece.Gap -> Spacer(modifier = Modifier.height(10.dp))
                is ReviewPiece.Sentence -> {
                    val sentence = piece.sentence
                    val on = sentence.index in picked
                    Text(
                        text = sentence.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .drawBehind {
                                if (on) drawRect(color = accent, size = Size(4.dp.toPx(), size.height))
                            }
                            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(sentence.index) })
                            .testTag(bookReviewSentenceTag(sentence.index))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 고른 문장을 보관함에 넣기 전에 한 번 보여 줍니다.
 *
 * 고른 자리가 한 글자 어긋나는 일이 흔해서, 넣기 전에 고칠 수 있어야 합니다.
 * 쪽수는 그 문장 위 진도 머리 줄의 쪽을, 없으면 지금 진도를 미리 넣어 둡니다 — 대개 방금 읽은 자리입니다.
 */
@Composable
private fun QuoteFromReviewDialog(
    book: BookEntity,
    initialBody: String,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    /** 그 문장 위 진도 머리 줄의 쪽. 없으면 지금 진도입니다. */
    initialPage: Int? = null
) {
    var body by remember(initialBody) { mutableStateOf(initialBody) }
    var pageText by remember(initialBody, initialPage) {
        val page = initialPage ?: book.currentPage
        mutableStateOf(if (page > 0) page.toString() else "")
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(bookReviewQuotePageTag)
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
