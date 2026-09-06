package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentMemoEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.RoutineEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 글귀 상세에서 메모를 달고 루틴을 뽑는 자리를 봅니다.
 *
 * 글귀 본문은 남의 말이라 고칠 수 없습니다. 읽을 때마다 드는 생각을 옆에 붙이는 길과,
 * 읽고 마는 대신 실천으로 옮기는 길이 여기서 갈립니다.
 */
class DetailMemoAndRoutineTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aMemoIsHandedOverWhenSaved() {
        var saved: String? = null
        compose.setContent { detailScreen(onSaveMemo = { saved = it }) }

        scrollTo(hasTestTag(detailMemoFieldTag))
        compose.onNodeWithTag(detailMemoFieldTag).performTextInput("세 번째 읽으니 다르게 들린다.")
        scrollTo(hasTestTag(detailSaveMemoTag))
        compose.onNodeWithTag(detailSaveMemoTag).performClick()

        assertEquals("세 번째 읽으니 다르게 들린다.", saved)
    }

    @Test
    fun anEmptyMemoCannotBeSaved() {
        compose.setContent { detailScreen() }

        scrollTo(hasTestTag(detailSaveMemoTag))

        compose.onNodeWithTag(detailSaveMemoTag).assertIsNotEnabled()
    }

    @Test
    fun memosAreShownNewestFirst() {
        compose.setContent {
            detailScreen(
                memos = listOf(
                    memo(id = 1, body = "먼저 적은 것", createdAt = 1_000L),
                    memo(id = 2, body = "나중에 적은 것", createdAt = 2_000L)
                )
            )
        }

        scrollTo(hasTestTag(detailMemoFieldTag))

        val bodies = compose.onAllNodesWithTag(detailMemoBodyTag)
        bodies[0].assertTextEquals("나중에 적은 것")
        bodies[1].assertTextEquals("먼저 적은 것")
    }

    @Test
    fun routinesAlreadyExtractedFromThisQuoteAreShown() {
        // 알려 주지 않으면 같은 글귀로 같은 루틴을 또 만듭니다.
        compose.setContent {
            detailScreen(
                routinesFromItem = listOf(
                    RoutineEntity(id = 3, syncId = "r-1", title = "아침에 한 가지 먼저 하기")
                )
            )
        }

        scrollTo(hasTestTag(detailMakeRoutineTag))

        compose.onNodeWithText("이 글귀에서 뽑은 루틴").assertIsDisplayed()
        compose.onNodeWithText("· 아침에 한 가지 먼저 하기").assertIsDisplayed()
    }

    @Test
    fun theRoutineNameIsPrefilledFromTheQuote() {
        var made: String? = null
        compose.setContent { detailScreen(onMakeRoutine = { made = it }) }

        scrollTo(hasTestTag(detailMakeRoutineTag))
        compose.onNodeWithTag(detailMakeRoutineTag).performClick()
        compose.onNodeWithText("만들기").performClick()

        // 본문을 그대로 넣으면 목록에서 잘려 무엇인지 알 수 없습니다.
        assertEquals("오늘의 기준", made)
    }

    /** 화면이 LazyColumn이라 아직 안 그려진 자리는 목록을 굴려야 나옵니다. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNodeWithTag(detailListTag).performScrollToNode(matcher)
    }

    @Composable
    private fun detailScreen(
        memos: List<ContentMemoEntity> = emptyList(),
        routinesFromItem: List<RoutineEntity> = emptyList(),
        onSaveMemo: (String) -> Unit = {},
        onMakeRoutine: (String) -> Unit = {}
    ) {
        DetailScreen(
            item = ContentItemEntity(
                id = 7,
                syncId = "item-1",
                type = ContentType.QUOTE,
                title = "오늘의 기준",
                body = "행동은 감정이 따라올 때까지 기다리면 늘 늦다."
            ),
            confirmedToday = false,
            onEdit = {},
            onDelete = {},
            onConfirm = {},
            onToggleFavorite = {},
            memos = memos,
            onSaveMemo = onSaveMemo,
            onDeleteMemo = {},
            routinesFromItem = routinesFromItem,
            onMakeRoutine = onMakeRoutine
        )
    }

    private fun memo(id: Long, body: String, createdAt: Long) = ContentMemoEntity(
        id = id,
        syncId = "memo-$id",
        updatedAt = createdAt,
        contentItemId = 7,
        contentItemSyncId = "item-1",
        contentTitle = "오늘의 기준",
        body = body,
        createdAt = createdAt
    )
}
