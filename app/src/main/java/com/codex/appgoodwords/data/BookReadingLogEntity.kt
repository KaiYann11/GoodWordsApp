package com.codex.appgoodwords.data

/**
 * 어느 날 어느 책을 어디까지 읽었는지.
 *
 * **책에는 마지막 쪽만 남습니다.** 오늘 100쪽을 적으면 어제의 60쪽은 덮여 사라집니다.
 * 그래서 책만 봐서는 "언제 읽었는지"를 되짚을 수 없고, 독서를 하루의 걸음([DailyStep.READING])으로
 * 삼으면 연속 날수와 점수를 기록에서 다시 셀 수 없습니다. 이 앱은 그 둘을 저장하지 않고
 * 매번 기록에서 셉니다. 그래서 날짜가 남는 자리를 따로 둡니다.
 *
 * **하루에 책 하나당 하나입니다.** 같은 날 같은 책을 두 번 적으면 그날 것을 고칩니다
 * (마지막 쪽만 올라갑니다). 여러 줄로 쌓으면 그날 몇 쪽을 읽었는지 셀 때마다 겹쳐 세게 됩니다.
 */
@androidx.room.Entity(
    tableName = "book_reading_logs",
    indices = [
        androidx.room.Index(value = ["syncId"], unique = true),
        androidx.room.Index(value = ["bookSyncId"]),
        androidx.room.Index(value = ["readDate"])
    ]
)
data class BookReadingLogEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = SyncIdentity.newId(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 기기 안에서만 쓰는 숫자 id. 저장 직전에 [SnapshotReindexer]가 다시 매깁니다. */
    val bookId: Long = 0,
    /** 기기 사이에서 책을 가리키는 값. 숫자 id로 가리키면 다른 기기에서 엉뚱한 책이 됩니다. */
    val bookSyncId: String = "",
    /**
     * 그때 그 책의 이름.
     *
     * 책을 지워도 읽은 날은 남습니다 — 읽은 것은 책과 별개로 이미 내가 한 일입니다.
     * 부모가 없어도 읽히도록 이름을 함께 둡니다(이력과 같은 예외입니다).
     */
    val bookTitle: String = "",
    /** ISO `yyyy-MM-dd`. */
    val readDate: String = "",
    /** 그날 읽기 시작한 쪽. 모르면 0입니다. */
    val startPage: Int = 0,
    /** 그날 마지막으로 닿은 쪽. */
    val endPage: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 그날 읽은 쪽수. 뒤로 돌려 적었으면 0입니다.
     *
     * 되돌려 적는 일이 있습니다(잘못 적었거나, 앞으로 돌아가 다시 읽거나). 음수를 그대로 두면
     * 합계가 줄어들어 "오늘 -12쪽"처럼 보입니다.
     */
    val pagesRead: Int
        get() = (endPage - startPage).coerceAtLeast(0)
}
