package com.codex.appgoodwords.data

/** 검색 결과가 어느 기능에서 나왔는지. */
enum class SearchKind(val label: String) {
    QUOTE("글귀"),
    MEMO("메모"),
    CONCEPT("개념"),
    INBOX("임시저장소"),
    DIARY("일기"),
    TODO("할 일"),
    BOOK("독서"),
    ROUTINE("루틴"),
    NOT_TO_DO("하지 않을 일"),
    PRINCIPLE("원칙"),
    GROWTH("돌아보기")
}

data class SearchHit(
    val kind: SearchKind,
    val id: Long,
    val title: String,
    /** 찾은 말 주변을 잘라 낸 조각. 어디가 걸렸는지 보이게 하려는 것입니다. */
    val snippet: String,
    /** 날짜나 저자처럼 어느 것인지 가려 주는 부가 정보. */
    val meta: String
)

data class SearchResults(
    val query: String,
    val hits: List<SearchHit>
) {
    val isEmpty: Boolean
        get() = hits.isEmpty()

    /** 종류별로 묶습니다. 화면은 이 순서대로 그립니다. */
    val byKind: List<Pair<SearchKind, List<SearchHit>>>
        get() = SearchKind.entries
            .map { kind -> kind to hits.filter { it.kind == kind } }
            .filter { (_, group) -> group.isNotEmpty() }
}

