package com.kogen.giraffe.analizer

import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.analizer.utils.MediaSignatures
import com.kogen.giraffe.testutil.fakeContext
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GiraffeRestBodyAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val analyzer = GiraffeRestBodyAnalyzer()

    @Test
    fun `classifies application-json as Json from the header alone`() {
        val bytes = """{"ok":true}""".toByteArray()
        val result = analyzer.analyze("application/json; charset=utf-8", bytes, fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.Json)
        assertThat(result.textContent).isEqualTo("""{"ok":true}""")
        assertThat(result.filePath).isNull()
    }

    @Test
    fun `saves a real PNG declared as image-png and verifies it against the signature`() {
        val bytes = MediaSignatures.PNG + ByteArray(20) { it.toByte() } + MediaSignatures.PNG_END
        val result = analyzer.analyze("image/png", bytes, fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.Image)
        assertThat(result.filePath).isNotNull()
        assertThat(File(result.filePath!!).readBytes()).isEqualTo(bytes)
    }

    @Test
    fun `downgrades to Unknown when Content-Type says image-png but the bytes don't match`() {
        // A real, sizeable body (>= the Unknown floor) that just isn't a PNG despite the header.
        val bytes = ByteArray(200) { (it * 7).toByte() }
        val result = analyzer.analyze("image/png", bytes, fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.Unknown)
    }

    @Test
    fun `trusts audio-ogg even though there's no signature to verify it against`() {
        // Not one of MediaSignatures' known audio formats (MP3/WAV) - the header is trusted
        // rather than downgraded, since a miss here means "unrecognized format", not "wrong header".
        val bytes = ByteArray(50) { it.toByte() }
        val result = analyzer.analyze("audio/ogg", bytes, fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.Audio)
        assertThat(result.filePath).isNotNull()
    }

    @Test
    fun `sniffs a PDF by magic bytes when there's no Content-Type at all`() {
        val bytes = "%PDF-1.4\nsome pdf content\n%%EOF".toByteArray()
        val result = analyzer.analyze(null, bytes, fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.Pdf)
        assertThat(result.filePath).isNotNull()
    }

    @Test
    fun `falls back to PlainText for ordinary text with no Content-Type`() {
        val result = analyzer.analyze(null, "just some plain text".toByteArray(), fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.PlainText)
        assertThat(result.textContent).isEqualTo("just some plain text")
    }

    @Test
    fun `treats a small opaque blob with no Content-Type as Unknown without saving a file`() {
        val bytes = ByteArray(16) { 0xFF.toByte() }
        val result = analyzer.analyze(null, bytes, fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.Unknown)
        assertThat(result.filePath).isNull()
        assertThat(result.textContent).isNull()
    }

    @Test
    fun `saves a large opaque blob with no Content-Type as an Unknown file`() {
        val bytes = ByteArray(200) { 0xFF.toByte() }
        val result = analyzer.analyze("application/octet-stream", bytes, fakeContext(tempFolder.root))

        assertThat(result.contentType).isEqualTo(GiraffeContentType.Unknown)
        assertThat(result.filePath).isNotNull()
        assertThat(File(result.filePath!!).readBytes()).isEqualTo(bytes)
    }
}
