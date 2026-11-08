package com.codex.appgoodwords.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 기기가 형식을 안 알려 줄 때 파일 앞부분으로 알아내는지 봅니다.
 *
 * 틀리면 멀쩡한 사진이 "형식을 알 수 없다"며 서버에 못 가고, 휴대폰에만 남습니다.
 */
class AttachmentTypesTest {
    @Test
    fun commonPhotosAreRecognized() {
        assertEquals("image/jpeg", AttachmentTypes.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE1) + ascii("Exif")))
        assertEquals("image/png", AttachmentTypes.sniff(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)))
        assertEquals("image/gif", AttachmentTypes.sniff(ascii("GIF89a") + bytes(0x01, 0x00)))
        assertEquals("image/webp", AttachmentTypes.sniff(ascii("RIFF") + bytes(0x24, 0, 0, 0) + ascii("WEBPVP8 ")))
    }

    @Test
    fun samsungHighEfficiencyPhotosAreHeic() {
        // 삼성 카메라의 "고효율 사진"입니다. 상자 크기 4바이트 뒤에 ftyp와 상표가 옵니다.
        assertEquals("image/heic", AttachmentTypes.sniff(bytes(0, 0, 0, 0x18) + ascii("ftypheic") + bytes(0, 0, 0, 0)))
        assertEquals("image/heif", AttachmentTypes.sniff(bytes(0, 0, 0, 0x18) + ascii("ftypmif1") + bytes(0, 0, 0, 0)))
    }

    @Test
    fun videosAndSoundsFollowTheServerList() {
        assertEquals("video/mp4", AttachmentTypes.sniff(bytes(0, 0, 0, 0x20) + ascii("ftypisom")))
        assertEquals("video/quicktime", AttachmentTypes.sniff(bytes(0, 0, 0, 0x14) + ascii("ftypqt  ")))
        assertEquals("audio/mp4", AttachmentTypes.sniff(bytes(0, 0, 0, 0x20) + ascii("ftypM4A ")))
        assertEquals("audio/mpeg", AttachmentTypes.sniff(ascii("ID3") + bytes(0x04, 0x00)))
        assertEquals("audio/ogg", AttachmentTypes.sniff(ascii("OggS") + bytes(0x00)))
    }

    @Test
    fun unknownOrUnacceptedFormatsStayBlank() {
        // 서버가 받지 않는 형식을 맞혀 봐야 올리는 데서 다시 실패합니다. 모르는 채로 둡니다.
        assertEquals("", AttachmentTypes.sniff(bytes(0, 0, 0, 0x1C) + ascii("ftypavif")))
        assertEquals("", AttachmentTypes.sniff(ascii("%PDF-1.7")))
        assertEquals("", AttachmentTypes.sniff(ByteArray(0)))
        assertEquals("", AttachmentTypes.sniff(bytes(0xFF)))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)
}
