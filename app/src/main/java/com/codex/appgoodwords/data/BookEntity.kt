package com.codex.appgoodwords.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 읽고 있거나 다 읽은 책.
 *
 * 상태는 [BookStatus]의 이름을 담은 문자열입니다. 열거형을 그대로 저장하지 않는 이유는
 * 다음 버전이 상태를 늘렸을 때 옛 앱이 그 값을 받아도 죽지 않게 하려는 것입니다.
 * 날씨·기분과 같은 방식입니다.
 */
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["syncId"], unique = true),
        Index(value = ["status"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    val title: String,
    val author: String = "",
    /** 전체 쪽수. 0이면 모르는 것으로 봅니다. 진도율을 계산하지 않습니다. */
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val status: String = BookStatus.READING.name,
    val note: String = "",
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val statusOption: BookStatus?
        get() = BookStatus.fromCode(status)

    val isFinished: Boolean
        get() = statusOption == BookStatus.FINISHED

    /**
     * 읽은 비율(0~1). 전체 쪽수를 모르면 null입니다.
     *
     * 모를 때 0을 돌려주면 화면이 "0% 읽음"이라고 단정합니다. 모르는 것과 안 읽은 것은 다릅니다.
     */
    val progress: Float?
        get() {
            if (totalPages <= 0) return null
            return (currentPage.toFloat() / totalPages).coerceIn(0f, 1f)
        }

    val displayAuthor: String
        get() = author.ifBlank { "저자 미상" }
}

/** 책의 상태. 읽고 싶은 책은 아직 다루지 않습니다. */
enum class BookStatus(val label: String) {
    READING("읽고 있는 책"),
    FINISHED("읽은 책");

    companion object {
        fun fromCode(code: String?): BookStatus? {
            val trimmed = code?.trim().orEmpty()
            if (trimmed.isBlank()) return null
            return entries.firstOrNull { it.name == trimmed }
        }
    }
}
