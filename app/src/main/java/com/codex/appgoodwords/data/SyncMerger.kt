package com.codex.appgoodwords.data

/**
 * 두 스냅샷을 레코드 단위로 합칩니다.
 *
 * 기존 동기화는 한쪽을 통째로 교체해서 두 기기에서 각각 편집하면 한쪽 작업이 사라졌습니다.
 * 여기서는 syncId로 같은 레코드를 짝지어 updatedAt이 최신인 쪽을 남기고,
 * 삭제 표식이 그 레코드의 updatedAt보다 나중이면 삭제를 유지합니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object SyncMerger {
    fun merge(local: AppDataSnapshot, remote: AppDataSnapshot): AppDataSnapshot {
        val deletions = mergeDeletions(local.deletions, remote.deletions)
        val deletedAtBySyncId = deletions.associate { it.syncId to it.deletedAt }

        return AppDataSnapshot(
            items = mergeMutable(
                local = local.items,
                remote = remote.items,
                deletedAtBySyncId = deletedAtBySyncId,
                syncId = { it.syncId },
                updatedAt = { it.updatedAt }
            ),
            events = mergeAppendOnly(
                local = local.events,
                remote = remote.events,
                deletedAtBySyncId = deletedAtBySyncId,
                syncId = { it.syncId }
            ),
            routines = mergeMutable(
                local = local.routines,
                remote = remote.routines,
                deletedAtBySyncId = deletedAtBySyncId,
                syncId = { it.syncId },
                updatedAt = { it.updatedAt }
            ),
            routineChecks = mergeAppendOnly(
                local = local.routineChecks,
                remote = remote.routineChecks,
                deletedAtBySyncId = deletedAtBySyncId,
                syncId = { it.syncId }
            ),
            routineMemos = mergeMutable(
                local = local.routineMemos,
                remote = remote.routineMemos,
                deletedAtBySyncId = deletedAtBySyncId,
                syncId = { it.syncId },
                updatedAt = { it.updatedAt }
            ),
            // 설정은 레코드가 아니라 화면 전체가 하나라 최근에 손댄 쪽을 통째로 쓴다.
            settings = if (remote.settingsUpdatedAt > local.settingsUpdatedAt) remote.settings else local.settings,
            settingsUpdatedAt = maxOf(local.settingsUpdatedAt, remote.settingsUpdatedAt),
            deletions = deletions
        )
    }

    /**
     * 수정 가능한 레코드: syncId로 짝지어 updatedAt이 최신인 쪽을 남긴다.
     * 같은 시각이면 로컬을 남겨 결과가 흔들리지 않게 한다.
     */
    private fun <T> mergeMutable(
        local: List<T>,
        remote: List<T>,
        deletedAtBySyncId: Map<String, Long>,
        syncId: (T) -> String,
        updatedAt: (T) -> Long
    ): List<T> {
        val merged = LinkedHashMap<String, T>()
        local.forEach { record -> merged[syncId(record)] = record }
        remote.forEach { record ->
            val key = syncId(record)
            val existing = merged[key]
            if (existing == null || updatedAt(record) > updatedAt(existing)) {
                merged[key] = record
            }
        }

        return merged.values.filterNot { record ->
            val deletedAt = deletedAtBySyncId[syncId(record)] ?: return@filterNot false
            // 지운 뒤에 다시 고쳤다면 그 수정이 이긴다.
            deletedAt >= updatedAt(record)
        }
    }

    /** 기록형 레코드: 바뀌지 않으므로 합집합을 만들고 삭제 표식만 걷어낸다. */
    private fun <T> mergeAppendOnly(
        local: List<T>,
        remote: List<T>,
        deletedAtBySyncId: Map<String, Long>,
        syncId: (T) -> String
    ): List<T> {
        val merged = LinkedHashMap<String, T>()
        local.forEach { record -> merged[syncId(record)] = record }
        remote.forEach { record -> merged.putIfAbsent(syncId(record), record) }

        return merged.values.filterNot { record -> deletedAtBySyncId.containsKey(syncId(record)) }
    }

    private fun mergeDeletions(
        local: List<DeletionEntity>,
        remote: List<DeletionEntity>
    ): List<DeletionEntity> {
        val merged = LinkedHashMap<String, DeletionEntity>()
        (local + remote).forEach { deletion ->
            val existing = merged[deletion.syncId]
            if (existing == null || deletion.deletedAt > existing.deletedAt) {
                merged[deletion.syncId] = deletion
            }
        }
        return merged.values.toList()
    }
}
