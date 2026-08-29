package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 번뜩인 것은 "오늘의 글귀"로 떠오르지 않습니다.
 *
 * 알림과 위젯이 말하는 것은 오늘의 글귀인데, 내가 방금 적어 둔 생각이 거기 뜨면 남의 좋은
 * 말인 척 돌아옵니다. 게다가 새로 담은 것은 `lastSurfacedAt`이 없어 가장 먼저 뽑힙니다.
 *
 * 뽑는 일 자체는 SQL에 있어 여기서 돌릴 수 없습니다. 대신 **어느 종류가 떠오를 자격이 있는지**를
 * 한곳에 적어 두고, 그 규칙이 흔들리지 않게 못 박습니다.
 */
class IdeaSurfacingTest {
    @Test
    fun anIdeaIsNotSomethingToBeShownAsAQuote() {
        assertTrue(ContentType.IDEA in ContentType.entries)
        // 담기는 자리는 같아도 떠오르는 자리는 다릅니다.
        assertEquals(ContentType.IDEA, ContentType.valueOf("IDEA"))
    }

    @Test
    fun everyOtherKindIsStillSomethingToRevisit() {
        // 글귀·링크·영상은 모아 두었다가 다시 만나려고 담은 것입니다.
        val revisitable = ContentType.entries.filter { it != ContentType.IDEA }

        assertEquals(listOf(ContentType.QUOTE, ContentType.LINK, ContentType.VIDEO), revisitable)
    }

    @Test
    fun theKindIsStoredByNameSoTheQueryCanCompareIt() {
        // 뽑는 질의가 `type <> :excluded`로 거릅니다. 이름이 바뀌면 그 비교가 조용히 빗나갑니다.
        assertEquals("IDEA", Converters().fromContentType(ContentType.IDEA))
        assertEquals(ContentType.IDEA, Converters().toContentType("IDEA"))
    }
}
