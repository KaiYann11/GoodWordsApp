package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 공유해 온 글을 어떻게 나누는지 봅니다.
 *
 * 여기가 틀리면 유튜브에서 담은 것이 영상이 아니라 글귀가 되고, 주소가 본문에 통째로
 * 박혀 썸네일도 원본 열기도 안 됩니다. 담는 화면 없이 저장하므로 사용자가 알아채기 전에
 * 보관함에 그대로 쌓입니다.
 */
class SharedTextTest {
    @Test
    fun youtubeSendsTheTitleAndTheLinkTogether() {
        // 유튜브가 실제로 보내는 모양입니다.
        val draft = SharedText.toDraft("나를 바꾼 15분\nhttps://youtu.be/dQw4w9WgXcQ?si=abc123")

        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc123", draft?.sourceUrl)
        assertEquals("나를 바꾼 15분", draft?.title)
        // 제목으로 다 담긴 글을 본문에 또 넣지 않습니다.
        assertEquals("", draft?.body)
    }

    @Test
    fun theTypeBecomesVideoForYoutube() {
        // 주소가 본문에 박히면 종류를 못 알아내 글귀가 됩니다.
        val draft = SharedText.toDraft("나를 바꾼 15분\nhttps://youtu.be/dQw4w9WgXcQ")

        assertEquals(ContentType.VIDEO, ContentNormalizer.detectType(draft!!))
    }

    @Test
    fun aBareLinkStillWorks() {
        val draft = SharedText.toDraft("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", draft?.sourceUrl)
        assertEquals("", draft?.body)
    }

    @Test
    fun theLinkIsFoundEvenInTheMiddle() {
        val draft = SharedText.toDraft("이거 봐 https://youtu.be/abc 진짜 좋다")

        assertEquals("https://youtu.be/abc", draft?.sourceUrl)
        assertEquals("이거 봐 진짜 좋다", draft?.title)
    }

    @Test
    fun trailingPunctuationIsNotPartOfTheLink() {
        // 남겨 두면 그 주소로는 열리지 않습니다.
        val draft = SharedText.toDraft("좋은 영상(https://youtu.be/abc).")

        assertEquals("https://youtu.be/abc", draft?.sourceUrl)
    }

    @Test
    fun plainTextBecomesAQuote() {
        val draft = SharedText.toDraft("행동은 감정이 따라올 때까지 기다리면 늘 늦다.")

        assertEquals("", draft?.sourceUrl)
        // 주소가 없으면 글 자체가 알맹이라 본문에 담깁니다. 제목만 채우면 담기지 않습니다
        // ("본문·링크·사진·영상 중 하나는 있어야 한다").
        assertEquals("행동은 감정이 따라올 때까지 기다리면 늘 늦다.", draft?.body)
        assertEquals(ContentType.QUOTE, ContentNormalizer.detectType(draft!!))
    }

    @Test
    fun aQuoteGetsItsTitleWhenItIsKept() {
        val draft = SharedText.toDraft("행동은 감정이 따라올 때까지 기다리면 늘 늦다.")!!

        // 직접 적어 담는 것과 같은 모양이어야 합니다 — 제목은 본문 앞부분에서 끌어옵니다.
        val normalized = ContentNormalizer.normalize(draft)

        assertEquals("행동은 감정이 따라올 때까지 기다리면 늘 늦다.", normalized.body)
        assertTrue(
            "제목이 본문 앞부분이 아닙니다: ${normalized.title}",
            normalized.body.startsWith(normalized.title)
        )
        assertTrue("제목이 비었습니다.", normalized.title.isNotBlank())
    }

    @Test
    fun aLongSharedTitleIsCutForTheTitleButKeptInTheBody() {
        val long = "가".repeat(80)

        val draft = SharedText.toDraft("$long\nhttps://youtu.be/abc")

        // 제목은 목록에서 잘리지 않을 만큼만, 나머지는 본문에 남습니다.
        assertEquals(60, draft?.title?.length)
        assertEquals(long, draft?.body)
    }

    @Test
    fun theSubjectFillsInWhenThereIsOnlyALink() {
        val draft = SharedText.toDraft(
            text = "https://youtu.be/abc",
            subject = "나를 바꾼 15분"
        )

        assertEquals("나를 바꾼 15분", draft?.title)
        assertEquals("https://youtu.be/abc", draft?.sourceUrl)
    }

    @Test
    fun theBodyWinsOverTheSubject() {
        // 본문에 제목이 있으면 그것이 사람이 고른 말입니다.
        val draft = SharedText.toDraft(
            text = "직접 적은 제목\nhttps://youtu.be/abc",
            subject = "앱이 붙인 제목"
        )

        assertEquals("직접 적은 제목", draft?.title)
    }

    @Test
    fun nothingToKeepReturnsNull() {
        assertNull(SharedText.toDraft(null))
        assertNull(SharedText.toDraft("   "))
        assertNull(SharedText.toDraft("\n\n"))
    }
}
