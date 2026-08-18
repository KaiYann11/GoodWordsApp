package com.codex.appgoodwords.data

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromContentType(value: ContentType): String = value.name

    @TypeConverter
    fun toContentType(value: String): ContentType = ContentType.valueOf(value)

    @TypeConverter
    fun fromExposureEventType(value: ExposureEventType): String = value.name

    @TypeConverter
    fun toExposureEventType(value: String): ExposureEventType = ExposureEventType.valueOf(value)

    @TypeConverter
    fun fromExposureTrigger(value: ExposureTrigger): String = value.name

    @TypeConverter
    fun toExposureTrigger(value: String): ExposureTrigger = ExposureTrigger.valueOf(value)

    @TypeConverter
    fun fromSyncEntityType(value: SyncEntityType): String = value.name

    @TypeConverter
    fun toSyncEntityType(value: String): SyncEntityType = SyncEntityType.valueOf(value)

    @TypeConverter
    fun fromTags(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun toTags(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val jsonArray = JSONArray(value)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                jsonArray.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }
}


/**
 * 일기 물음의 답만 쓰는 변환기.
 *
 * [Converters.toTags]는 빈 문자열을 버립니다. 태그나 첨부 주소는 빈 값이 쓸모없기 때문입니다.
 * 답은 다릅니다. 세 물음 중 마지막에만 답한 날 빈칸을 버리면 답이 앞으로 밀려서
 * 다른 물음의 답으로 보입니다. 그래서 자리를 그대로 지키는 변환기를 따로 두고,
 * `DiaryEntity.answers`에만 붙여 씁니다.
 */
class DiaryAnswerConverters {
    @TypeConverter
    fun fromAnswers(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun toAnswers(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val jsonArray = JSONArray(value)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.optString(index))
            }
        }
    }
}
