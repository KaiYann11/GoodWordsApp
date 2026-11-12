package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.codex.appgoodwords.data.Connections
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType
import com.codex.appgoodwords.data.DayDigest
import com.codex.appgoodwords.data.DayScore
import com.codex.appgoodwords.data.DiaryMood
import com.codex.appgoodwords.data.LinkedRecord
import com.codex.appgoodwords.data.RecordKind
import com.codex.appgoodwords.data.RecordRef
import com.codex.appgoodwords.ui.theme.AppGoodWordsTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * 보관함 "행복" 칸 — 행복한 순간·좋아하는 것과 이어진 기록(사용자가 정함, 2026-11-12, 목업으로 고름).
 *
 * 글에 이름이 나온 좋아하는 것은 미리 골라 두되 직접 뺀 것은 되돌리지 않고, 새로 적은 좋아하는 것은 바로 골라 두며,
 * 상세에는 직접 이은 것과 이름이 나온 곳이 따로 보이고, 풀기는 한 번 더 묻습니다.
 */
class HappyScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val sunset = like(id = 11, syncId = "like-sunset", name = "노을")
    private val coffee = like(id = 12, syncId = "like-coffee", name = "커피")

    @Test
    fun aLikeNamedInTheTextIsPickedAndADroppedOneStaysDropped() {
        var saved: List<String>? = null
        var savedText: String? = null
        var savedDate: LocalDate? = null
        compose.setContent {
            AppGoodWordsTheme {
                HappyMomentList(
                    moments = emptyList(),
                    likes = listOf(sunset, coffee),
                    likesByMoment = emptyMap(),
                    onOpen = {},
                    onSave = { text, date, _, likeIds ->
                        savedText = text
                        savedDate = date
                        saved = likeIds
                        Result.success(1L)
                    },
                    onCreateLike = { Result.success(0L) },
                    onMessage = {}
                )
            }
        }

        compose.onNodeWithTag(happyAddMomentTag).performClick()
        compose.onNodeWithTag(momentTextFieldTag).performTextInput("퇴근길 노을이 너무 예뻤다 ")

        compose.onNodeWithTag(momentLikeChipTag(sunset.syncId)).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(momentLikeChipTag(coffee.syncId)).assertIsNotSelected()
        compose.onNodeWithText("글에 나온 '노을'은(는) 골라 두었습니다.").assertIsDisplayed()

        // 직접 뺀 것은 글에 이름이 있어도 다시 고르지 않습니다.
        compose.onNodeWithTag(momentLikeChipTag(sunset.syncId)).performClick()
        compose.onNodeWithTag(momentLikeChipTag(sunset.syncId)).assertIsNotSelected()
        compose.onNodeWithTag(momentLikeChipTag(coffee.syncId)).performClick()
        compose.onNodeWithTag(momentSaveTag).performClick()

        compose.waitForIdle()
        assertEquals(listOf(coffee.syncId), saved)
        assertEquals("퇴근길 노을이 너무 예뻤다", savedText)
        assertEquals(LocalDate.now(), savedDate)
    }

    @Test
    fun aNewLikeIsPickedAsSoonAsItArrives() {
        var likes by mutableStateOf(listOf(sunset))
        var saved: List<String>? = null
        compose.setContent {
            AppGoodWordsTheme {
                HappyMomentList(
                    moments = emptyList(),
                    likes = likes,
                    likesByMoment = emptyMap(),
                    onOpen = {},
                    onSave = { _, _, _, likeIds ->
                        saved = likeIds
                        Result.success(1L)
                    },
                    onCreateLike = { name ->
                        likes = likes + like(id = 99, syncId = "like-new", name = name)
                        Result.success(99L)
                    },
                    onMessage = {}
                )
            }
        }

        compose.onNodeWithTag(happyAddMomentTag).performClick()
        compose.onNodeWithTag(momentTextFieldTag).performTextInput("친구랑 늦게까지 수다")
        compose.onNodeWithTag(momentNewLikeTag).performScrollTo().performClick()
        compose.onNodeWithTag(momentNewLikeFieldTag).performScrollTo().performTextInput("친구 J")
        compose.onNodeWithTag(momentNewLikeAddTag).performClick()

        compose.onNodeWithTag(momentLikeChipTag("like-new")).performScrollTo().assertIsSelected()
        compose.onNodeWithTag(momentSaveTag).performClick()
        compose.waitForIdle()
        assertEquals(listOf("like-new"), saved)
    }

    @Test
    fun aLikeTakesAChipCategoryOrOneOfItsOwn() {
        var saved: Pair<String, String>? = null
        compose.setContent {
            AppGoodWordsTheme {
                LikeList(
                    likes = emptyList(),
                    momentCounts = emptyMap(),
                    onOpen = {},
                    onSave = { name, category, _, _ ->
                        saved = name to category
                        Result.success(1L)
                    },
                    onMessage = {}
                )
            }
        }

        compose.onNodeWithTag(happyAddLikeTag).performClick()
        compose.onNodeWithTag(likeNameFieldTag).performTextInput("가을 하늘")
        compose.onNodeWithTag(likeCategoryChipTag("장소")).performClick()
        compose.onNodeWithTag(likeCategoryChipTag("장소")).assertIsSelected()
        compose.onNodeWithTag(likeCustomCategoryTag).performClick()
        compose.onNodeWithTag(likeCategoryChipTag("장소")).assertIsNotSelected()
        compose.onNodeWithTag(likeCustomCategoryFieldTag).performScrollTo().performTextInput("계절")
        compose.onNodeWithTag(likeSaveTag).performClick()

        compose.waitForIdle()
        assertEquals("가을 하늘" to "계절", saved)
    }

    @Test
    fun theListShowsHowManyMomentsALikeWasIn() {
        compose.setContent {
            AppGoodWordsTheme {
                LikeList(
                    likes = listOf(sunset, coffee),
                    momentCounts = mapOf(sunset.syncId to 3),
                    onOpen = {},
                    onSave = { _, _, _, _ -> Result.success(1L) },
                    onMessage = {}
                )
            }
        }

        compose.onNodeWithText("행복한 순간 3개").assertIsDisplayed()
        // 있었던 순간이 없으면 아무 숫자도 말하지 않습니다(못 한 것을 세지 않습니다).
        compose.onNodeWithText("행복한 순간 0개").assertDoesNotExist()
        compose.onNodeWithText("장소").assertIsDisplayed()
        compose.onNodeWithText("갈래 없음").assertIsDisplayed()
    }

    @Test
    fun theDetailShowsLinkedAndNamedRecordsAndUnlinkingAsksTwice() {
        val diary = LinkedRecord(RecordRef(RecordKind.DIARY.name, "diary-1"), "일기", "회사 앞 노을", id = 3)
        val moment = LinkedRecord(
            RecordRef(RecordKind.CONTENT_ITEM.name, "moment-1"),
            "행복한 순간",
            "한강 산책",
            id = 5,
            contentType = ContentType.HAPPY_MOMENT,
            snippet = "…한강에서 노을 보며…"
        )
        var opened: LinkedRecord? = null
        var unlinked: RecordRef? = null
        var promoted: RecordRef? = null
        showDetail(
            item = sunset,
            connections = Connections(direct = listOf(diary), mentions = listOf(moment)),
            onOpenLinked = { opened = it },
            onLink = { ref -> promoted = ref; Result.success(true) },
            onUnlink = { ref -> unlinked = ref; Result.success(1) }
        )

        compose.onNodeWithText("행복한 순간 1개에 있었습니다.").assertIsDisplayed()
        compose.onNodeWithText("직접 이은 것").assertIsDisplayed()
        compose.onNodeWithText("이름이 나온 곳").assertIsDisplayed()
        compose.onNodeWithText("\"…한강에서 노을 보며…\"").assertIsDisplayed()

        compose.onNodeWithTag(happyLinkedRowTag("diary-1")).performScrollTo().performClick()
        assertEquals(diary, opened)

        compose.onNodeWithTag(happyUnlinkTag("diary-1")).performScrollTo().performClick()
        assertNull("한 번 눌러서는 풀지 않습니다.", unlinked)
        compose.onNodeWithText("정말 풀기").performClick()
        compose.waitForIdle()
        assertEquals(diary.ref, unlinked)

        compose.onNodeWithTag(happyPromoteTag("moment-1")).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(moment.ref, promoted)
    }

    @Test
    fun theLinkPickerFindsRecordsAndLinksThePickedOne() {
        val book = LinkedRecord(RecordRef(RecordKind.BOOK.name, "book-1"), "책", "사피엔스", id = 7)
        var linked: RecordRef? = null
        showDetail(
            item = sunset,
            connections = Connections(direct = emptyList(), mentions = emptyList()),
            searchRecords = { query -> if ("사피" in query) listOf(book) else emptyList() },
            onLink = { ref -> linked = ref; Result.success(true) }
        )

        compose.onNodeWithTag(happyLinkTag).performScrollTo().performClick()
        compose.onNodeWithTag(happyLinkSearchFieldTag).performTextInput("사피")
        compose.onNodeWithTag(happyLinkResultTag("book-1")).performClick()

        compose.waitForIdle()
        assertEquals(book.ref, linked)
        // 고르면 창이 닫힙니다.
        compose.onNodeWithTag(happyLinkSearchFieldTag).assertDoesNotExist()
    }

    @Test
    fun aMomentShowsWhatElseHappenedThatDay() {
        val day = LocalDate.of(2026, 11, 11)
        val moment = ContentItemEntity(
            id = 21,
            syncId = "moment-21",
            type = ContentType.HAPPY_MOMENT,
            title = "퇴근길 노을",
            body = "퇴근길 노을이 너무 예뻤다",
            createdAt = day.atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        val digest = DayDigest(
            score = DayScore(
                date = day,
                steps = emptyList(),
                doneSteps = emptySet(),
                routineChecks = 2,
                todosDone = 1,
                diaries = 1,
                quotesRead = 0,
                walkSteps = 6_512,
                mood = DiaryMood.GOOD
            ),
            routineTitles = listOf("아침 산책", "독서"),
            todoTitles = listOf("장보기"),
            diaryTitles = listOf("일기"),
            quoteTitles = emptyList()
        )
        var openedDay: LocalDate? = null
        showDetail(item = moment, connections = Connections(emptyList(), emptyList()), sameDay = digest, onOpenDay = { openedDay = it })

        compose.onNodeWithTag(happyDetailListTag).performScrollToNode(hasTestTag(happySameDayTag))
        compose.onNodeWithText("같은 날 · ${formatHappyDate(day)}").assertIsDisplayed()
        compose.onNodeWithText("기분 🙂 좋음").assertIsDisplayed()
        compose.onNodeWithText("6,512걸음").assertIsDisplayed()
        compose.onNodeWithText("루틴 2개 · 아침 산책, 독서").assertIsDisplayed()
        compose.onNodeWithText("할 일 1개 끝냄").assertIsDisplayed()
        compose.onNodeWithTag(happyDayOpenTag).performClick()
        assertEquals(day, openedDay)
    }

    private fun showDetail(
        item: ContentItemEntity,
        connections: Connections,
        sameDay: DayDigest? = null,
        searchRecords: (String) -> List<LinkedRecord> = { emptyList() },
        onOpenLinked: (LinkedRecord) -> Unit = {},
        onLink: suspend (RecordRef) -> Result<Boolean> = { Result.success(true) },
        onUnlink: suspend (RecordRef) -> Result<Int> = { Result.success(1) },
        onOpenDay: (LocalDate) -> Unit = {}
    ) {
        compose.setContent {
            AppGoodWordsTheme {
                HappyDetailScreen(
                    item = item,
                    connections = connections,
                    likes = listOf(sunset, coffee),
                    linkedLikeSyncIds = emptyList(),
                    sameDay = sameDay,
                    sameDayMoments = emptyList(),
                    onSaveMoment = { _, _, _, _ -> Result.success(item.id) },
                    onSaveLike = { _, _, _, _ -> Result.success(item.id) },
                    onCreateLike = { Result.success(0L) },
                    onDelete = { Result.success(Unit) },
                    onOpenLinked = onOpenLinked,
                    onOpenMoment = {},
                    onLink = onLink,
                    onUnlink = onUnlink,
                    searchRecords = searchRecords,
                    onOpenDay = onOpenDay,
                    onMessage = {}
                )
            }
        }
    }

    private fun like(id: Long, syncId: String, name: String) = ContentItemEntity(
        id = id,
        syncId = syncId,
        type = ContentType.LIKE,
        title = name,
        body = "",
        category = if (name == "노을") "장소" else ""
    )
}
