package com.kogen.giraffe.analizer

import android.content.Context
import com.kogen.giraffe.analizer.utils.MediaSignatures
import com.kogen.giraffe.analizer.utils.Mp3FrameSync
import com.kogen.giraffe.analizer.utils.saveMediaToCache
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

private const val MAX_DB_TEXT_LENGTH = 500_000

// Same floor and reasoning as GiraffeUnknownBinaryParser: below this, an opaque blob with no
// useful Content-Type is far more likely to be a stray token/id than an actual attachment.
private const val MIN_UNKNOWN_BODY_SIZE = 128

/**
 * Classifies one REST request/response body - unlike [GiraffeMessageAnalyzer], which has to
 * infer everything by sniffing raw protobuf bytes (there's no equivalent of a `Content-Type`
 * header inside a gRPC message), this starts from the declared `Content-Type` and only falls
 * back to magic-byte sniffing (reusing the exact same [MediaSignatures] the gRPC parsers use)
 * when that header is missing or too generic to say anything ([classify] returns
 * [GiraffeContentType.Unknown]).
 */
internal class GiraffeRestBodyAnalyzer {

    fun analyze(contentType: String?, bytes: ByteArray, context: Context): AnalysisResult {
        val declared = classify(contentType)
        val resolved = if (declared == GiraffeContentType.Unknown) {
            sniffBytes(bytes)
        } else {
            verifyAgainstBytes(declared, bytes)
        }

        return when (resolved) {
            GiraffeContentType.Image, GiraffeContentType.Audio, GiraffeContentType.Video, GiraffeContentType.Pdf -> {
                val extension = extensionFrom(contentType, fallback = resolved.name.lowercase())
                val path = saveMediaToCache(context, bytes, resolved.name.lowercase(), extension)
                AnalysisResult(contentType = resolved, textContent = null, filePath = path)
            }

            GiraffeContentType.Json, GiraffeContentType.PlainText -> AnalysisResult(
                contentType = resolved,
                textContent = truncateForDb(decodeAsTextOrNull(bytes)),
                filePath = null,
            )

            GiraffeContentType.Unknown -> {
                if (bytes.size >= MIN_UNKNOWN_BODY_SIZE) {
                    AnalysisResult(
                        contentType = GiraffeContentType.Unknown,
                        textContent = null,
                        filePath = saveMediaToCache(context, bytes, "unknown", "bin"),
                    )
                } else {
                    // Decoding never throws on malformed UTF-8 (it just substitutes replacement
                    // characters), so gate on isLikelyUtf8Text first - otherwise genuinely binary
                    // noise would still show up as garbled "text" instead of nothing.
                    val text = if (MediaSignatures.isLikelyUtf8Text(bytes)) decodeAsTextOrNull(bytes) else null
                    AnalysisResult(
                        contentType = GiraffeContentType.Unknown,
                        textContent = truncateForDb(text),
                        filePath = null,
                    )
                }
            }
        }
    }

    /** Maps a `Content-Type` header value to the [GiraffeContentType] family it declares, or [GiraffeContentType.Unknown] if it's missing or doesn't say anything useful. */
    private fun classify(contentType: String?): GiraffeContentType {
        val type = contentType?.substringBefore(";")?.trim()?.lowercase() ?: return GiraffeContentType.Unknown
        return when {
            type.startsWith("image/") -> GiraffeContentType.Image
            type.startsWith("audio/") -> GiraffeContentType.Audio
            type.startsWith("video/") -> GiraffeContentType.Video
            type == "application/pdf" -> GiraffeContentType.Pdf
            type == "application/json" || type.endsWith("+json") -> GiraffeContentType.Json
            type.startsWith("text/") -> GiraffeContentType.PlainText
            else -> GiraffeContentType.Unknown
        }
    }

