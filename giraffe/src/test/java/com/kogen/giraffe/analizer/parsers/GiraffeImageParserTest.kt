package com.kogen.giraffe.analizer.parsers

import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.analizer.utils.MediaSignatures
import com.kogen.giraffe.analizer.utils.ProtoWireScanner
import com.kogen.giraffe.testutil.fakeContext
import com.kogen.giraffe.testutil.lengthDelimitedField
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GiraffeImageParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = GiraffeImageParser()

    @Test
    fun `extracts a PNG buried inside a protobuf bytes field and trims trailing noise`() {
        val pngPayload = MediaSignatures.PNG + ByteArray(10) { it.toByte() } +
            MediaSignatures.PNG_END + ByteArray(5) { 0x7A }
        val originalBytes = lengthDelimitedField(fieldNumber = 7, payload = pngPayload)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Image)
        val expectedChunk = pngPayload.copyOfRange(0, pngPayload.size - 5)
        assertThat(result.bytes).isEqualTo(expectedChunk)
        assertThat(result.filePath).isNotNull()
        val savedFile = File(result.filePath!!)
        assertThat(savedFile.exists()).isTrue()
        assertThat(savedFile.readBytes()).isEqualTo(expectedChunk)
        assertThat(savedFile.extension).isEqualTo("png")
    }

    @Test
    fun `returns null when no known image signature is present`() {
        val originalBytes = lengthDelimitedField(fieldNumber = 7, payload = ByteArray(30) { (it % 5).toByte() })
        val context = fakeContext(tempFolder.root)

        assertThat(parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)).isNull()
    }
}
