package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType

internal const val notToDoAddTag = "not_to_do_add"
internal const val notToDoTitleTag = "not_to_do_title"
internal const val notToDoMemoTag = "not_to_do_memo"

private val notToDoLabels = RuleListLabels(
    type = ContentType.NOT_TO_DO,
    name = "하지 않을 일",
    intro = "반복하지 않기로 한 행동과 그 이유를 적어 두세요.",
    titleLabel = "하지 않을 일",
    titlePlaceholder = "예: 잠들기 전 숏폼 오래 보기",
    memoLabel = "메모 (선택)",
    memoPlaceholder = "피하려는 이유나 기억할 점",
    emptyTitle = "아직 기록한 항목이 없습니다.",
    emptyBody = "예: 잠들기 전 숏폼 오래 보기, 화가 난 채로 답장하기. 피하고 싶은 행동부터 하나 적어 보세요.",
    tagPrefix = "not_to_do"
)

/** 계속 확인할 기준을 적습니다. 읽음이나 루틴 수행으로 세지 않습니다. */
@Composable
fun NotToDoScreen(
    items: List<ContentItemEntity>,
    onSave: suspend (ContentDraft) -> Result<Unit>,
    onDelete: suspend (Long) -> Result<Unit>,
    modifier: Modifier = Modifier,
    focusId: Long? = null
) {
    RuleListScreen(
        labels = notToDoLabels,
        items = items,
        onSave = onSave,
        onDelete = onDelete,
        modifier = modifier,
        focusId = focusId
    )
}
