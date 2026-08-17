package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 앱에 쌓인 모든 기록을 한 번에 찾습니다.
 *
 * 여기서 보는 것은 "찾아야 할 것을 찾는가"와 "안 찾아야 할 것까지 끌고 오지 않는가"입니다.
 */
class AppSearchTest {
    private val quote = ContentItemEntity(
        id = 1,
        syncId = "q1",
        type = ContentType.QUOTE,
        title = "행동에 대하여",
        body = "행동은 감정이 따라올 때까지 기다리면 늘 늦다.",
        author = "제임스 클리어",
        category = "동기부여",
        tags = listOf("시작")
    )
    private val diary = DiaryEntity(
        id = 2,
        syncId = "d1",
        entryDate = "2025-07-14",
        title = "바다",
        body = "여름 바다에 다녀왔다. 오랜만에 마음이 놓였다."
    )
    private val todo = TodoEntity(
        id = 3,
        syncId = "t1",
        title = "우체국 가기",
        note = "등기 부치기",
        dueDate = "2026-08-18"
    )
    private val book = BookEntity(
        id = 4,
        syncId = "b1",
        title = "아주 작은 습관의 힘",
        author = "제임스 클리어",
        note = "3장부터 다시"
    )
    private val routine = RoutineEntity(
        id = 5,
        syncId = "r1",
        title = "아침 산책",
        note = "30분",
        category = "건강"
    )

    private fun search(query: String) = AppSearch.search(
        query = query,
        items = listOf(quote),
        diaries = listOf(diary),
        todos = listOf(todo),
        books = listOf(book),
        routines = listOf(routine)
    )

    @Test
    fun everyKindCanBeFound() {
        // 예전에는 글귀만 찾을 수 있었습니다.
        assertEquals(SearchKind.QUOTE, search("행동").hits.single().kind)
        assertEquals(SearchKind.DIARY, search("바다").hits.single().kind)
        assertEquals(SearchKind.TODO, search("우체국").hits.single().kind)
        assertEquals(SearchKind.BOOK, search("습관").hits.single().kind)
        assertEquals(SearchKind.ROUTINE, search("산책").hits.single().kind)
    }

    @Test
    fun oneWordCanMatchSeveralKinds() {
        // 글귀의 저자이자 책의 저자입니다. 둘 다 나와야 합니다.
        val kinds = search("제임스").hits.map { it.kind }

        assertTrue(kinds.contains(SearchKind.QUOTE))
        assertTrue(kinds.contains(SearchKind.BOOK))
    }

    @Test
    fun everyWordMustAppear() {
        // 통째로 이어 붙여 비교하면 말이 하나만 겹쳐도 걸려서 결과가 쓸모없어집니다.
        assertTrue(search("여름 바다").hits.isNotEmpty())
        assertTrue("없는 말이 섞였는데도 걸렸습니다.", search("여름 산").hits.isEmpty())
    }

    @Test
    fun theSearchIgnoresCase() {
        val item = quote.copy(id = 9, syncId = "q9", title = "Atomic Habits", body = "본문")

        val hits = AppSearch.search(query = "atomic", items = listOf(item)).hits

        assertEquals(1, hits.size)
    }

    @Test
    fun anEmptyQueryFindsNothing() {
        // 빈칸에 전부를 쏟아 내면 검색 화면이 목록 화면이 됩니다.
        assertTrue(search("").isEmpty)
        assertTrue(search("   ").isEmpty)
    }

    @Test
    fun theSnippetShowsWhereItMatched() {
        val long = quote.copy(
            id = 10,
            syncId = "q10",
            title = "긴 글",
            body = "가".repeat(200) + " 여기에걸린다 " + "나".repeat(200)
        )

        val hit = AppSearch.search(query = "여기에걸린다", items = listOf(long)).hits.single()

        // 앞에서부터 자르면 정작 걸린 부분이 안 보입니다.
        assertTrue("조각에 찾은 말이 없습니다: ${hit.snippet}", hit.snippet.contains("여기에걸린다"))
        assertTrue("조각이 너무 깁니다.", hit.snippet.length < 120)
    }

    @Test
    fun resultsAreGroupedInAFixedOrder() {
        val grouped = search("제임스").byKind.map { it.first }

        // 종류마다 자리가 바뀌면 매번 눈으로 다시 찾아야 합니다.
        assertEquals(listOf(SearchKind.QUOTE, SearchKind.BOOK), grouped)
    }

    @Test
    fun eachHitCarriesEnoughToTellThingsApart() {
        val hit = search("바다").hits.single()

        assertEquals("바다", hit.title)
        assertTrue("어느 날 일기인지 알 수 없습니다.", hit.meta.contains("2025-07-14"))
        assertEquals(diary.id, hit.id)
    }

    @Test
    fun aFinishedTodoSaysSo() {
        val done = todo.copy(id = 11, syncId = "t11", doneAt = 5_000L)

        val hit = AppSearch.search(query = "우체국", todos = listOf(done)).hits.single()

        assertTrue(hit.meta.contains("끝냄"))
    }

    @Test
    fun oneKindCannotFloodTheResults() {
        val many = (1..50).map { index ->
            quote.copy(id = index.toLong(), syncId = "q$index", title = "같은 말 $index")
        }

        val hits = AppSearch.search(query = "같은", items = many).hits

        // 한 종류가 화면을 다 차지하면 다른 종류가 밀려 나갑니다.
        assertEquals(AppSearch.LIMIT_PER_KIND, hits.size)
    }

    @Test
    fun aDiaryDateIsSearchable() {
        // "작년 여름에 뭐라고 썼더라"를 날짜로도 찾을 수 있어야 합니다.
        assertEquals(SearchKind.DIARY, search("2025-07").hits.single().kind)
    }
}
