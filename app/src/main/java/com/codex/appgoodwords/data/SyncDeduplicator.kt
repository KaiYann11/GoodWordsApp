package com.codex.appgoodwords.data

/**
 * 같은 내용인데 `syncId`만 다른 레코드를 하나로 합칩니다.
 *
 * 앱과 서버는 각자 기본 글귀를 심습니다. 그 둘은 내용이 같아도 `syncId`가 달라서,
 * 처음 서버를 붙이면 같은 글귀가 두 벌이 됩니다. 기기를 새로 깔고 붙여도 마찬가지입니다.
 *
 * 그래서 병합이 끝난 뒤 내용이 같은 것끼리 묶어 하나만 남기고, 사라진 쪽을 가리키던
 * 이력·체크·메모는 남은 쪽으로 옮겨 붙입니다. 옮기지 않으면 이력이 부모를 잃습니다.
 *
 * **이력과 체크는 합치지 않습니다.** 같은 글귀를 두 번 본 것은 진짜로 두 번 본 것입니다.
 *
 * 서버의 `deduplicate()`와 규칙이 같아야 합니다. 한쪽만 바꾸면 병합할 때마다 결과가 달라집니다.
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object SyncDeduplicator {
    fun deduplicate(snapshot: AppDataSnapshot): AppDataSnapshot {
        val items = resolve(snapshot.items, { it.syncId }, { it.updatedAt }, ::itemFingerprint)
        val routines = resolve(snapshot.routines, { it.syncId }, { it.updatedAt }, ::routineFingerprint)
        val diaries = resolve(snapshot.diaries, { it.syncId }, { it.updatedAt }, ::diaryFingerprint)
        val todos = resolve(snapshot.todos, { it.syncId }, { it.updatedAt }, ::todoFingerprint)
        val books = resolve(snapshot.books, { it.syncId }, { it.updatedAt }, ::bookFingerprint)

        return snapshot.copy(
            items = items.kept.map { item ->
                // 사라진 책을 가리키던 글귀는 남은 책으로 옮겨 붙입니다. 안 옮기면 출처를 잃습니다.
                if (item.bookSyncId.isBlank()) item
                else item.copy(bookSyncId = books.survivorOf(item.bookSyncId))
            },
            routines = routines.kept,
            diaries = diaries.kept,
            todos = todos.kept,
            books = books.kept,
            events = snapshot.events.map { event ->
                event.copy(contentItemSyncId = items.survivorOf(event.contentItemSyncId))
            },
            routineChecks = snapshot.routineChecks.map { check ->
                check.copy(routineSyncId = routines.survivorOf(check.routineSyncId))
            },
            routineMemos = snapshot.routineMemos.map { memo ->
                memo.copy(routineSyncId = routines.survivorOf(memo.routineSyncId))
            }
        )
    }

    /**
     * 살아남은 레코드와, 사라진 `syncId`가 어디로 갔는지를 함께 들고 있습니다.
     * 자식이 부모를 다시 찾으려면 이 표가 필요합니다.
     */
    private class Resolution<T>(
        val kept: List<T>,
        private val movedTo: Map<String, String>
    ) {
        fun survivorOf(syncId: String): String = movedTo[syncId] ?: syncId
    }

    /**
     * 내용이 같은 것끼리 묶어 최근에 손댄 쪽을 남깁니다.
     * 같은 시각이면 `syncId` 순으로 정해, 어느 기기에서 돌려도 결과가 같게 합니다.
     */
    private fun <T> resolve(
        records: List<T>,
        syncId: (T) -> String,
        updatedAt: (T) -> Long,
        fingerprint: (T) -> String
    ): Resolution<T> {
        // 내용이 비어 있으면 무엇과도 같아 보이므로 묶지 않는다.
        val winnerByFingerprint = records
            .filter { fingerprint(it).isNotBlank() }
            .groupBy(fingerprint)
            .mapValues { (_, group) -> group.maxWith(compareBy({ updatedAt(it) }, { syncId(it) })) }

        val movedTo = mutableMapOf<String, String>()
        val kept = records.filter { record ->
            val winner = winnerByFingerprint[fingerprint(record)] ?: return@filter true
            if (syncId(record) == syncId(winner)) return@filter true
            movedTo[syncId(record)] = syncId(winner)
            false
        }

        return Resolution(kept = kept, movedTo = movedTo)
    }

    // 글만 같고 첨부가 다르면 다른 기록이다. 첨부까지 넣지 않으면
    // 글 없이 사진만 올린 두 기록이 하나로 합쳐지면서 한쪽 사진이 사라진다.
    private fun itemFingerprint(item: ContentItemEntity): String = listOf(
        item.type.name,
        normalize(item.title),
        normalize(item.body),
        normalize(item.author),
        normalize(item.sourceUrl),
        item.imageUris.joinToString(","),
        item.videoUris.joinToString(",")
    ).joinToString("|")

    private fun routineFingerprint(routine: RoutineEntity): String = normalize(routine.title)

    // 날씨와 기분도 함께 봅니다. 글 없이 기분만 남긴 날이 있을 수 있어서,
    // 빼면 같은 날 남긴 두 기분 중 하나가 조용히 사라집니다.
    private fun diaryFingerprint(diary: DiaryEntity): String = listOf(
        diary.entryDate.trim(),
        normalize(diary.title),
        normalize(diary.body),
        diary.weather.trim(),
        diary.mood.trim(),
        diary.imageUris.joinToString(","),
        diary.videoUris.joinToString(","),
        diary.audioUris.joinToString(",")
    ).joinToString("|")

    /**
     * 같은 책은 제목과 저자로 봅니다.
     *
     * 읽은 쪽수는 넣지 않습니다. 두 기기에서 같은 책을 각자 담으면 진도가 다른 것이 당연한데,
     * 쪽수까지 보면 서로 다른 책이 되어 목록에 같은 책이 두 벌 남습니다.
     * 진도는 최신 `updatedAt`이 이깁니다.
     */
    private fun bookFingerprint(book: BookEntity): String = listOf(
        normalize(book.title),
        normalize(book.author)
    ).joinToString("|")

    private fun todoFingerprint(todo: TodoEntity): String = listOf(
        todo.dueDate.trim(),
        normalize(todo.title),
        normalize(todo.note)
    ).joinToString("|")

    /** 띄어쓰기와 대소문자만 다른 것도 같은 내용으로 봅니다. */
    private fun normalize(value: String): String = value.trim().lowercase().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
