package com.codex.appgoodwords.data

/**
 * 파일 앞부분을 보고 형식을 알아냅니다.
 *
 * 기기는 대개 형식을 알려 주지만(`ContentResolver.getType`), 주소에 확장자가 없고 제공자도 형식을 안 줄 때가
 * 있습니다. 그때 멀쩡한 사진을 "형식을 알 수 없다"며 못 올리면 안 됩니다. 서버가 받는 형식(`server` 첨부 목록)만
 * 돌려줍니다 — 서버가 거절할 형식을 맞혀 봐야 올리는 데서 다시 실패할 뿐입니다.
 *
 * 순수 함수라 기기 없이 검증할 수 있습니다.
 */
object AttachmentTypes {
    fun sniff(head: ByteArray): String = when {
        head.startsWith(0xFF, 0xD8, 0xFF) -> "image/jpeg"
        head.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
        head.asciiAt(0, 6) == "GIF87a" || head.asciiAt(0, 6) == "GIF89a" -> "image/gif"
        head.asciiAt(0, 4) == "RIFF" && head.asciiAt(8, 4) == "WEBP" -> "image/webp"
        head.asciiAt(0, 4) == "RIFF" && head.asciiAt(8, 4) == "WAVE" -> "audio/wav"
        head.asciiAt(4, 4) == "ftyp" -> isoMediaType(head.asciiAt(8, 4))
        head.startsWith(0x1A, 0x45, 0xDF, 0xA3) -> "video/webm"
        head.asciiAt(0, 4) == "OggS" -> "audio/ogg"
        head.asciiAt(0, 5) == "#!AMR" -> "audio/amr"
        head.asciiAt(0, 3) == "ID3" || (head.size >= 2 && head.byteAt(0) == 0xFF && (head.byteAt(1) and 0xE0) == 0xE0) ->
            "audio/mpeg"
        else -> ""
    }

    /**
     * MP4 계열은 네 글자 상표로 갈립니다. 삼성 카메라의 "고효율 사진"은 HEIC로 남습니다.
     * 모르는 상표는 영상으로 봅니다 — 이 계열의 대부분이 MP4 영상입니다.
     */
    private fun isoMediaType(brand: String): String = when (brand) {
        "heic", "heix", "hevc", "hevx", "heim", "heis" -> "image/heic"
        "mif1", "msf1", "heif" -> "image/heif"
        "avif", "avis" -> ""
        "qt  " -> "video/quicktime"
        "3gp4", "3gp5", "3gp6", "3g2a" -> "video/3gpp"
        "M4A " -> "audio/mp4"
        else -> "video/mp4"
    }

    private fun ByteArray.byteAt(index: Int): Int = this[index].toInt() and 0xFF

    private fun ByteArray.startsWith(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { byteAt(it) == expected[it] }

    private fun ByteArray.asciiAt(offset: Int, length: Int): String =
        if (size < offset + length) "" else String(this, offset, length, Charsets.ISO_8859_1)
}
