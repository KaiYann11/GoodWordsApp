package com.codex.appgoodwords.data

import org.json.JSONObject

/**
 * 코치가 보낸 글을 한마디로 바꿉니다.
 *
 * JSON만 달라고 일러 두어도 코드펜스로 감싸거나 앞뒤에 인사말을 붙여 오는 때가 있습니다
 * ([GrowthReportParser]와 같은 사정). 가운데 중괄호 덩어리를 찾아 읽습니다.
 *
 * **길이는 여기서 한 번 더 잡습니다.** 짧게 달라는 말은 모델에 따라 지켜질 때도 아닐 때도
 * 있는데, 홈 카드는 스치듯 보는 자리라 길게 오면 카드가 읽을 거리가 됩니다
 * ([ConceptExplanation]과 같은 방식).
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object DailyCoachParser {
    /** 두 문장이면 이 정도입니다. */
    private const val MESSAGE_LIMIT = 160
    private const val QUOTE_LIMIT = 160
    private const val AUTHOR_LIMIT = 40

    /** 루틴 이름과 할 일 이름. 길면 목록에서 줄바꿈이 생깁니다. */
    private const val NAME_LIMIT = 40

    /** 내 말에 돌려준 답. 세 문장까지라 한마디보다 조금 깁니다. */
    private const val ANSWER_LIMIT = 240

    /**
     * 읽어 내지 못하면 null. 부르는 쪽이 사용자에게 사유를 말할 수 있어야 합니다.
     *
     * 한마디([DailyCoachTip.message])가 비어 있으면 받은 것으로 치지 않습니다. 글귀만 덜렁
     * 오면 "오늘 하루를 보고 건넨 말"이 아니라 그냥 글귀 추천입니다.
     */
    fun parse(rawText: String, model: String, now: Long = System.currentTimeMillis()): DailyCoachTip? {
        val json = extractObject(rawText) ?: return null
        val quote = json.optJSONObject("quote")
        // 글귀만 문자열로 보내오는 모델도 있습니다([GrowthReportParser]와 같습니다).
        // **덩어리로 왔으면 문자열 자리를 보지 않습니다** — `optString`은 덩어리를 글로 바꿔
        // 돌려주어서, 빈 글귀가 `{"author":"","text":""}`로 카드에 뜹니다.
        val quoteText = quote?.let { textOf(it, "text") } ?: textOf(json, "quote")

        val tip = DailyCoachTip(
            message = clip(json.optString("message"), MESSAGE_LIMIT),
            quote = clip(quoteText, QUOTE_LIMIT),
            quoteAuthor = clip(quote?.let { textOf(it, "author") }.orEmpty(), AUTHOR_LIMIT),
            routine = clip(json.optString("routine"), NAME_LIMIT),
            action = clip(json.optString("action"), NAME_LIMIT),
            model = model,
            createdAt = now
        )
        return tip.takeIf { it.message.isNotBlank() }
    }

    /**
     * 내 말에 코치가 돌려준 답. 읽어 내지 못하면 null.
     *
     * **형식 없이 글로만 와도 답으로 받습니다.** 한마디는 카드에 자리가 정해져 있어 형식이 어긋나면 버리지만, 대화의 답은
     * 말 자체가 전부라 JSON을 잊은 답도 그대로 읽힙니다. 다만 중괄호가 보이는데 읽지 못한 것은 잘린 JSON이라 받지 않습니다 —
     * 글자 그대로 띄우면 `{"message":"그럼` 같은 조각이 말풍선에 뜹니다.
     */
    fun parseAnswer(rawText: String, model: String, now: Long = System.currentTimeMillis()): DailyCoachLine? {
        val text = rawText.trim()
        val json = extractObject(text)
        val answer = when {
            json != null -> DailyCoachLine(
                kind = DailyCoachLine.Kind.ANSWER,
                text = clip(textOf(json, "message"), ANSWER_LIMIT),
                action = clip(textOf(json, "action"), NAME_LIMIT),
                model = model,
                at = now
            )
            '{' !in text -> DailyCoachLine(
                kind = DailyCoachLine.Kind.ANSWER,
                text = clip(text, ANSWER_LIMIT),
                model = model,
                at = now
            )
            else -> null
        }
        return answer?.takeIf { it.text.isNotBlank() }
    }

    /** 비워 두라는 자리에 `null`을 적어 보내는 모델도 있습니다. 글자 그대로 "null"이 할 일로 뜨지 않게 합니다. */
    private fun textOf(json: JSONObject, name: String): String = if (json.isNull(name)) "" else json.optString(name)

    /** 첫 `{`부터 마지막 `}`까지. 중괄호 짝을 세다 틀리면 통째로 못 읽습니다. */
    private fun extractObject(rawText: String): JSONObject? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private fun clip(value: String, limit: Int): String {
        val text = value.trim()
        return if (text.length <= limit) text else text.take(limit).trimEnd() + "…"
    }
}
