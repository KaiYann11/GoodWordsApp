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

