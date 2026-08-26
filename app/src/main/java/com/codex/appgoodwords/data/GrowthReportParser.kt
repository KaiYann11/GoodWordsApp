package com.codex.appgoodwords.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 모델이 돌려준 글을 레코드로 바꿉니다.
 *
 * JSON만 달라고 일러 두었지만 그대로 지켜 주지 않는 경우가 있습니다. 코드펜스로 감싸거나
 * 앞뒤에 인사말을 붙입니다. 그때마다 실패로 돌리면 사용자는 이유를 알 수 없는 오류만 봅니다.
 * 그래서 가운데 중괄호 덩어리를 찾아내 읽습니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object GrowthReportParser {
    /** 한 갈래에 담을 최대 줄 수. 모델이 열 줄을 보내와도 화면은 읽히는 만큼만 둡니다. */
    private const val MAX_LINES = 5

    /**
     * 읽어 내지 못하면 null. 부르는 쪽이 "형식이 어긋났다"고 사용자에게 말할 수 있어야 합니다.
     */
    fun parse(
        rawText: String,
        period: ReportPeriod,
        periodStart: String,
        periodEnd: String,
        model: String,
        now: Long = System.currentTimeMillis()
    ): GrowthReportEntity? {
        val json = extractObject(rawText) ?: return null
        val quote = json.optJSONObject("suggestedQuote")

        val report = GrowthReportEntity(
            updatedAt = now,
            period = period.name,
            periodStart = periodStart,
            periodEnd = periodEnd,
            model = model,
            strengths = json.optJSONArray("strengths").toLines(),
            improvements = json.optJSONArray("improvements").toLines(),
            // 글귀만 문자열로 보내오는 모델도 있습니다.
            suggestedQuote = quote?.optString("text").orEmpty().ifBlank { json.optString("suggestedQuote") }.trim(),
            suggestedQuoteAuthor = quote?.optString("author").orEmpty().trim(),
            suggestedRoutines = json.optJSONArray("suggestedRoutines").toLines(),
            guide = json.optString("guide").trim(),
            createdAt = now
        )
        return report.takeIf { it.hasContent }
    }

    /**
     * 글 안에서 JSON 덩어리를 떼어 냅니다.
     *
     * 첫 `{`부터 마지막 `}`까지를 봅니다. 중괄호 짝을 세지 않는 이유는, 본문 안에 중괄호가
     * 들어갈 일이 거의 없고 세다가 틀리면 통째로 못 읽기 때문입니다.
     */
    private fun extractObject(rawText: String): JSONObject? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    private fun JSONArray?.toLines(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val line = optString(index).trim()
                if (line.isNotBlank()) add(line)
            }
        }.take(MAX_LINES)
    }
}
