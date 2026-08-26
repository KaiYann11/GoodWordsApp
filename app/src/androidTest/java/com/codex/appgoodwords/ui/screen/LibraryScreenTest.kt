package com.codex.appgoodwords.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 글귀를 담는 버튼은 글귀 화면 안에 있습니다.
 *
 * 전에는 하단 바의 +였습니다. 하단 바는 "어디로 갈지"만 담아야 읽기 쉬운데 +만 혼자
 * "무엇을 할지"여서 성격이 달랐습니다. 담는 일은 글귀를 보다가 하게 되는 일이라 이 화면에 둡니다.
 */
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theAddButtonSitsInsideTheQuoteScreen() {
        compose.setContent { libraryScreen(items = emptyList()) }

        // +만 있으면 무엇이 담기는지 처음 보는 사람은 알 수 없습니다.
        // 화면에 글자가 보이는 것과, 소리로 읽어 주는 이름이 있는 것은 다른 문제라 둘 다 봅니다.
        compose.onNodeWithText("글귀 담기", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(libraryAddButtonTag).assertIsDisplayed().assertContentDescriptionContains("글귀 담기")
    }

    @Test
    fun tappingItOpensTheAddForm() {
        var opened = false
        compose.setContent { libraryScreen(items = emptyList(), onAddContent = { opened = true }) }

        compose.onNodeWithTag(libraryAddButtonTag).performClick()

        assertTrue("담기 화면이 열리지 않았습니다.", opened)
    }

    @Test
    fun theButtonStaysReachableWithALongList() {
        val many = (1..30).map { index ->
            ContentItemEntity(
                id = index.toLong(),
                syncId = "item-$index",
                type = ContentType.QUOTE,
                title = "글귀 $index",
                body = "본문 $index"
            )
        }
        compose.setContent { libraryScreen(items = many) }

        // 목록 위에 떠 있어야 합니다. 목록 끝에 두면 30개를 굴려야 담을 수 있습니다.
        compose.onNodeWithTag(libraryAddButtonTag).assertIsDisplayed()
    }

    @Test
    fun itOpensOnWhatIsStillUnread() {
        // 읽는 자리가 여기로 왔습니다. 열자마자 이미 넘긴 것을 지나쳐 가며 읽을 것을
        // 찾게 두지 않습니다.
        compose.setContent { libraryScreen(items = threeQuotes, confirmedTodayIds = setOf(1L)) }

        compose.onNodeWithText("글귀 1").assertDoesNotExist()
        compose.onNodeWithText("글귀 2").assertIsDisplayed()
    }

    @Test
    fun theUnreadFilterHidesWhatWasAlreadyReadToday() {
        // 홈에 있던 거르개가 읽는 자리를 따라 여기로 왔습니다. 이것이 없으면
        // 오늘 읽을 것만 골라 보는 방법이 앱에서 사라집니다.
        compose.setContent { libraryScreen(items = threeQuotes, confirmedTodayIds = setOf(1L)) }

        compose.onNodeWithTag(libraryReadFilterTag("UNREAD")).performClick()

        compose.onNodeWithText("글귀 2").assertIsDisplayed()
        compose.onNodeWithText("글귀 1").assertDoesNotExist()
    }

    @Test
    fun theReadFilterShowsOnlyTodaysReading() {
        compose.setContent { libraryScreen(items = threeQuotes, confirmedTodayIds = setOf(1L)) }

        compose.onNodeWithTag(libraryReadFilterTag("READ")).performClick()

        compose.onNodeWithText("글귀 1").assertIsDisplayed()
        compose.onNodeWithText("글귀 2").assertDoesNotExist()
    }

    @Test
    fun anEmptyFilterSaysWhyInsteadOfShowingNothing() {
        compose.setContent { libraryScreen(items = threeQuotes, confirmedTodayIds = emptySet()) }

        compose.onNodeWithTag(libraryReadFilterTag("READ")).performClick()

        compose.onNodeWithText("오늘 읽은 글귀가 아직 없습니다.").assertIsDisplayed()
    }

    private val threeQuotes = (1..3).map { index ->
        ContentItemEntity(
            id = index.toLong(),
            syncId = "item-$index",
            type = ContentType.QUOTE,
            title = "글귀 $index",
            body = "본문 $index"
        )
    }

    @androidx.compose.runtime.Composable
    private fun libraryScreen(
        items: List<ContentItemEntity>,
        confirmedTodayIds: Set<Long> = emptySet(),
        onAddContent: () -> Unit = {}
    ) {
        LibraryScreen(
            items = items,
            categories = emptyList(),
            confirmedTodayIds = confirmedTodayIds,
            onToggleFavorite = {},
            onConfirmItem = {},
            onOpenItem = {},
            onResetTodayConfirmed = {},
            onAddContent = onAddContent
        )
    }
}
