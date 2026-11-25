package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 모델 목록을 받는 세 곳(내 서버·Claude API·OpenAI API)의 꼴과, 이 기기에 적어 두는 꼴. */
class AiModelListJsonTest {
    @Test
    fun theServerListKeepsItsOrder() {
        val models = AiModelListJson.parseServer(
            """{"provider":"codex_cli","models":[{"id":"gpt-6.1-terra","label":"GPT-6.1 Terra","createdAt":0},""" +
                """{"id":"gpt-6-luna","label":"","createdAt":0},{"id":""}],"fetchedAt":1}"""
        )

        assertEquals(listOf("gpt-6.1-terra", "gpt-6-luna"), models.map { it.id })
        assertEquals("GPT-6.1 Terra", models[0].label)
        // 이름표가 비면 이름을 씁니다.
        assertEquals("gpt-6-luna", models[1].label)
    }

    @Test
    fun aClaudePageNamesTheNextPageOnlyWhenThereIsMore() {
        val (first, next) = AiModelListJson.parseAnthropicPage(
            """{"data":[{"type":"model","id":"claude-fable-5-2","display_name":"Claude Fable 5.2",""" +
                """"created_at":"2026-11-01T00:00:00Z"}],"has_more":true,"first_id":"claude-fable-5-2","last_id":"claude-fable-5-2"}"""
        )
        val (last, none) = AiModelListJson.parseAnthropicPage(
            """{"data":[{"id":"claude-opus-5-5","display_name":"Claude Opus 5.5","created_at":"2026-10-01T09:00:00+09:00"}],""" +
                """"has_more":false,"last_id":"claude-opus-5-5"}"""
        )

        assertEquals("Claude Fable 5.2", first.single().label)
        assertEquals(java.time.Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(), first.single().createdAt)
        assertEquals("claude-fable-5-2", next)
        assertEquals(java.time.Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), last.single().createdAt)
        assertNull(none)
    }

    @Test
    fun theOpenAiListKeepsTextModelsNewestFirst() {
        val models = AiModelListJson.parseOpenAi(
            """{"object":"list","data":[{"id":"gpt-6-luna","created":100},{"id":"text-embedding-3-large","created":300},""" +
                """{"id":"gpt-6.1-terra","created":200},{"id":"gpt-image-1","created":400},{"id":"o4-mini","created":50}]}"""
        )

        assertEquals(listOf("gpt-6.1-terra", "gpt-6-luna", "o4-mini"), models.map { it.id })
        assertEquals(200_000L, models[0].createdAt)
    }

    @Test
    fun aSavedListReadsBackTheSame() {
        val list = AiModelList(
            models = listOf(AiModelInfo("claude-fable-5-2", "Claude Fable 5.2", 5L), AiModelInfo("claude-opus-5-5")),
            fetchedAt = 1_764_000_000_000L
        )

        assertEquals(list, AiModelListJson.decode(AiModelListJson.encode(list)))
    }

    @Test
    fun aMissingOrBrokenSavedListIsTreatedAsNeverFetched() {
        assertEquals(AiModelList(), AiModelListJson.decode(null))
        assertEquals(AiModelList(), AiModelListJson.decode(""))
        assertEquals(AiModelList(), AiModelListJson.decode("{not json"))
    }

    @Test
    fun aListIsStaleWhenNeverFetchedOrOld() {
        assertEquals(true, AiModelList().isStale(now = 10_000L, maxAgeMillis = 1_000L))
        assertEquals(false, AiModelList(fetchedAt = 9_500L).isStale(now = 10_000L, maxAgeMillis = 1_000L))
        assertEquals(true, AiModelList(fetchedAt = 8_000L).isStale(now = 10_000L, maxAgeMillis = 1_000L))
    }

    @Test
    fun theResolvedNamesAreKeyedByWhatWasSent() {
        val resolved = AiModelListJson.parseResolved(
            """{"provider":"claude_cli","models":[{"requested":"","model":"claude-opus-5-5","error":""},""" +
                """{"requested":"sonnet","model":"claude-sonnet-5-5","error":""},""" +
                """{"requested":"haiku","model":"","error":"사용 한도에 걸렸습니다"}],"resolvedAt":1}"""
        )

        assertEquals(AiResolvedModel("claude-opus-5-5"), resolved[""])
        assertEquals(AiResolvedModel("claude-sonnet-5-5"), resolved["sonnet"])
        assertEquals(AiResolvedModel("", "사용 한도에 걸렸습니다"), resolved["haiku"])
    }

    @Test
    fun aCheckSurvivesTheRoundTripWithTheBlankChoice() {
        val check = AiModelCheck(
            checkedAt = 1_764_050_000_000L,
            names = mapOf("" to "claude-opus-5-5", "latest:sonnet" to "claude-sonnet-5-5"),
            errors = mapOf("latest:haiku" to "사용 한도")
        )

        assertEquals(check, AiModelListJson.decodeCheck(AiModelListJson.encodeCheck(check)))
        assertEquals(AiModelCheck(), AiModelListJson.decodeCheck(null))
        assertEquals(AiModelCheck(), AiModelListJson.decodeCheck("{꼴이 어긋남"))
    }
}
