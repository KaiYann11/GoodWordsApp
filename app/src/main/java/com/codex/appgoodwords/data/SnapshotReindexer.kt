package com.codex.appgoodwords.data

/**
 * 스냅샷의 숫자 id를 이 기기 안에서 다시 매깁니다.
 *
 * 숫자 id는 기기마다 따로 증가하는 값이라 기기 간 식별자가 될 수 없습니다.
 * 병합 결과에는 A기기 id=1과 B기기 id=1이 함께 들어오는데, 그대로 저장하면
 * 뒤에 넣은 쪽이 앞의 것을 덮어써서 항목이 사라집니다.
 *
 * 그래서 저장 직전에 id를 1부터 다시 부여하고, 자식 레코드의 참조는 syncId로 다시 잇습니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object SnapshotReindexer {
    fun reindex(snapshot: AppDataSnapshot): AppDataSnapshot {
        // 9 이전 레코드는 부모를 숫자 id로만 가리킨다. 번호를 바꾸기 전에 syncId로 옮겨 둔다.
        val itemSyncIdByOldId = snapshot.items.oldIdToSyncId({ it.id }, { it.syncId })
        val routineSyncIdByOldId = snapshot.routines.oldIdToSyncId({ it.id }, { it.syncId })

        val items = snapshot.items.mapIndexed { index, item -> item.copy(id = index + 1L) }
        val routines = snapshot.routines.mapIndexed { index, routine -> routine.copy(id = index + 1L) }
        val itemIdBySyncId = items.associate { it.syncId to it.id }
        val routineIdBySyncId = routines.associate { it.syncId to it.id }

        return snapshot.copy(
            items = items,
            routines = routines,
            // 이력은 항목이 지워진 뒤에도 남으므로, 부모를 못 찾아도 버리지 않고 0으로 끊는다.
            events = snapshot.events.mapIndexed { index, event ->
                val parentSyncId = event.contentItemSyncId
                    .ifBlank { itemSyncIdByOldId[event.contentItemId].orEmpty() }
                event.copy(
                    id = index + 1L,
                    contentItemSyncId = parentSyncId,
                    contentItemId = itemIdBySyncId[parentSyncId] ?: 0L
                )
            },
            routineChecks = snapshot.routineChecks.mapIndexed { index, check ->
                val parentSyncId = check.routineSyncId
                    .ifBlank { routineSyncIdByOldId[check.routineId].orEmpty() }
                check.copy(
                    id = index + 1L,
                    routineSyncId = parentSyncId,
                    routineId = routineIdBySyncId[parentSyncId] ?: 0L
                )
            },
            // 메모는 루틴 화면 안에서만 보이므로, 붙을 루틴이 없으면 남겨도 볼 방법이 없다.
            routineMemos = snapshot.routineMemos
                .mapNotNull { memo ->
                    val parentSyncId = memo.routineSyncId
                        .ifBlank { routineSyncIdByOldId[memo.routineId].orEmpty() }
                    val routineId = routineIdBySyncId[parentSyncId] ?: return@mapNotNull null
                    memo.copy(routineSyncId = parentSyncId, routineId = routineId)
                }
                .mapIndexed { index, memo -> memo.copy(id = index + 1L) },
            // 일기·할 일·책은 딸린 자식이 없어 번호만 다시 매기면 된다.
            // 글귀가 책을 가리키지만 숫자 id가 아니라 bookSyncId로 가리켜서 번호가 바뀌어도 그대로다.
            diaries = snapshot.diaries.mapIndexed { index, diary -> diary.copy(id = index + 1L) },
            todos = snapshot.todos.mapIndexed { index, todo -> todo.copy(id = index + 1L) },
            books = snapshot.books.mapIndexed { index, book -> book.copy(id = index + 1L) }
        )
    }

    /** 같은 숫자 id가 여러 번 나오면 어느 쪽인지 알 수 없으므로 아예 잇지 않는다. */
    private fun <T> List<T>.oldIdToSyncId(
        oldId: (T) -> Long,
        syncId: (T) -> String
    ): Map<Long, String> {
        val ambiguous = groupingBy(oldId).eachCount().filterValues { it > 1 }.keys
        return filterNot { record -> oldId(record) in ambiguous }
            .associate { record -> oldId(record) to syncId(record) }
    }
}
