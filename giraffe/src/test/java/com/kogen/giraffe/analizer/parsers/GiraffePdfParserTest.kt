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

class GiraffePdfParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = GiraffePdfParser()

    @Test
    fun `extracts a PDF bounded by its last EOF marker and trims trailing noise`() {
        val header = "%PDF-1.4\n".toByteArray()
        val filler1 = ByteArray(20) { it.toByte() }
        val intermediateEof = "%%EOF\n".toByteArray() // an earlier revision's EOF - not the real end
        val filler2 = ByteArray(10) { (it + 1).toByte() }
        val finalEof = "%%EOF".toByteArray()
        val pdfBody = header + filler1 + intermediateEof + filler2 + finalEof
        val trailingNoise = "\n".toByteArray() + ByteArray(6) { 0x5A }
        val leaf = pdfBody + trailingNoise
        val originalBytes = lengthDelimitedField(fieldNumber = 6, payload = leaf)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Pdf)
        assertThat(result.bytes).isEqualTo(pdfBody)
        val savedFile = File(result.filePath!!)
        assertThat(savedFile.readBytes()).isEqualTo(pdfBody)
        assertThat(savedFile.extension).isEqualTo("pdf")
    }

    @Test
    fun `falls back to the whole leaf when no EOF marker is present`() {
        val pdfBody = "%PDF-1.7\n".toByteArray() + ByteArray(20) { it.toByte() }
        val originalBytes = lengthDelimitedField(fieldNumber = 6, payload = pdfBody)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.bytes).isEqualTo(pdfBody)
    }

    @Test
    fun `detects a minimal, uncompressed PDF made entirely of printable ASCII`() {
        // Unlike PNG/JPEG/MP4/MP3, whose signature or compressed payload bytes reliably fail a
        // UTF-8 "is this actually text" check, an uncompressed PDF with no embedded binary images
        // can be pure printable ASCII end to end - exactly what a real "sample.pdf" test fixture
        // (or ProtoWireScanner) would produce.
        val asciiPdf = (
            "%PDF-1.4\n" +
                "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                "trailer\n<< /Size 3 /Root 1 0 R >>\n" +
                "%%EOF\n"
            ).toByteArray()
        val originalBytes = lengthDelimitedField(fieldNumber = 7, payload = asciiPdf)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Pdf)
    }

    @Test
    fun `returns null without a PDF header`() {
        val originalBytes = lengthDelimitedField(fieldNumber = 6, payload = ByteArray(30) { (it % 5).toByte() })
        val context = fakeContext(tempFolder.root)

        assertThat(parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)).isNull()
    }
}
