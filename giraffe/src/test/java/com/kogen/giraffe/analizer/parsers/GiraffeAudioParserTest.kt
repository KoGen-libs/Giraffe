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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class GiraffeAudioParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = GiraffeAudioParser()

    // One valid MPEG-1 Layer III / 128kbps / 44100Hz frame header, matching Mp3FrameSyncTest.
    private val mp3FrameLength = 144 * 128_000 / 44_100
    private fun mp3Frame(): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00) + ByteArray(mp3FrameLength - 4)

    private fun littleEndianInt(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    @Test
    fun `finds an MP3 frame chain and trims garbage that precedes it`() {
        val garbagePrefix = byteArrayOf(1, 2, 3, 4, 5, 6)
        val mp3Data = mp3Frame() + mp3Frame() + mp3Frame()
        val leaf = garbagePrefix + mp3Data
        val originalBytes = lengthDelimitedField(fieldNumber = 4, payload = leaf)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Audio)
        assertThat(result.bytes).isEqualTo(mp3Data)
        val savedFile = File(result.filePath!!)
        assertThat(savedFile.readBytes()).isEqualTo(mp3Data)
        assertThat(savedFile.extension).isEqualTo("mp3")
    }

    @Test
    fun `extracts a WAV chunk and trims trailing noise past the declared RIFF size`() {
        val body = ByteArray(24) { 0 }
        val riffChunkSize = 4 + body.size // "WAVE" tag + body, per the RIFF chunk-size convention
        val wavBytes = "RIFF".toByteArray() + littleEndianInt(riffChunkSize) + "WAVE".toByteArray() + body
        val leaf = wavBytes + ByteArray(5) { 0x5A }
        val originalBytes = lengthDelimitedField(fieldNumber = 4, payload = leaf)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Audio)
        assertThat(result.bytes).isEqualTo(wavBytes)
        val savedFile = File(result.filePath!!)
        assertThat(savedFile.readBytes()).isEqualTo(wavBytes)
        assertThat(savedFile.extension).isEqualTo("wav")
    }

    @Test
    fun `falls back to the headerless-PCM heuristic and wraps the result as WAV`() {
        val sampleCount = 400
        val buffer = ByteBuffer.allocate(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            buffer.putShort((12_000 * sin(2.0 * Math.PI * i / 50.0)).toInt().toShort())
        }
        val pcm = buffer.array()
        val originalBytes = lengthDelimitedField(fieldNumber = 4, payload = pcm)
        val context = fakeContext(tempFolder.root)

        val result = parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)

        assertThat(result).isNotNull()
        assertThat(result!!.contentType).isEqualTo(GiraffeContentType.Audio)
        assertThat(result.bytes).isEqualTo(pcm)
        val savedFile = File(result.filePath!!)
        assertThat(savedFile.extension).isEqualTo("wav")
        // The saved file is the PCM wrapped in a 44-byte WAV header, not the bare samples.
        assertThat(savedFile.readBytes().size).isEqualTo(44 + pcm.size)
        assertThat(savedFile.readBytes().copyOfRange(44, 44 + pcm.size)).isEqualTo(pcm)
    }

    @Test
    fun `returns null for a leaf that matches no audio format`() {
        val originalBytes = lengthDelimitedField(fieldNumber = 4, payload = ByteArray(30) { (it % 5).toByte() })
        val context = fakeContext(tempFolder.root)

        assertThat(parser.parse(ProtoWireScanner().findBinaryLeaves(originalBytes), context)).isNull()
    }
}
