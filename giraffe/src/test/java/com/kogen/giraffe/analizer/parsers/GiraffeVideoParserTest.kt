package com.kogen.giraffe.analizer.parsers

import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.analizer.utils.ProtoWireScanner
import com.kogen.giraffe.testutil.fakeContext
import com.kogen.giraffe.testutil.lengthDelimitedField
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GiraffeVideoParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = GiraffeVideoParser()

    private fun bigEndianInt(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    @Test
    fun `walks the MP4 box chain and trims trailing noise past the last box`() {
        val ftypBox = bigEndianInt(20) + "ftyp".toByteArray() + ByteArray(12) { it.toByte() }
        val moovBox = bigEndianInt(8) + "moov".toByteArray()
        val trailingNoise = ByteArray(6) { 0x5A }
        val mp4Bytes = ftypBox + moovBox + trailingNoise
        val originalBytes = lengthDelimitedField(fieldNumber = 3, payload = mp4Bytes)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Video)
        val expectedChunk = mp4Bytes.copyOfRange(0, ftypBox.size + moovBox.size)
        assertThat(result.bytes).isEqualTo(expectedChunk)
        val savedFile = File(result.filePath!!)
        assertThat(savedFile.readBytes()).isEqualTo(expectedChunk)
        assertThat(savedFile.extension).isEqualTo("mp4")
    }

    @Test
    fun `returns null without an ftyp box`() {
        val originalBytes = lengthDelimitedField(fieldNumber = 3, payload = ByteArray(30) { (it % 7).toByte() })
        val context = fakeContext(tempFolder.root)

        assertThat(parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)).isNull()
    }
}
