package com.codex.appgoodwords.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 이어진 기록 한 줄. 화면이 그리고 열 수 있게 무엇인지·제목·이 기기의 번호를 함께 둡니다. */
data class LinkedRecord(
    val ref: RecordRef,
    /** "행복한 순간"·"좋아하는 것"·"일기"처럼 무엇인지. */
    val label: String,
    val title: String,
    /** 이 기기의 번호. 그 기록을 열 때만 씁니다 — 기기마다 달라 저장하지 않습니다. */
    val id: Long,
    /** 보관함 기록이면 그 종류. 어느 화면으로 열지 정합니다. */
    val contentType: ContentType? = null,
    /** 이름이 나온 곳이면 그 둘레의 글. 잠긴 일기는 비웁니다. */
    val snippet: String = "",
    /** 늘어놓는 차례에 쓰는 때(그 기록의 날짜나 만든 때). */
    val time: Long = 0L
)

/** 한 기록의 이어진 기록(사용자가 정함, 2026-11-12 — 직접 이은 것 + 저절로 보이는 연결). */
data class Connections(
    /** 직접 이은 것. 지운 기록을 가리키는 줄은 화면에서만 뺍니다(줄은 그대로 둡니다). */
    val direct: List<LinkedRecord>,
    /**
     * 이름이 나온 곳 — 이 기록의 글에 다른 기록의 이름이 나왔거나, 다른 기록의 글에 이 기록의 이름이 나온 것.
     * 직접 이은 것은 빼서 같은 기록이 두 번 보이지 않게 합니다.
     */
    val mentions: List<LinkedRecord>
) {
    val isEmpty: Boolean
        get() = direct.isEmpty() && mentions.isEmpty()

    /** 이어진 행복한 순간(직접 이었거나 이름이 나온 것). 좋아하는 것이 몇 순간에 있었는지 셉니다. */
    val happyMoments: List<LinkedRecord>
        get() = (direct + mentions).filter { it.contentType == ContentType.HAPPY_MOMENT }.distinctBy { it.ref }
}

/**
 * 기록끼리의 연결을 셉니다. **저장하지 않습니다** — 기록이 바뀌면 다시 만듭니다(AGENTS "셈한 것은 저장하지 않고").
 *
 * 이름이 나온 곳은 **좋아하는 것·롤모델·책·루틴의 이름만** 봅니다. 목표·글귀의 제목은 문장이라 다른 글에 그대로 나올 일이
 * 드물고, 일기·할 일 제목처럼 짧은 말이 우연히 걸리면 연결이 아니라 소음이 됩니다. 견주는 규칙은 [Mentions]입니다.
 *
 * 잠긴 일기는 제목과 둘레의 글을 가립니다(그날 요약이 "잠긴 일기"로 가리는 것과 같게). 걷는 중인 걷기는 아직 기록이
 * 아니라 넣지 않습니다.
 */
