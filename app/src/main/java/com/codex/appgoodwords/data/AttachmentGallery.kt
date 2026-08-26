package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class AttachmentKind(val label: String) {
    IMAGE("사진"),
    VIDEO("동영상"),
    AUDIO("소리")
}

/** 어딘가에 붙여 둔 첨부 한 장. */
data class AttachmentShot(
    val uri: String,
    val kind: AttachmentKind,
    /** 어디에 붙어 있는지. 눌렀을 때 그 기록으로 데려가려면 필요합니다. */
    val source: SearchKind,
    val sourceId: Long,
    val date: LocalDate,
    val label: String
)

/**
 * 여기저기 붙여 둔 첨부를 한자리에 모읍니다.
 *
 * 일기에 사진·동영상·소리를 붙일 수 있고 글귀에도 사진을 붙이는데, 모아 보는 곳이 없었습니다.
 * 지난 사진 한 장을 찾으려면 그날 일기를 기억해 내서 찾아 들어가야 했습니다.
 *
 * **파일을 옮기거나 베끼지 않습니다.** 주소만 모아 늘어놓습니다. 첨부 자체는 기기나 서버에
 * 그대로 있고, 여기서는 어디에 무엇이 붙어 있는지만 봅니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object AttachmentGallery {
    fun collect(
        diaries: List<DiaryEntity>,
        items: List<ContentItemEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<AttachmentShot> {
        val fromDiaries = diaries.flatMap { diary ->
            val date = parseDate(diary.entryDate) ?: toDate(diary.createdAt, zoneId)
            val label = diary.title.ifBlank { diary.entryDate.ifBlank { "일기" } }
            shotsOf(
                source = SearchKind.DIARY,
                sourceId = diary.id,
                date = date,
                label = label,
                images = diary.imageUris,
                videos = diary.videoUris,
                audios = diary.audioUris
            )
        }

        val fromItems = items.flatMap { item ->
            shotsOf(
                source = SearchKind.QUOTE,
                sourceId = item.id,
                date = toDate(item.createdAt, zoneId),
                label = item.title.ifBlank { "글귀" },
                images = item.imageUris,
                videos = item.videoUris,
                audios = emptyList()
            )
        }

        // 새것이 위입니다. 같은 날이면 붙인 차례를 그대로 둡니다.
        return (fromDiaries + fromItems)
            .distinctBy { it.uri }
            .sortedByDescending { it.date }
    }

    /** 달별로 묶습니다. 새 달이 위입니다. */
    fun byMonth(shots: List<AttachmentShot>): Map<YearMonth, List<AttachmentShot>> =
        shots.groupBy { YearMonth.from(it.date) }
            .toSortedMap(compareByDescending { it })

    private fun shotsOf(
        source: SearchKind,
        sourceId: Long,
        date: LocalDate,
        label: String,
        images: List<String>,
        videos: List<String>,
        audios: List<String>
    ): List<AttachmentShot> {
        fun of(uris: List<String>, kind: AttachmentKind) = uris
            .filter { it.isNotBlank() }
            .map { AttachmentShot(it, kind, source, sourceId, date, label) }

        return of(images, AttachmentKind.IMAGE) +
            of(videos, AttachmentKind.VIDEO) +
            of(audios, AttachmentKind.AUDIO)
    }

    private fun parseDate(value: String): LocalDate? =
        value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun toDate(millis: Long, zoneId: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
}
