package com.codex.appgoodwords.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codex.appgoodwords.data.ContentDraft
import com.codex.appgoodwords.data.ContentItemEntity
import com.codex.appgoodwords.data.ContentType

internal const val principleAddTag = "principle_add"
internal const val principleTitleTag = "principle_title"
internal const val principleMemoTag = "principle_memo"

private val principleLabels = RuleListLabels(
    type = ContentType.PRINCIPLE,
    name = "원칙",
    // 세지 않는다는 것을 먼저 말해 둡니다. 안 그러면 "오늘 지켰나"를 찾다가 없다고 여깁니다.
    intro = "지키기로 한 기준을 적어 두세요. 지킨 날을 세지 않습니다 — 흔들릴 때 다시 읽는 자리입니다.",
    titleLabel = "원칙",
    titlePlaceholder = "예: 화가 난 채로 답하지 않는다",
    memoLabel = "이유 (선택)",
    memoPlaceholder = "왜 그렇게 정했는지, 어길 뻔했던 순간",
    emptyTitle = "아직 적어 둔 원칙이 없습니다.",
    emptyBody = "예: 먼저 듣고 나중에 말한다, 몸이 힘들면 하루 쉰다. 지키고 싶은 기준을 하나 적어 보세요.",
    tagPrefix = "principle"
)

/**
 * 지키기로 한 인생의 기준.
 *
 * 하지 않을 일의 반대편입니다. 피할 것만 적어 두면 목록이 "안 되는 것"으로만 차서,
 * 무엇을 향해 가는지는 어디에도 남지 않습니다.
 */
@Composable
fun PrincipleScreen(
    items: List<ContentItemEntity>,
    onSave: suspend (ContentDraft) -> Result<Unit>,
    onDelete: suspend (Long) -> Result<Unit>,
    modifier: Modifier = Modifier,
    focusId: Long? = null
) {
    RuleListScreen(
        labels = principleLabels,
        items = items,
        onSave = onSave,
        onDelete = onDelete,
        modifier = modifier,
        focusId = focusId
    )
}
