package com.codex.appgoodwords.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 모델 목록을 읽고 적는 꼴.
 *
 * 받는 곳이 셋입니다 — 내 서버(`GET /api/ai-models`), Claude API(`GET /v1/models`), OpenAI API(`GET /v1/models`). 어디서 받든
 * [AiModelInfo] 목록으로 바꿔 같은 자리에 둡니다(`SettingsStore`에는 [encode]한 글로).
 */
object AiModelListJson {
    /** 내 서버의 답: `{ provider, models: [{ id, label, createdAt }], fetchedAt }`. 차례는 서버가 준 대로입니다. */
    fun parseServer(body: String): List<AiModelInfo> = readModels(JSONObject(body).optJSONArray("models"))

    /**
     * Claude API 한 쪽: `{ data: [{ id, display_name, created_at }], has_more, last_id }`.
     *
     * @return 이 쪽의 모델과, 다음 쪽이 있으면 그 쪽을 받을 `after_id`
     */
    fun parseAnthropicPage(body: String): Pair<List<AiModelInfo>, String?> {
        val json = JSONObject(body)
        val data = json.optJSONArray("data") ?: JSONArray()
        val models = buildList {
            for (index in 0 until data.length()) {
                val model = data.optJSONObject(index) ?: continue
                val id = model.optString("id").trim()
                if (id.isEmpty()) continue
                add(
                    AiModelInfo(
                        id = id,
                        label = model.optString("display_name").trim().ifEmpty { id },
                        createdAt = parseInstant(model.optString("created_at"))
                    )
                )
            }
        }
        val next = json.optString("last_id").trim().takeIf { json.optBoolean("has_more") && it.isNotEmpty() }
        return models to next
    }

    /** OpenAI의 목록: `{ data: [{ id, created }] }`. 글 모델만 남겨 새로 나온 것부터 놓습니다(서버 `listOpenAiModels`와 같게). */
    fun parseOpenAi(body: String): List<AiModelInfo> {
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val model = data.optJSONObject(index) ?: continue
                val id = model.optString("id").trim()
                if (id.isEmpty() || !AiModels.isOpenAiChatModel(id)) continue
                add(AiModelInfo(id = id, label = id, createdAt = model.optLong("created") * 1000L))
            }
        }.sortedByDescending { it.createdAt }
    }

    /** 이 기기에 적어 둘 꼴. */
    fun encode(list: AiModelList): String = JSONObject()
        .put("fetchedAt", list.fetchedAt)
        .put(
            "models",
            JSONArray().apply {
                list.models.forEach { model ->
                    put(JSONObject().put("id", model.id).put("label", model.label).put("createdAt", model.createdAt))
                }
            }
        )
        .toString()

    /** 적어 둔 것을 읽습니다. 비었거나 꼴이 어긋나면 받은 적이 없는 것으로 봅니다(새로 받으면 됩니다). */
    fun decode(text: String?): AiModelList {
        if (text.isNullOrBlank()) return AiModelList()
        return runCatching {
            val json = JSONObject(text)
            AiModelList(models = readModels(json.optJSONArray("models")), fetchedAt = json.optLong("fetchedAt"))
        }.getOrDefault(AiModelList())
    }

    /**
     * 서버가 알아 온 이름(`POST /api/ai-models/resolve`): `{ provider, models: [{ requested, model, error }], resolvedAt }`.
     *
     * @return 보낸 이름(`""`·`sonnet`) → 알아 온 것
     */
    fun parseResolved(body: String): Map<String, AiResolvedModel> {
        val array = JSONObject(body).optJSONArray("models") ?: JSONArray()
        return buildMap {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                put(
                    row.optString("requested").trim(),
                    AiResolvedModel(model = row.optString("model").trim(), error = row.optString("error").trim())
                )
            }
        }
    }

    /** 칸마다 알아 둔 이름([AiModelCheck])을 이 기기에 적어 둘 꼴. 빈 값(CLI 기본)도 칸 이름으로 씁니다. */
    fun encodeCheck(check: AiModelCheck): String = JSONObject()
        .put("checkedAt", check.checkedAt)
        .put("names", JSONObject().apply { check.names.forEach { (choice, model) -> put(choice, model) } })
        .put("errors", JSONObject().apply { check.errors.forEach { (choice, error) -> put(choice, error) } })
        .toString()

    /** 적어 둔 것을 읽습니다. 비었거나 꼴이 어긋나면 알아 둔 것이 없는 것으로 봅니다(다시 받으면 됩니다). */
    fun decodeCheck(text: String?): AiModelCheck {
        if (text.isNullOrBlank()) return AiModelCheck()
        return runCatching {
            val json = JSONObject(text)
            AiModelCheck(
                checkedAt = json.optLong("checkedAt"),
                names = readStrings(json.optJSONObject("names")),
                errors = readStrings(json.optJSONObject("errors"))
            )
        }.getOrDefault(AiModelCheck())
    }

    private fun readStrings(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        return buildMap {
            json.keys().forEach { key -> json.optString(key).trim().takeIf { it.isNotEmpty() }?.let { put(key, it) } }
        }
    }

    private fun readModels(array: JSONArray?): List<AiModelInfo> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val model = array.optJSONObject(index) ?: continue
                val id = model.optString("id").trim()
                if (id.isEmpty()) continue
                add(
                    AiModelInfo(
                        id = id,
                        label = model.optString("label").trim().ifEmpty { id },
                        createdAt = model.optLong("createdAt")
                    )
                )
            }
        }
    }

    /** RFC 3339 때(`2026-11-01T00:00:00Z`, `+09:00`이 붙기도). 읽지 못하면 0 — 차례는 이름으로 셉니다([AiModels.newestIn]). */
    private fun parseInstant(text: String): Long =
        runCatching { java.time.OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli() }.getOrDefault(0L)
}