    /**
     * Confirms [declared] against [bytes]' magic signature for [GiraffeContentType.Image]/[GiraffeContentType.Pdf]
     * - downgrading to [GiraffeContentType.Unknown] on a mismatch, since [MediaSignatures]' PNG/JPEG/GIF/WEBP and
     * PDF signatures already cover the near-totality of what a real API actually returns for those two, so a miss
     * is more likely a wrong header than a format we simply don't recognize.
     *
     * [GiraffeContentType.Audio]/[GiraffeContentType.Video] are deliberately NOT downgraded on a miss: our
     * signature set only covers MP3/WAV and MP4 respectively, nowhere near every real container/codec (ogg, aac,
     * webm, avi, ...), so a miss there is far more likely "a format we don't have a signature for" than a wrong
     * header - trust the header rather than manufacture a false negative from an incomplete signature list.
     * [GiraffeContentType.Json]/[GiraffeContentType.PlainText] have no bytes-level signature to check at all.
     */
    private fun verifyAgainstBytes(declared: GiraffeContentType, bytes: ByteArray): GiraffeContentType {
        return when (declared) {
            GiraffeContentType.Image -> if (looksLikeImage(bytes)) declared else GiraffeContentType.Unknown
            GiraffeContentType.Pdf -> if (MediaSignatures.matchesAt(bytes, 0, MediaSignatures.PDF)) declared else GiraffeContentType.Unknown
            else -> declared
        }
    }

    /** Falls back to sniffing [bytes] directly (same signatures the gRPC-side parsers use) when there was no usable `Content-Type` to go on. */
    private fun sniffBytes(bytes: ByteArray): GiraffeContentType {
        return when {
            looksLikeImage(bytes) -> GiraffeContentType.Image
            MediaSignatures.matchesAt(bytes, 0, MediaSignatures.PDF) -> GiraffeContentType.Pdf
            MediaSignatures.matchesAt(bytes, 4, MediaSignatures.MP4_FTYP) -> GiraffeContentType.Video
            looksLikeKnownAudio(bytes) -> GiraffeContentType.Audio
            isJsonShaped(bytes) -> GiraffeContentType.Json
            MediaSignatures.isLikelyUtf8Text(bytes) -> GiraffeContentType.PlainText
            else -> GiraffeContentType.Unknown
        }
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean =
        MediaSignatures.matchesAt(bytes, 0, MediaSignatures.PNG) ||
            MediaSignatures.matchesAt(bytes, 0, MediaSignatures.JPEG) ||
            MediaSignatures.matchesAt(bytes, 0, MediaSignatures.GIF) ||
            (MediaSignatures.matchesAt(bytes, 0, MediaSignatures.WEBP) &&
                MediaSignatures.matchesAt(bytes, 8, MediaSignatures.WEBP_TAG))

    private fun looksLikeKnownAudio(bytes: ByteArray): Boolean =
        (MediaSignatures.matchesAt(bytes, 0, MediaSignatures.WAV) &&
            MediaSignatures.matchesAt(bytes, 8, MediaSignatures.WAVE_TAG)) ||
            Mp3FrameSync.findValidatedStart(bytes) != null

    private fun isJsonShaped(bytes: ByteArray): Boolean {
        val text = decodeAsTextOrNull(bytes)?.trim() ?: return false
        return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))
    }

    /** Derives a filename extension from the Content-Type's subtype (`"image/svg+xml"` -> `"svg+xml"` sanitized to `"svg-xml"`), or [fallback] if there's nothing usable. */
    private fun extensionFrom(contentType: String?, fallback: String): String {
        val subtype = contentType?.substringBefore(";")?.substringAfter("/", "")?.trim()?.lowercase()
        if (subtype.isNullOrBlank()) return fallback
        val sanitized = subtype.map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '-' }.joinToString("")
        return sanitized.ifBlank { fallback }
    }

    private fun decodeAsTextOrNull(bytes: ByteArray): String? {
        return try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun truncateForDb(text: String?, maxLength: Int = MAX_DB_TEXT_LENGTH): String? {
        return when {
            text == null -> null
            text.length <= maxLength -> text
            else -> text.substring(0, maxLength)
        }
    }
}
