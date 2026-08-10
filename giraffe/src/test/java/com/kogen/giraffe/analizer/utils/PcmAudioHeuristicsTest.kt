package com.kogen.giraffe.analizer.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class PcmAudioHeuristicsTest {

    /** A synthetic 16-bit signed PCM sine wave, packed little-endian like real LINEAR16 audio. */
    private fun sineWavePcm16(sampleCount: Int, amplitude: Int = 12_000, samplesPerCycle: Double = 50.0): ByteArray {
        val buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val sample = (amplitude * sin(2.0 * Math.PI * i / samplesPerCycle)).toInt()
            buffer.putShort(sample.toShort())
        }
        return buffer.array()
    }

    @Test
    fun `looksLikePcm16 accepts a plausible speech-like sine wave`() {
        assertThat(PcmAudioHeuristics.looksLikePcm16(sineWavePcm16(sampleCount = 400))).isTrue()
    }

    @Test
    fun `looksLikePcm16 rejects an odd-length buffer`() {
        assertThat(PcmAudioHeuristics.looksLikePcm16(ByteArray(321))).isFalse()
    }

    @Test
    fun `looksLikePcm16 rejects buffers shorter than the minimum sample count`() {
        assertThat(PcmAudioHeuristics.looksLikePcm16(sineWavePcm16(sampleCount = 50))).isFalse()
    }

    @Test
    fun `looksLikePcm16 rejects digital silence`() {
        assertThat(PcmAudioHeuristics.looksLikePcm16(ByteArray(400))).isFalse()
    }

    @Test
    fun `looksLikePcm16 rejects plain UTF-8 text of matching size`() {
        val text = "x".repeat(400).toByteArray()

        assertThat(PcmAudioHeuristics.looksLikePcm16(text)).isFalse()
    }

    @Test
    fun `looksLikePcm16 rejects a minimal, uncompressed PDF reinterpreted as samples`() {
        // Regression test: a PDF this plain is exactly what tricked ProtoWireScanner into
        // treating it as ordinary text before (see ProtoWireScannerTest); it must still get
        // rejected by the *audio* heuristic even now that the scanner lets it through as a leaf,
        // or GiraffeAudioParser ends up claiming it before GiraffePdfParser ever sees it.
        val pdfLine = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        val pdf = ("%PDF-1.4\n" + pdfLine.repeat(20) + "%%EOF\n").toByteArray()
        val evenLengthPdf = if (pdf.size % 2 == 0) pdf else pdf + byteArrayOf(0x0A)

        assertThat(PcmAudioHeuristics.looksLikePcm16(evenLengthPdf)).isFalse()
    }

    @Test
    fun `looksLikePcm16 rejects a heavily clipped signal`() {
        val sampleCount = 400
        val buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleCount) { i ->
            // Alternate between the two clipping rails - way above the 2% clip-ratio budget.
            buffer.putShort(if (i % 2 == 0) 32700.toShort() else (-32700).toShort())
        }

        assertThat(PcmAudioHeuristics.looksLikePcm16(buffer.array())).isFalse()
    }

    @Test
    fun `wrapAsWav produces a well-formed 44-byte RIFF-WAVE header`() {
        val pcm = sineWavePcm16(sampleCount = 100)

        val wav = PcmAudioHeuristics.wrapAsWav(pcm, sampleRate = 16000, channels = 1, bitsPerSample = 16)

        assertThat(wav.size).isEqualTo(44 + pcm.size)
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertThat(String(wav, 0, 4, Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(buffer.getInt(4)).isEqualTo(36 + pcm.size)
        assertThat(String(wav, 8, 4, Charsets.US_ASCII)).isEqualTo("WAVE")
        assertThat(String(wav, 12, 4, Charsets.US_ASCII)).isEqualTo("fmt ")
        assertThat(buffer.getInt(16)).isEqualTo(16) // fmt chunk size
        assertThat(buffer.getShort(20).toInt()).isEqualTo(1) // PCM format tag
        assertThat(buffer.getShort(22).toInt()).isEqualTo(1) // channels
        assertThat(buffer.getInt(24)).isEqualTo(16000) // sample rate
        assertThat(buffer.getInt(28)).isEqualTo(32000) // byte rate = 16000 * 1 * 16 / 8
        assertThat(buffer.getShort(32).toInt()).isEqualTo(2) // block align
        assertThat(buffer.getShort(34).toInt()).isEqualTo(16) // bits per sample
        assertThat(String(wav, 36, 4, Charsets.US_ASCII)).isEqualTo("data")
        assertThat(buffer.getInt(40)).isEqualTo(pcm.size)
        assertThat(wav.copyOfRange(44, wav.size)).isEqualTo(pcm)
    }
}
