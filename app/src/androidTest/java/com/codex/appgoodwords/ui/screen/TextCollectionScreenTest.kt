package com.codex.appgoodwords.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentMemoEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.ConceptExplanation
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TextCollectionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val item = ContentItemEntity(id = 1, type = ContentType.INBOX, title = "용어", body = "찾아본 용어의 원문")

    @Test
    fun pasteCapturesTextOnlyWhenTheButtonIsPressed() {
        var captured: String? = null
        compose.setContent {
            AppGoodWordsTheme {
                TextCollectionScreen(emptyList(), ContentType.INBOX, onOpenItem = {}, onPaste = { captured = it })
            }
        }
        compose.runOnIdle {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("시험", "복사한 문장\n두 번째 줄"))
        }
        assertEquals(null, captured)
        compose.onNodeWithTag(inboxPasteTag).performClick()
        assertEquals("복사한 문장\n두 번째 줄", captured)
    }

    @Test
    fun classificationMovesTheCardIntoTheSeparateMemoTab() {
        val items = mutableStateOf(listOf(item))
        val choosing = mutableStateOf(false)
        compose.setContent {
            AppGoodWordsTheme {
                LibraryTabsScreen(
                    quoteContent = {}, bookContent = {}, requestedTab = LibraryTab.INBOX,
                    inboxContent = {
                        TextCollectionScreen(items.value.filter { it.type == ContentType.INBOX }, ContentType.INBOX,
                            onOpenItem = {}, onClassifyItem = { choosing.value = true })
                    },
                    memoContent = {
                        TextCollectionScreen(items.value.filter { it.type == ContentType.MEMO }, ContentType.MEMO, onOpenItem = {})
                    }
                )
                if (choosing.value) ClassifyInboxDialog(item, false,
                    onSelect = { type -> items.value = listOf(item.copy(type = type)); choosing.value = false },
                    onDismiss = { choosing.value = false })
            }
        }
        compose.onNodeWithTag(inboxClassifyTag(item.id)).performClick()
        compose.onNodeWithText("메모로 이동").performClick()
        compose.onNodeWithText(item.body).assertDoesNotExist()
        compose.onNodeWithTag(libraryMemoTabTag).performClick()
        compose.onNodeWithText(item.body).assertIsDisplayed()
        compose.onNodeWithTag(inboxClassifyTag(item.id)).assertDoesNotExist()
    }

    @Test
    fun cancellingClassificationLeavesTheOriginalAlone() {
        var chosen: ContentType? = null
        var dismissed = false
        compose.setContent {
            AppGoodWordsTheme {
                ClassifyInboxDialog(item, false, onSelect = { chosen = it }, onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("취소").performClick()
        assertEquals(null, chosen)
        assertEquals(true, dismissed)
    }

    @Test
    fun askingAiMovesTheOriginalAndShowsTheAnswerInTheConceptTab() {
        val items = mutableStateOf(listOf(item))
        val memos = mutableStateOf(emptyList<ContentMemoEntity>())
        compose.setContent {
            AppGoodWordsTheme {
                LibraryTabsScreen(
                    quoteContent = {},
                    bookContent = {},
                    requestedTab = LibraryTab.INBOX,
                    inboxContent = {
                        TextCollectionScreen(
                            items = items.value.filter { it.type == ContentType.INBOX },
                            collectionType = ContentType.INBOX,
                            onOpenItem = {},
                            onAskConcept = {
                                items.value = listOf(item.copy(type = ContentType.CONCEPT))
                                memos.value = listOf(
                                    ContentMemoEntity(
                                        contentItemId = item.id,
                                        contentItemSyncId = item.syncId,
                                        contentTitle = item.title,
                                        body = ConceptExplanation.memoBody("쉽게 이해할 수 있는 설명")
                                    )
                                )
                            }
                        )
                    },
                    conceptContent = {
                        TextCollectionScreen(
                            items = items.value.filter { it.type == ContentType.CONCEPT },
                            collectionType = ContentType.CONCEPT,
                            contentMemos = memos.value,
                            onOpenItem = {}
                        )
                    }
                )
            }
        }

        compose.onNodeWithTag(inboxAskConceptTag(item.id)).performClick()
        compose.onNodeWithText(item.body).assertDoesNotExist()
        compose.onNodeWithTag(libraryConceptTabTag).performClick()
        compose.onNodeWithText("쉽게 이해할 수 있는 설명").assertIsDisplayed()
    }

    @Test
    fun editingAMemoDoesNotTurnItIntoAQuote() {
        var saved: ContentDraft? = null
        compose.setContent {
            AppGoodWordsTheme {
                AddContentScreen(
                    initialDraft = ContentDraft(id = 1, type = ContentType.MEMO, body = "메모의 원문"),
                    categories = emptyList(), existingTags = emptyList(), sharedText = null,
                    formVersion = 0, submitLabel = "저장", secondaryActionLabel = null,
                    onSecondaryAction = null, onSharedTextConsumed = {}, onFetchMetadata = { _, _ -> },
                    onSave = { saved = it }
                )
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("저장"))
        compose.onNodeWithText("저장").performClick()
        assertEquals(ContentType.MEMO, saved?.type)
        assertEquals("메모의 원문", saved?.body)
    }
}
