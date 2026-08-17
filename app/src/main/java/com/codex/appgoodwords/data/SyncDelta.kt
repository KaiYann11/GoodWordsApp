package com.codex.appgoodwords.data

/**
 * 마지막으로 보낸 뒤에 이 기기에서 바뀐 것만 골라냅니다.
 *
 * 전체를 매번 실어 보내면 이력이 수천 건 쌓였을 때 한 번에 1MB 가까이 오갑니다.
 * 그중 새로 생긴 것은 몇 건뿐입니다.
 *
 * 기준 시각은 **이 기기의 시계로만** 잽니다. 다른 기기의 시각과 비교하지 않으므로,
 * 기기 사이 시차가 있어도 보낼 것을 빠뜨리지 않습니다.
 * 다른 기기에서 받아 온 레코드가 한 번 더 올라갈 수는 있는데, 같은 내용이라 결과가 달라지지 않습니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object SyncDelta {
    fun changedSince(snapshot: AppDataSnapshot, since: Long): AppDataSnapshot {
        if (since <= 0L) return snapshot

        return snapshot.copy(
            items = snapshot.items.filter { it.updatedAt > since },
            routines = snapshot.routines.filter { it.updatedAt > since },
            routineMemos = snapshot.routineMemos.filter { it.updatedAt > since },
            diaries = snapshot.diaries.filter { it.updatedAt > since },
            todos = snapshot.todos.filter { it.updatedAt > since },
            books = snapshot.books.filter { it.updatedAt > since },
            // 이력과 체크는 한 번 생기면 바뀌지 않으므로 생긴 시각으로 봅니다.
            events = snapshot.events.filter { it.occurredAt > since },
            routineChecks = snapshot.routineChecks.filter { it.checkedAt > since },
            deletions = snapshot.deletions.filter { it.deletedAt > since },
            // 설정은 레코드가 아니라 한 덩어리입니다. 안 바뀌었으면 빈 시각으로 보내
            // 서버가 자기 것을 지키게 합니다.
            settingsUpdatedAt = if (snapshot.settingsUpdatedAt > since) snapshot.settingsUpdatedAt else 0L
        )
    }

    /** 이번에 보낸 것이 무엇이든, 다음 기준은 지금입니다. */
    fun nextPushMark(now: Long): Long = now
}
