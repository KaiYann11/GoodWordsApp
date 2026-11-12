package com.codex.appgoodwords.data

/**
 * 이은 줄([RecordLinkEntity])을 다루는 셈. 순수 함수라 기기 없이 시험합니다.
 *
 * 줄에는 방향이 없습니다 — A에서 B로 이은 줄과 B에서 A로 이은 줄은 같은 줄입니다.
 */
object RecordLinks {
    /**
     * 같은 두 기록을 잇는 줄인지 가리는 지문. 두 끝을 글자 차례로 늘어놓아 방향을 지웁니다.
     *
     * 서버 `recordLinkFingerprint`와 같아야 합니다(어긋나면 두 기기가 끝없이 서로를 고칩니다). 끝을 모르면 빈 문자열이라
     * 아무것과도 묶이지 않습니다.
     */
    fun fingerprint(link: RecordLinkEntity): String {
        val from = link.fromSyncId.trim()
        val to = link.toSyncId.trim()
        if (from.isBlank() || to.isBlank()) return ""
        val a = link.fromKind.trim() + ":" + from
        val b = link.toKind.trim() + ":" + to
        return if (a <= b) "$a|$b" else "$b|$a"
    }

    /** [ref]에 닿은 줄. */
    fun touching(links: List<RecordLinkEntity>, ref: RecordRef): List<RecordLinkEntity> =
        links.filter { it.otherEnd(ref) != null }

    /** 두 기록을 잇는 줄. 어느 쪽에서 이었든 찾습니다. */
    fun between(links: List<RecordLinkEntity>, a: RecordRef, b: RecordRef): RecordLinkEntity? =
        links.firstOrNull { it.otherEnd(a) == b }

    /** [ref]와 이어진 기록들. 같은 기록을 두 번 이었어도(합치기 전의 사본) 한 번만 냅니다. */
    fun linkedTo(links: List<RecordLinkEntity>, ref: RecordRef): List<RecordRef> =
        links.mapNotNull { it.otherEnd(ref) }.distinct()
}
