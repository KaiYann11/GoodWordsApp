package com.codex.appgoodwords.data

/**
 * 보관함에 담기는 것의 종류.
 *
 * [IDEA]는 남의 좋은 말이 아니라 **내가 번뜩인 것**입니다. 할 일에 적어 두지 않는 이유가
 * 있습니다. 할 일은 하기로 한 것이고 아이디어는 아직 아무것도 아닌데, 섞어 두면 할 일 목록이
 * "안 한 것들"로 불어나 목록 자체를 믿을 수 없게 됩니다.
 *
 * 번뜩인 것의 핵심 성질은 **나중에 다시 만나야 한다**는 것입니다. 그 일을 하는 장치(알림·위젯·
 * 앱 켤 때 섞기·안 읽은 것부터 보여 주기)가 보관함에 다 있어서 여기에 둡니다.
 * 익어서 할 만한 것이 되면 그때 루틴이나 할 일로 옮깁니다.
 */
enum class ContentType {
    QUOTE,
    LINK,
    VIDEO,
    IDEA,
    INBOX,
    MEMO,
    NOT_TO_DO,
    CONCEPT,

    /**
     * 지키기로 한 인생의 기준. [NOT_TO_DO]의 반대편입니다.
     *
     * 루틴과 다릅니다. 루틴은 **오늘 밟았는지**를 세는 것이고, 원칙은 세지 않습니다.
     * 지킨 날을 세기 시작하면 원칙이 또 하나의 할 일이 되어, 못 지킨 날 목록만 쌓입니다.
     * 그래서 수행 버튼이 없습니다 — 그냥 거기 있고, 흔들릴 때 다시 읽는 것입니다.
     */
    PRINCIPLE;

    /** 글귀 목록과 알림에서 다시 보여 줄 종류. 미분류 글과 개인 메모는 따로 둡니다. */
    val isReadingContent: Boolean
        get() = this in readingTypes

    /** 읽음·알림·위젯·AI 추천 원문에서 빼는 종류. 남의 좋은 말이 아니라 내가 적어 둔 것입니다. */
    val isPersonalRecord: Boolean
        get() = this in personalTypes

    companion object {
        val readingTypes = listOf(QUOTE, LINK, VIDEO)

        /**
         * 읽음으로 세지 않는 종류.
         *
         * 한곳에 모아 둡니다. 예전에는 이 목록이 여러 군데에 따로 적혀 있어서, 종류를 늘릴 때
         * 한 곳만 빠뜨리면 그 종류가 "오늘의 글귀"라며 알림에 뜨거나 읽음 횟수로 세어졌습니다.
         */
        val personalTypes = listOf(INBOX, MEMO, NOT_TO_DO, CONCEPT, PRINCIPLE)
    }
}