/**
 * 앱에 쌓인 모든 기록을 한 번에 찾습니다.
 *
 * 예전에는 보관함(글귀)에만 검색이 있어서, 일기가 쌓이면 "작년 여름에 뭐라고 썼더라"를
 * 찾을 방법이 없었습니다.
 *
 * 띄어쓴 말은 **모두** 들어 있어야 걸립니다. "여름 일기"로 찾으면 둘 다 든 것만 나옵니다.
 * 통째로 이어 붙여 비교하면 말이 하나만 겹쳐도 걸려서 결과가 금방 쓸모없어집니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object AppSearch {
    /** 한 종류에서 너무 많이 나오면 다른 종류가 화면 밖으로 밀립니다. */
    const val LIMIT_PER_KIND = 20

    private const val SNIPPET_RADIUS = 40

    fun search(
        query: String,
        items: List<ContentItemEntity> = emptyList(),
        contentMemos: List<ContentMemoEntity> = emptyList(),
        diaries: List<DiaryEntity> = emptyList(),
        todos: List<TodoEntity> = emptyList(),
        books: List<BookEntity> = emptyList(),
        routines: List<RoutineEntity> = emptyList(),
        /** 루틴에 적어 둔 메모. 루틴에서 실제로 글을 쓰는 자리는 여기뿐입니다. */
        routineMemos: List<RoutineMemoEntity> = emptyList(),
        growthReports: List<GrowthReportEntity> = emptyList()
    ): SearchResults {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return SearchResults(query = query, hits = emptyList())

        val hits = buildList {
            val memoBodiesByItem = contentMemos.groupBy { it.contentItemId }
            val contentGroups = items.groupBy { item ->
                when (item.type) {
                    ContentType.MEMO -> SearchKind.MEMO
                    ContentType.CONCEPT -> SearchKind.CONCEPT
                    ContentType.INBOX -> SearchKind.INBOX
                    ContentType.NOT_TO_DO -> SearchKind.NOT_TO_DO
                    ContentType.PRINCIPLE -> SearchKind.PRINCIPLE
                    else -> SearchKind.QUOTE
                }
            }
            contentGroups.forEach { (kind, content) ->
                addAll(
                    content.matching(
                        terms = terms,
                        fields = { item ->
                            listOf(item.title, item.body, item.author, item.sourceUrl, item.category, item.tags.joinToString(" ")) +
                                memoBodiesByItem[item.id].orEmpty().map { it.body }
                        }
                    ) { item ->
                        SearchHit(
                            kind = kind,
                            id = item.id,
                            title = item.title.ifBlank { item.body.take(30) },
                            snippet = snippetOf(
                                (listOf(item.body) + memoBodiesByItem[item.id].orEmpty().map { it.body })
                                    .joinToString(" "),
                                terms
                            ),
                            meta = listOfNotNull(
                                item.author.takeIf { it.isNotBlank() },
                                item.category.takeIf { it.isNotBlank() }
                            ).joinToString(" · ")
                        )
                    }
                )
            }
            addAll(
                diaries.matching(
                    terms = terms,
                    // 감사·반성 일기는 본문이 비고 답에만 적히므로 답도 함께 봅니다.
                    fields = { diary ->
                        listOf(diary.title, diary.body, diary.entryDate) + diary.answers
                    }
                ) { diary ->
                    SearchHit(
                        kind = SearchKind.DIARY,
                        id = diary.id,
                        title = diary.displayTitle.ifBlank { diary.entryDate },
                        snippet = snippetOf(
                            diary.body.ifBlank {
                                diary.filledAnswers.joinToString(" · ") { (prompt, answer) ->
                                    "$prompt: $answer"
                                }
                            },
                            terms
                        ),
                        meta = listOfNotNull(
                            diary.entryDate,
                            diary.kindOption.takeIf { it.isGuided }?.label,
                            diary.weatherOption?.let { "${it.emoji} ${it.label}" },
                            diary.moodOption?.let { "${it.emoji} ${it.label}" }
                        ).joinToString(" · ")
                    )
                }
            )
            addAll(
                todos.matching(
                    terms = terms,
                    fields = { todo -> listOf(todo.title, todo.note, todo.dueDate) }
                ) { todo ->
                    SearchHit(
                        kind = SearchKind.TODO,
                        id = todo.id,
                        title = todo.title,
                        snippet = snippetOf(todo.note, terms),
                        meta = listOfNotNull(
                            todo.dueDate,
                            if (todo.doneAt != null) "끝냄" else null
                        ).joinToString(" · ")
                    )
                }
            )
            addAll(
                books.matching(
                    terms = terms,
                    // 독후감이 책에서 가장 긴 글입니다. 여기서 빼면 정작 쓴 것을 못 찾습니다.
                    fields = { book -> listOf(book.title, book.author, book.note, book.review) }
                ) { book ->
                    SearchHit(
                        kind = SearchKind.BOOK,
                        id = book.id,
                        title = book.title,
                        // 메모와 독후감을 함께 훑어 걸린 자리를 보여 줍니다.
                        snippet = snippetOf(listOf(book.note, book.review).joinToString(" ").trim(), terms),
                        meta = listOfNotNull(
                            book.author.takeIf { it.isNotBlank() },
                            if (book.isFinished) "읽음" else "읽는 중"
                        ).joinToString(" · ")
                    )
                }
            )
            // 루틴에 적어 둔 메모도 함께 봅니다. 루틴에서 글을 쓰는 자리는 메모뿐이라,
            // 빼 두면 "그때 뭐라고 적었더라"를 찾을 방법이 없습니다.
            // 메모를 따로 내놓지 않고 루틴에 얹는 이유: 제목과 메모가 같이 걸려도 한 줄이어야
            // 결과가 같은 루틴으로 두 번 나오지 않습니다(글귀 메모와 같은 방식).
            val memosByRoutine = routineMemos.groupBy { it.routineId }
            addAll(
                routines.matching(
                    terms = terms,
                    fields = { routine ->
                        listOf(routine.title, routine.note, routine.category) +
                            memosByRoutine[routine.id].orEmpty().map { it.body }
                    }
                ) { routine ->
                    val memoBodies = memosByRoutine[routine.id].orEmpty()
                        .sortedByDescending { it.createdAt }
                        .map { it.body }
                    SearchHit(
                        kind = SearchKind.ROUTINE,
                        id = routine.id,
                        title = routine.title,
                        snippet = snippetOf((listOf(routine.note) + memoBodies).joinToString(" ").trim(), terms),
                        meta = listOfNotNull(
                            routine.category.takeIf { it.isNotBlank() },
                            "메모 ${memoBodies.size}개".takeIf { memoBodies.isNotEmpty() }
                        ).joinToString(" · ")
                    )
                }
            )
            // 돌아보기는 쌓이기만 하고 찾을 수 없었습니다. "봄에 뭐라고 했더라"를 찾으려면
            // 목록을 끝까지 굴리는 수밖에 없었습니다.
            addAll(
                growthReports.matching(
                    terms = terms,
                    fields = { report ->
                        report.strengths + report.improvements + report.suggestedRoutines +
                            listOf(report.suggestedQuote, report.guide)
                    }
                ) { report ->
                    SearchHit(
                        kind = SearchKind.GROWTH,
                        id = report.id,
                        title = "${ReportPeriod.of(report.period).label} 돌아보기",
                        snippet = snippetOf(
                            (report.improvements + report.strengths).joinToString(" ").ifBlank { report.guide },
                            terms
                        ),
                        meta = listOfNotNull(
                            "${report.periodStart} ~ ${report.periodEnd}".takeIf { report.periodStart.isNotBlank() },
                            report.model.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                    )
                }
            )
        }

        return SearchResults(query = query, hits = hits)
    }

    private fun <T> List<T>.matching(
        terms: List<String>,
        fields: (T) -> List<String>,
        toHit: (T) -> SearchHit
    ): List<SearchHit> = asSequence()
        .filter { record ->
            val haystack = fields(record).joinToString(" ").lowercase()
            terms.all { haystack.contains(it) }
        }
        .take(LIMIT_PER_KIND)
        .map(toHit)
        .toList()

    /**
     * 찾은 말 주변만 잘라 냅니다.
     *
     * 긴 본문을 앞에서부터 자르면 정작 걸린 부분이 안 보입니다.
     */
    private fun snippetOf(body: String, terms: List<String>): String {
        val text = body.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return ""

        val at = terms.firstNotNullOfOrNull { term ->
            text.lowercase().indexOf(term).takeIf { it >= 0 }
        } ?: 0

        val start = (at - SNIPPET_RADIUS).coerceAtLeast(0)
        val end = (at + SNIPPET_RADIUS).coerceAtMost(text.length)
        return buildString {
            if (start > 0) append("…")
            append(text.substring(start, end))
            if (end < text.length) append("…")
        }
    }
}
