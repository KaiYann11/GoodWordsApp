package com.codex.appgoodwords.data

/**
 * 글귀를 그때그때 다른 차례로 늘어놓습니다.
 *
 * 담은 차례(최신순) 그대로 두면 늘 같은 글귀만 먼저 만나게 되어, 뒤쪽에 담아 둔 글귀는
 * 좀처럼 눈에 들어오지 않습니다.
 *
 * `shuffled()`를 쓰지 않습니다. 목록이 조금이라도 바뀌면(글귀 하나를 읽음으로 넘기면)
 * 남은 글귀까지 전부 다시 섞여서, 방금 읽던 자리가 눈앞에서 사라집니다.
 * 그래서 글귀마다 (씨앗, syncId)로 정해지는 번호를 붙이고 그 번호대로 세웁니다.
 * 목록에서 하나가 빠져도 남은 것들의 차례는 그대로입니다.
 */
object ContentShuffle {
    /** 씨앗이 0이면 섞지 않고 받은 차례 그대로 둡니다. */
    fun ordered(items: List<ContentItemEntity>, seed: Long): List<ContentItemEntity> {
        if (seed == 0L || items.size < 2) return items
        return items.sortedWith(
            compareBy<ContentItemEntity> { key(seed, it) }.thenBy { it.id }
        )
    }

    /**
     * 글귀 하나가 받는 번호.
     *
     * 기기마다 따로 증가하는 숫자 id 대신 syncId로 셈합니다. 같은 씨앗이면 어느 기기에서나
     * 같은 차례가 되고, 동기화로 id가 다시 매겨져도 보던 줄이 흔들리지 않습니다.
     */
    private fun key(seed: Long, item: ContentItemEntity): Long {
        val id = item.syncId.ifBlank { item.id.toString() }
        var hash = seed
        for (index in id.indices) {
            hash = hash * 31 + id[index].code
        }
        return mix(hash)
    }

    /**
     * splitmix64의 마지막 섞기.
     *
     * 곱셈만 하면 비슷한 syncId끼리 이웃한 번호를 받아 늘 붙어 다닙니다.
     */
    private fun mix(value: Long): Long {
        var z = value
        z = (z xor (z ushr 30)) * -0x40A7B892E31B1A47L
        z = (z xor (z ushr 27)) * -0x6B2FB644ECCEEE15L
        return z xor (z ushr 31)
    }
}
