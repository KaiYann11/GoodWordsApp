package com.codex.appgoodwords.data

/** 화면에서 편집 중인 책. 저장 전까지는 DB에 없습니다. */
data class BookDraft(
    val id: Long = 0L,
    val title: String = "",
    val author: String = "",
    /** 빈 문자열이면 모르는 것으로 둡니다. 숫자로 들고 있으면 지우는 중에 0이 되어 진도가 튑니다. */
    val totalPagesText: String = "",
    val currentPageText: String = "",
    val status: String = BookStatus.READING.name,
    val note: String = ""
) {
    val totalPages: Int
        get() = totalPagesText.trim().toIntOrNull() ?: 0

    val currentPage: Int
        get() = currentPageText.trim().toIntOrNull() ?: 0

    val canSave: Boolean
        get() = title.isNotBlank()

    companion object {
        fun from(book: BookEntity): BookDraft = BookDraft(
            id = book.id,
            title = book.title,
            author = book.author,
            totalPagesText = if (book.totalPages > 0) book.totalPages.toString() else "",
            currentPageText = if (book.currentPage > 0) book.currentPage.toString() else "",
            status = book.status,
            note = book.note
        )
    }
}
