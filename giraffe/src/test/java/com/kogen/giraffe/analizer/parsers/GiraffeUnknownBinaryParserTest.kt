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
import java.util.UUID

class GiraffeUnknownBinaryParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = GiraffeUnknownBinaryParser()

    @Test
    fun `saves a sufficiently large opaque binary leaf as an unknown-content blob`() {
        val binary = ByteArray(200) { (it * 37 xor 0x5A).toByte() }
        val originalBytes = lengthDelimitedField(fieldNumber = 11, payload = binary)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Unknown)
        assertThat(result.bytes).isEqualTo(binary)
        val savedFile = File(result.filePath!!)
        assertThat(savedFile.readBytes()).isEqualTo(binary)
        assertThat(savedFile.extension).isEqualTo("bin")
    }

    @Test
    fun `ignores a raw random UUID - too small to be a real file`() {
        val uuidBytes = ByteArray(16) { (it * 11 xor 0x3C).toByte() }
        val originalBytes = lengthDelimitedField(fieldNumber = 11, payload = uuidBytes)
        val context = fakeContext(tempFolder.root)

        assertThat(parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)).isNull()
    }

    @Test
    fun `ignores a random UUID wrapped in a nested single-field message`() {
        // Mirrors a common real-world shape for a nullable id/trace-id field: the raw UUID
        // bytes nested one level deeper inside a tiny wrapper message. The wrapper is only a
        // couple of bytes larger than the UUID itself - still nowhere near a real attachment.
        val uuid = UUID.randomUUID()
        val uuidBytes = ByteArray(16).also {
            val buffer = java.nio.ByteBuffer.wrap(it)
            buffer.putLong(uuid.mostSignificantBits)
            buffer.putLong(uuid.leastSignificantBits)
        }
        val wrapped = lengthDelimitedField(fieldNumber = 1, payload = uuidBytes)
        val originalBytes = lengthDelimitedField(fieldNumber = 11, payload = wrapped)
        val context = fakeContext(tempFolder.root)

        assertThat(parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)).isNull()
    }

    @Test
    fun `returns null when the message has no binary leaves at all`() {
        val originalBytes = lengthDelimitedField(
            fieldNumber = 11,
            payload = "just a short plain-text field".toByteArray(),
        )
        val context = fakeContext(tempFolder.root)

        assertThat(parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)).isNull()
    }
}
