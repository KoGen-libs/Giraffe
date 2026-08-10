package com.kogen.giraffe.analizer.utils

import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.testutil.lengthDelimitedField
import com.kogen.giraffe.testutil.tag
import com.kogen.giraffe.testutil.varint
import com.kogen.giraffe.testutil.varintField
import org.junit.Test

class ProtoWireScannerTest {

    private val scanner = ProtoWireScanner()

    @Test
    fun `scan decodes every wire type at the correct field number`() {
        val varintPart = varintField(fieldNumber = 1, value = 150L)
        val lengthDelimitedPart = lengthDelimitedField(fieldNumber = 2, payload = "hello".toByteArray())
        val fixed64Part = tag(3, 1) + ByteArray(8) { 0x11 }
        val fixed32Part = tag(4, 5) + ByteArray(4) { 0x22 }

        val fields = scanner.scan(varintPart + lengthDelimitedPart + fixed64Part + fixed32Part)

        assertThat(fields).hasSize(4)
        assertThat(fields[0].fieldNumber).isEqualTo(1)
        assertThat(fields[0].wireType).isEqualTo(0)

        assertThat(fields[1].fieldNumber).isEqualTo(2)
        assertThat(fields[1].wireType).isEqualTo(2)
        assertThat(fields[1].bytes).isEqualTo("hello".toByteArray())

        assertThat(fields[2].fieldNumber).isEqualTo(3)
        assertThat(fields[2].wireType).isEqualTo(1)

        assertThat(fields[3].fieldNumber).isEqualTo(4)
        assertThat(fields[3].wireType).isEqualTo(5)
    }

    @Test
    fun `scan stops cleanly on truncated input instead of throwing`() {
        // A length-delimited tag that claims more bytes than actually follow.
        val truncated = tag(1, 2) + varint(1000L) + byteArrayOf(1, 2, 3)

        val fields = scanner.scan(truncated)

        assertThat(fields).isEmpty()
    }

    @Test
    fun `findBinaryLeaves ignores plain UTF-8 text payloads`() {
        val text = "This is an ordinary human-readable protobuf string field.".toByteArray()
        val message = lengthDelimitedField(fieldNumber = 9, payload = text)

        assertThat(scanner.findBinaryLeaves(message)).isEmpty()
    }

    @Test
    fun `findBinaryLeaves ignores payloads smaller than minSize`() {
        val tinyBinary = ByteArray(10) { 0xFF.toByte() }
        val message = lengthDelimitedField(fieldNumber = 5, payload = tinyBinary)

        assertThat(scanner.findBinaryLeaves(message, minSize = 17)).isEmpty()
    }

    @Test
    fun `findBinaryLeaves returns opaque binary payloads at or above minSize`() {
        val binary = ByteArray(20) { 0xFF.toByte() }
        val message = lengthDelimitedField(fieldNumber = 5, payload = binary)

        val leaves = scanner.findBinaryLeaves(message, minSize = 17)

        assertThat(leaves).hasSize(1)
        assertThat(leaves[0]).isEqualTo(binary)
    }

    @Test
    fun `findBinaryLeaves descends through a real nested message to find the true leaf`() {
        val innerLeaf = ByteArray(20) { 0xFF.toByte() }
        val innerMessage = lengthDelimitedField(fieldNumber = 5, payload = innerLeaf)
        // innerMessage is itself a fully-formed protobuf message (one byte-carrying field that
        // consumes ~91% of it), so it should be recursed into rather than reported verbatim.
        val outerMessage = lengthDelimitedField(fieldNumber = 1, payload = innerMessage)

        val leaves = scanner.findBinaryLeaves(outerMessage, minSize = 17)

        // ByteArray has no structural equals(), so compare sizes/contents explicitly rather
        // than via containsExactly (which would compare leaves by reference).
        assertThat(leaves).hasSize(1)
        assertThat(leaves[0]).isEqualTo(innerLeaf)
    }

    @Test
    fun `findBinaryLeaves does not skip a plain-ASCII PDF the way it would skip ordinary text`() {
        // A minimal, uncompressed PDF is otherwise indistinguishable from ordinary text - this is
        // the one deliberate exception to the "ignores plain UTF-8 text" rule above.
        val pdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n".toByteArray()
        val message = lengthDelimitedField(fieldNumber = 7, payload = pdf)

        val leaves = scanner.findBinaryLeaves(message)

        assertThat(leaves).hasSize(1)
        assertThat(leaves[0]).isEqualTo(pdf)
    }

    @Test
    fun `findBinaryLeaves falls back to the raw payload when a noise run parses as an empty message`() {
        // A long run of zero bytes parses "validly" as a chain of empty wireType=0 fields, but
        // carries zero real (wireType=2) bytes, so it must NOT be discarded as a nested message.
        val noise = ByteArray(24) { 0 }
        val message = lengthDelimitedField(fieldNumber = 5, payload = noise)

        val leaves = scanner.findBinaryLeaves(message, minSize = 17)

        assertThat(leaves).hasSize(1)
        assertThat(leaves[0]).isEqualTo(noise)
    }
}
