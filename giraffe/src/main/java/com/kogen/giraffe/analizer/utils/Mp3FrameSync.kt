package com.kogen.giraffe.analizer.utils

/**
 * Finds a genuine MP3 stream inside arbitrary binary data by validating that a candidate frame
 * header is followed by a chain of further valid frame headers - MP3's `0xFF Ex` sync pattern
 * alone is common enough in random binary that a single match isn't reliable evidence of audio.
 */
internal object Mp3FrameSync {
    private val bitrateV1L1 =
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, -1)
    private val bitrateV1L2 =
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, -1)
    private val bitrateV1L3 =
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1)
    private val bitrateV2L1 =
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, -1)
    private val bitrateV2L23 =
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1)

    private val sampleRateV1 = intArrayOf(44100, 48000, 32000, -1)
    private val sampleRateV2 = intArrayOf(22050, 24000, 16000, -1)
    private val sampleRateV25 = intArrayOf(11025, 12000, 8000, -1)

    /** Parses an MPEG audio frame header at [offset] and computes its length in bytes, or `null` if the header is invalid/reserved. */
    private fun frameLength(bytes: ByteArray, offset: Int): Int? {
        if (offset + 4 > bytes.size) return null
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF

        if ((bytes[offset].toInt() and 0xFF) != 0xFF) return null
        if ((b1 and 0xE0) != 0xE0) return null

        val versionBits = (b1 shr 3) and 0x3
        val layerBits = (b1 shr 1) and 0x3
        if (versionBits == 1 || layerBits == 0) return null

        val bitrateIndex = (b2 shr 4) and 0xF
        val sampleRateIndex = (b2 shr 2) and 0x3
        val paddingBit = (b2 shr 1) and 0x1
        if (bitrateIndex == 0xF || bitrateIndex == 0 || sampleRateIndex == 0x3) return null

        val sampleRate = when (versionBits) {
            3 -> sampleRateV1[sampleRateIndex]
            2 -> sampleRateV2[sampleRateIndex]
            else -> sampleRateV25[sampleRateIndex]
        }
        if (sampleRate <= 0) return null

        val table = when {
            versionBits == 3 && layerBits == 3 -> bitrateV1L1
            versionBits == 3 && layerBits == 2 -> bitrateV1L2
            versionBits == 3 -> bitrateV1L3
            layerBits == 3 -> bitrateV2L1
            layerBits == 2 -> bitrateV2L23
            else -> bitrateV2L23
        }
        val bitrateKbps = table.getOrNull(bitrateIndex) ?: return null
        if (bitrateKbps <= 0) return null
        val bitrate = bitrateKbps * 1000

        val length = when (layerBits) {
            3 -> (12 * bitrate / sampleRate + paddingBit) * 4
            2 -> 144 * bitrate / sampleRate + paddingBit
            else -> if (versionBits == 3) {
                144 * bitrate / sampleRate + paddingBit
            } else {
                72 * bitrate / sampleRate + paddingBit
            }
        }
        return length.takeIf { it > 0 }
    }

    /**
     * Scans [bytes] for the first sync pattern that's followed by at least [minChainedFrames]
     * consecutive valid frames (each frame's computed length correctly leading into the next
     * frame's header), and returns its offset - or `null` if no such chain exists.
     */
    fun findValidatedStart(bytes: ByteArray, minChainedFrames: Int = 3): Int? {
        var i = 0
        while (i < bytes.size - 4) {
            if ((bytes[i].toInt() and 0xFF) == 0xFF && (bytes[i + 1].toInt() and 0xE0) == 0xE0) {
                var pos = i
                var count = 0
                while (count < minChainedFrames) {
                    val len = frameLength(bytes, pos) ?: break
                    pos += len
                    count++
                    if (pos >= bytes.size) break
                }
                if (count >= minChainedFrames) {
                    return i
                }
            }
            i++
        }
        return null
    }
}