class RecordIndex(
    items: List<ContentItemEntity> = emptyList(),
    diaries: List<DiaryEntity> = emptyList(),
    books: List<BookEntity> = emptyList(),
    routines: List<RoutineEntity> = emptyList(),
    todos: List<TodoEntity> = emptyList(),
    walks: List<WalkEntity> = emptyList(),
    private val diaryLocked: Boolean = false,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val records = LinkedHashMap<RecordRef, LinkedRecord>()

    /** 이름이 나왔는지 훑는 글. 기록마다 하나로 이어 둡니다. */
    private val texts = LinkedHashMap<RecordRef, String>()

    /** 다른 글에 나오면 이어져 보이는 이름. */
    private val names = LinkedHashMap<RecordRef, String>()

    /** 이 기기의 번호로 찾기(종류 이름, 번호). 검색에서 고른 것을 기록으로 바꿀 때 씁니다. */
    private val byLocalId = HashMap<Pair<String, Long>, RecordRef>()

    init {
        items.forEach { item ->
            val ref = RecordRef.of(item)
            records[ref] = LinkedRecord(
                ref = ref,
                label = item.type.label,
                title = item.title.ifBlank { item.body.trim().take(TITLE_LENGTH) },
                id = item.id,
                contentType = item.type,
                time = item.createdAt
            )
            // 행복한 순간은 이름이 글의 첫 줄이라 글만 둡니다(이어 붙이면 조각에 같은 줄이 두 번 보입니다).
            texts[ref] = if (item.type == ContentType.HAPPY_MOMENT) item.body else listOf(item.title, item.body).joinToString("\n")
            if (item.type == ContentType.LIKE || item.type == ContentType.ROLE_MODEL) names[ref] = item.title
        }
        diaries.forEach { diary ->
            val ref = RecordRef.of(diary)
            val date = runCatching { LocalDate.parse(diary.entryDate) }.getOrNull()
            records[ref] = LinkedRecord(
                ref = ref,
                label = "일기",
                title = if (diaryLocked) "잠긴 일기" else diary.displayTitle.ifBlank { diary.entryDate },
                id = diary.id,
                time = date?.atStartOfDay(zoneId)?.toInstant()?.toEpochMilli() ?: diary.createdAt
            )
            texts[ref] = (listOf(diary.title, diary.body) + diary.answers).joinToString("\n")
        }
        books.forEach { book ->
            val ref = RecordRef.of(book)
            records[ref] = LinkedRecord(ref = ref, label = "책", title = book.title, id = book.id, time = book.createdAt)
            texts[ref] = listOf(book.title, book.note, book.review).joinToString("\n")
            names[ref] = book.title
        }
        routines.forEach { routine ->
            val ref = RecordRef.of(routine)
            records[ref] = LinkedRecord(ref = ref, label = "루틴", title = routine.title, id = routine.id, time = routine.createdAt)
            texts[ref] = listOf(routine.title, routine.note).joinToString("\n")
            names[ref] = routine.title
        }
        todos.forEach { todo ->
            val ref = RecordRef.of(todo)
            records[ref] = LinkedRecord(ref = ref, label = "할 일", title = todo.title, id = todo.id, time = todo.createdAt)
            texts[ref] = listOf(todo.title, todo.note).joinToString("\n")
        }
        walks.filter { it.isFinished }.forEach { walk ->
            val ref = RecordRef.of(walk)
            val day = Instant.ofEpochMilli(walk.startedAt).atZone(zoneId).format(WALK_DAY)
            records[ref] = LinkedRecord(
                ref = ref,
                label = "걷기",
                title = "$day · ${Walking.formatDistance(walk.distanceMeters)}",
                id = walk.id,
                time = walk.startedAt
            )
        }
    }

    init {
        records.values.forEach { record -> byLocalId[record.ref.kind to record.id] = record.ref }
    }

    /** 이 기기에 있는 기록이면 그 줄, 지웠거나 아직 오지 않았으면 null. */
    fun resolve(ref: RecordRef): LinkedRecord? = records[ref]

    /** 검색에서 고른 것이 가리키는 기록. 돌아보기처럼 잇지 않는 것이나 모르는 번호는 null입니다. */
    fun refOf(hit: SearchHit): RecordRef? {
        val kind = when (hit.kind) {
            SearchKind.DIARY -> RecordKind.DIARY
            SearchKind.TODO -> RecordKind.TODO
            SearchKind.BOOK -> RecordKind.BOOK
            SearchKind.ROUTINE -> RecordKind.ROUTINE
            SearchKind.GROWTH -> return null
            else -> RecordKind.CONTENT_ITEM
        }
        return byLocalId[kind.name to hit.id]
    }

    /** [ref]의 이어진 기록. 직접 이은 것은 최근에 이은 차례, 이름이 나온 곳은 최근 기록부터입니다. */
    fun connectionsOf(ref: RecordRef, links: List<RecordLinkEntity>): Connections {
        val direct = RecordLinks.linkedTo(links, ref).mapNotNull(records::get)
        val skip = direct.mapTo(HashSet()) { it.ref }.apply { add(ref) }
        val mentions = LinkedHashMap<RecordRef, LinkedRecord>()
        // 이 기록의 글에 나온 다른 기록의 이름.
        texts[ref]?.let { text ->
            names.forEach { (other, name) ->
                if (other !in skip && Mentions.contains(text, name)) {
                    records[other]?.let { mentions[other] = it.copy(snippet = snippetOf(ref, text, name)) }
                }
            }
        }
        // 다른 기록의 글에 나온 이 기록의 이름.
        names[ref]?.let { name ->
            texts.forEach { (other, text) ->
                if (other !in skip && other !in mentions && Mentions.contains(text, name)) {
                    records[other]?.let { mentions[other] = it.copy(snippet = snippetOf(other, text, name)) }
                }
            }
        }
        return Connections(direct = direct, mentions = mentions.values.sortedByDescending { it.time })
    }

    private fun snippetOf(owner: RecordRef, text: String, name: String): String =
        if (diaryLocked && owner.kind == RecordKind.DIARY.name) "" else Mentions.snippet(text, name)

    private companion object {
        const val TITLE_LENGTH = 30
        val WALK_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 걷기", Locale.KOREAN)
    }
}
