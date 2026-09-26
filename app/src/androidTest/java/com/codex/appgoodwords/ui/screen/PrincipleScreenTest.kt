package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * 원칙 화면.
 *
 * 하지 않을 일과 같은 뼈대를 쓰지만, **원칙으로 저장되는지**와 **수행 버튼이 없는지**는
 * 여기서 따로 봅니다. 종류가 섞이면 적어 둔 기준이 반대편 탭에 가서 붙습니다.
 */
class PrincipleScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun whatIsTypedIsSavedAsAPrinciple() {
        var saved: ContentDraft? = null
        compose.setContent {
            AppGoodWordsTheme {
                PrincipleScreen(
                    items = emptyList(),
                    onDelete = { Result.success(Unit) },
                    onSave = { saved = it; Result.success(Unit) }
                )
            }
        }

        compose.onNodeWithTag(principleAddTag).performClick()
        compose.onNodeWithText("저장").assertIsNotEnabled()
        compose.onNodeWithTag(principleTitleTag).performTextInput("먼저 듣고 나중에 말한다")
        compose.onNodeWithTag(principleMemoTag).performTextInput("말이 앞서면 후회가 남는다")
        compose.onNodeWithText("저장").performClick()

        assertEquals(ContentType.PRINCIPLE, saved?.type)
        assertEquals("먼저 듣고 나중에 말한다", saved?.title)
        assertEquals("말이 앞서면 후회가 남는다", saved?.body)
    }

    @Test fun thereIsNoWayToCheckOffAPrinciple() {
        val item = ContentItemEntity(
            id = 1, type = ContentType.PRINCIPLE, title = "몸이 힘들면 하루 쉰다", body = "미루는 것과 쉬는 것은 다르다"
        )
        compose.setContent {
            AppGoodWordsTheme {
                PrincipleScreen(
                    items = listOf(item),
                    onSave = { Result.success(Unit) },
                    onDelete = { Result.success(Unit) }
                )
            }
        }

        compose.onNodeWithText(item.title).assertIsDisplayed()
        compose.onNodeWithText(item.body).assertIsDisplayed()
        // 지킨 날을 세기 시작하면 원칙이 또 하나의 루틴이 됩니다.
        compose.onNodeWithText("수행 +1").assertDoesNotExist()
        compose.onNodeWithText("읽음").assertDoesNotExist()
        // 지킨 날을 세지 않는다는 것을 화면에서 먼저 말해 둡니다.
        compose.onNodeWithText("지킨 날을 세지 않습니다", substring = true).assertIsDisplayed()
    }

    @Test fun onlyPrinciplesAreListedEvenWhenOtherRulesAreMixedIn() {
        val principle = ContentItemEntity(id = 1, type = ContentType.PRINCIPLE, title = "먼저 듣는다", body = "")
        val avoid = ContentItemEntity(id = 2, type = ContentType.NOT_TO_DO, title = "잠들기 전 숏폼", body = "")
        val quote = ContentItemEntity(id = 3, type = ContentType.QUOTE, title = "글귀", body = "본문")
        val records = mutableStateOf(listOf(principle, avoid, quote))
        compose.setContent {
            AppGoodWordsTheme {
                PrincipleScreen(
                    items = records.value,
                    onSave = { Result.success(Unit) },
                    onDelete = { Result.success(Unit) }
                )
            }
        }

        compose.onNodeWithText(principle.title).assertIsDisplayed()
        compose.onNodeWithText(avoid.title).assertDoesNotExist()
        compose.onNodeWithText(quote.title).assertDoesNotExist()
        compose.onNodeWithText("원칙 1개").assertIsDisplayed()
    }

    @Test fun aFailedDeletionKeepsTheRecordAndCanBeRetried() {
        val item = ContentItemEntity(id = 1, type = ContentType.PRINCIPLE, title = "화가 난 채로 답하지 않는다", body = "")
        val records = mutableStateOf(listOf(item))
        var attempts = 0
        compose.setContent {
            AppGoodWordsTheme {
                PrincipleScreen(
                    items = records.value,
                    onSave = { Result.success(Unit) },
                    onDelete = {
                        attempts++
                        if (attempts == 1) Result.failure(IllegalStateException("삭제 실패"))
                        else { records.value = emptyList(); Result.success(Unit) }
                    }
                )
            }
        }

        compose.onNodeWithTag("principle_delete_1").performClick()
        compose.onNodeWithTag("principle_confirm_delete").performClick()
        compose.onNodeWithText("삭제 실패").assertIsDisplayed()
        compose.onNodeWithTag("principle_confirm_delete").performClick()
        compose.onNodeWithText(item.title).assertDoesNotExist()
        assertEquals(2, attempts)
    }
}
