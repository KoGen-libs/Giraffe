package com.kogen.giraffe.analizer.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Statistical classifier for headerless LINEAR16 PCM audio (no magic bytes exist for raw PCM,
 * so detection can only ever be probabilistic — tuned to separate real speech/audio signal
 * from arbitrary protobuf binary payloads, not to guarantee a match).
 */
internal object PcmAudioHeuristics {
    private const val MIN_SAMPLES = 160
    private const val MIN_STD_DEV = 80.0
    private const val MAX_CLIP_RATIO = 0.02
    private const val MAX_ZERO_RATIO = 0.5
    private const val MAX_DELTA_TO_STD_RATIO = 2.5
    private const val CLIP_THRESHOLD = 32000

    /**
     * Scores [bytes] as likely-or-not headerless LINEAR16 PCM by checking sample statistics
     * (standard deviation, clipping, silence, and sample-to-sample smoothness) against thresholds
     * tuned to reject text/structured binary while accepting real speech/audio.
     */
    fun looksLikePcm16(bytes: ByteArray): Boolean {
        if (bytes.size % 2 != 0) return false
        val sampleCount = bytes.size / 2
        if (sampleCount < MIN_SAMPLES) return false
        if (MediaSignatures.isLikelyUtf8Text(bytes)) return false

        var sum = 0.0
        var sumSq = 0.0
        var zeroCount = 0
        var clipCount = 0
        var deltaSum = 0.0
        var prev = 0

        for (i in 0 until sampleCount) {
            val lo = bytes[i * 2].toInt() and 0xFF
            val hi = bytes[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo

            sum += sample
            sumSq += sample.toDouble() * sample
            if (sample == 0) zeroCount++
            if (abs(sample) >= CLIP_THRESHOLD) clipCount++
            if (i > 0) deltaSum += abs(sample - prev)
            prev = sample
        }

        val mean = sum / sampleCount
        val variance = (sumSq / sampleCount) - mean * mean
        val stdDev = sqrt(variance.coerceAtLeast(0.0))
        val zeroRatio = zeroCount.toDouble() / sampleCount
        val clipRatio = clipCount.toDouble() / sampleCount
        val meanAbsDelta = deltaSum / (sampleCount - 1)

        return stdDev >= MIN_STD_DEV &&
            clipRatio <= MAX_CLIP_RATIO &&
            zeroRatio <= MAX_ZERO_RATIO &&
            meanAbsDelta <= stdDev * MAX_DELTA_TO_STD_RATIO
    }

    /** Wraps raw PCM in a standard 44-byte WAV header so it can be handed to MediaPlayer. */
    fun wrapAsWav(
        pcm: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }.array()

        return header + pcm
    }
}
