package com.kogen.giraffe.analizer.parsers

import android.content.Context
import com.kogen.giraffe.analizer.utils.MediaSignatures
import com.kogen.giraffe.analizer.utils.Mp3FrameSync
import com.kogen.giraffe.analizer.utils.PcmAudioHeuristics
import com.kogen.giraffe.analizer.utils.saveMediaToCache
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/**
 * Detects MP3 (via chained frame-sync validation) or WAV (via its RIFF/WAVE signature) inside a
 * message's binary leaves, and falls back to a statistical PCM16 heuristic - wrapping the result
 * in a synthetic WAV header - for headerless raw audio that carries no magic bytes at all.
 */
internal class GiraffeAudioParser : ContentParser {
    private enum class Format(val extension: String) {
        MP3("mp3"), WAV("wav")
    }

    private data class Match(val start: Int, val format: Format)

    override fun parse(leaves: List<ByteArray>, context: Context): ParserResult? {
        for (leaf in leaves) {
            val match = findEarliestMatch(leaf) ?: continue

            val endIndex = when (match.format) {
                Format.MP3 -> leaf.size
                Format.WAV -> MediaSignatures.findRiffEnd(leaf, match.start)
                    .let { if (it == -1) leaf.size else it }
            }

            val chunk = leaf.copyOfRange(match.start, endIndex)
            val path = saveMediaToCache(context, chunk, "audio", match.format.extension)

            path?.let {
                return ParserResult(
                    contentType = GiraffeContentType.Audio,
                    filePath = it,
                    bytes = chunk
                )
            }
        }

        val pcm = leaves.firstOrNull { PcmAudioHeuristics.looksLikePcm16(it) } ?: return null

        val wavBytes = PcmAudioHeuristics.wrapAsWav(pcm)
        val path = saveMediaToCache(context, wavBytes, "audio_pcm", "wav")

        return path?.let {
            ParserResult(contentType = GiraffeContentType.Audio, filePath = it, bytes = pcm)
        }
    }

    /** Picks whichever of MP3/WAV starts earliest in [bytes], in case both signatures happen to be present. */
    private fun findEarliestMatch(bytes: ByteArray): Match? {
        val candidates = listOfNotNull(
            Mp3FrameSync.findValidatedStart(bytes)?.let { Match(it, Format.MP3) },
            if (MediaSignatures.matchesAt(bytes, 0, MediaSignatures.WAV) &&
                MediaSignatures.matchesAt(bytes, 8, MediaSignatures.WAVE_TAG)
            ) {
                Match(0, Format.WAV)
            } else {
                null
            },
        )
        return candidates.minByOrNull { it.start }
    }
}
