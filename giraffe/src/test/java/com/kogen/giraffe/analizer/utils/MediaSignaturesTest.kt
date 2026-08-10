package com.kogen.giraffe.analizer.utils

import android.util.Base64
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Base64 as JavaBase64

class MediaSignaturesTest {

    @Before
    fun setUp() {
        // android.util.Base64 has no real implementation on the JVM unit-test classpath;
        // delegate it to the real java.util.Base64 codec so tryDecodeBase64 can be exercised.
        mockkStatic(Base64::class)
        every { Base64.decode(any<String>(), any()) } answers {
            try {
                JavaBase64.getDecoder().decode(firstArg<String>())
            } catch (e: IllegalArgumentException) {
                throw e
            }
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun `matchesAt confirms a signature exactly at the given offset`() {
        val bytes = byteArrayOf(0, 0, 0x89.toByte(), 0x50, 0x4E, 0x47)

        assertThat(MediaSignatures.matchesAt(bytes, 2, MediaSignatures.PNG)).isTrue()
        assertThat(MediaSignatures.matchesAt(bytes, 0, MediaSignatures.PNG)).isFalse()
    }

    @Test
    fun `matchesAt is false when the signature would run past the end of the array`() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50)

        assertThat(MediaSignatures.matchesAt(bytes, 0, MediaSignatures.PNG)).isFalse()
    }

    @Test
    fun `indexOf finds the first occurrence and returns -1 when absent`() {
        val bytes = byteArrayOf(1, 2, 3, 0xFF.toByte(), 0xD9.toByte(), 9)

        assertThat(MediaSignatures.indexOf(bytes, MediaSignatures.JPEG_END)).isEqualTo(3)
        assertThat(MediaSignatures.indexOf(bytes, MediaSignatures.PNG)).isEqualTo(-1)
    }

    @Test
    fun `findEndOfMedia returns the offset right after the terminator`() {
        val bytes = byteArrayOf(1, 2) + MediaSignatures.PNG_END + byteArrayOf(9, 9)

        val end = MediaSignatures.findEndOfMedia(bytes, 0, MediaSignatures.PNG_END)

        assertThat(end).isEqualTo(2 + MediaSignatures.PNG_END.size)
    }

    @Test
    fun `findEndOfMedia returns -1 when the terminator is missing`() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertThat(MediaSignatures.findEndOfMedia(bytes, 0, MediaSignatures.PNG_END)).isEqualTo(-1)
    }

    @Test
    fun `findLastEndOfMedia picks the final terminator among several`() {
        val bytes = MediaSignatures.JPEG_END + byteArrayOf(1, 2, 3) + MediaSignatures.JPEG_END + byteArrayOf(9)

        val end = MediaSignatures.findLastEndOfMedia(bytes, 0, MediaSignatures.JPEG_END)

        val expected = MediaSignatures.JPEG_END.size + 3 + MediaSignatures.JPEG_END.size
        assertThat(end).isEqualTo(expected)
    }

    @Test
    fun `findRiffEnd computes the end from the little-endian chunk size`() {
        val chunkSize = 20
        val header = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()) +
            intToLittleEndian(chunkSize)
        val bytes = header + ByteArray(chunkSize)

        assertThat(MediaSignatures.findRiffEnd(bytes, 0)).isEqualTo(8 + chunkSize)
    }

    @Test
    fun `findRiffEnd returns -1 when the declared chunk size overruns the buffer`() {
        val header = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()) +
            intToLittleEndian(1000)
        val bytes = header + ByteArray(4)

        assertThat(MediaSignatures.findRiffEnd(bytes, 0)).isEqualTo(-1)
    }

    @Test
    fun `findMp4End walks box headers until they no longer fit`() {
        // Box 1: size=20, type="ftyp", 12 bytes of payload.
        val ftypBox = intToBigEndian(20) + "ftyp".toByteArray() + ByteArray(12)
        // Box 2: size=8, type="moov", no payload (bare header).
        val moovBox = intToBigEndian(8) + "moov".toByteArray()
        val bytes = ftypBox + moovBox

        val ftypIndex = 4 // offset of the "ftyp" tag within ftypBox

        assertThat(MediaSignatures.findMp4End(bytes, ftypIndex)).isEqualTo(bytes.size)
    }

    @Test
    fun `findMp4End returns -1 when no full box follows`() {
        val bytes = intToBigEndian(20) + "ftyp".toByteArray()

        assertThat(MediaSignatures.findMp4End(bytes, 4)).isEqualTo(-1)
    }

    @Test
    fun `isLikelyUtf8Text accepts plain text and rejects binary noise`() {
        assertThat(MediaSignatures.isLikelyUtf8Text("hello world".toByteArray())).isTrue()
        assertThat(MediaSignatures.isLikelyUtf8Text(ByteArray(20) { 0xFF.toByte() })).isFalse()
        // A stray NUL byte is technically valid UTF-8 but should still be treated as binary.
        assertThat(MediaSignatures.isLikelyUtf8Text(byteArrayOf(0, 1, 2))).isFalse()
    }

    @Test
    fun `isLikelyUtf8Text considers a minimal, uncompressed PDF to be text`() {
        // Deliberately: this function means "not text", full stop, for every caller (e.g.
        // PcmAudioHeuristics.looksLikePcm16 relies on that). ProtoWireScanner special-cases PDF
        // itself for its own leaf-vs-text decision rather than this function lying about it.
        val asciiPdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".toByteArray()

        assertThat(MediaSignatures.isLikelyUtf8Text(asciiPdf)).isTrue()
    }

    @Test
    fun `tryDecodeBase64 decodes a well-formed payload and strips a data URI prefix`() {
        val original = "hello giraffe".toByteArray()
        val encoded = JavaBase64.getEncoder().encodeToString(original)

        assertThat(MediaSignatures.tryDecodeBase64(encoded)).isEqualTo(original)
        assertThat(MediaSignatures.tryDecodeBase64("data:image/png;base64,$encoded")).isEqualTo(original)
    }

    @Test
    fun `tryDecodeBase64 rejects malformed input instead of throwing`() {
        assertThat(MediaSignatures.tryDecodeBase64("short")).isNull()
        assertThat(MediaSignatures.tryDecodeBase64("not-base64!!")).isNull()
    }

    private fun intToLittleEndian(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun intToBigEndian(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )
}
