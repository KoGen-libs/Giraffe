package com.kogen.giraffe.analizer.parsers

import android.content.Context
import com.kogen.giraffe.analizer.utils.MediaSignatures
import com.kogen.giraffe.analizer.utils.ProtoWireScanner
import com.kogen.giraffe.analizer.utils.saveMediaToCache
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/** Detects PNG/JPEG/GIF/WEBP inside a message's binary leaves by their magic-byte signatures. */
internal class GiraffeImageParser : ContentParser {
    private enum class Format(val extension: String) {
        PNG("png"), JPEG("jpg"), GIF("gif"), WEBP("webp")
    }

    override fun parse(originalBytes: ByteArray, context: Context): ParserResult? {
        for (leaf in ProtoWireScanner().findBinaryLeaves(originalBytes)) {
            val format = matchFormatAtStart(leaf) ?: continue

            val endIndex = when (format) {
                Format.PNG -> MediaSignatures.findEndOfMedia(leaf, 0, MediaSignatures.PNG_END)
                Format.JPEG -> MediaSignatures.findLastEndOfMedia(leaf, 0, MediaSignatures.JPEG_END)
                Format.GIF -> MediaSignatures.findEndOfMedia(leaf, 0, MediaSignatures.GIF_END)
                Format.WEBP -> MediaSignatures.findRiffEnd(leaf, 0)
            }.let { if (it == -1) leaf.size else it }

            val chunk = leaf.copyOfRange(0, endIndex)
            val path = saveMediaToCache(context, chunk, "img", format.extension)

            path?.let {
                return ParserResult(contentType = GiraffeContentType.Image, filePath = it, bytes = chunk)
            }
        }
        return null
    }

    /** Identifies the image format from the magic bytes at the very start of [bytes], or `null` if none match. */
    private fun matchFormatAtStart(bytes: ByteArray): Format? = when {
        MediaSignatures.matchesAt(bytes, 0, MediaSignatures.PNG) -> Format.PNG
        MediaSignatures.matchesAt(bytes, 0, MediaSignatures.JPEG) -> Format.JPEG
        MediaSignatures.matchesAt(bytes, 0, MediaSignatures.GIF) -> Format.GIF
        MediaSignatures.matchesAt(bytes, 0, MediaSignatures.WEBP) &&
            MediaSignatures.matchesAt(bytes, 8, MediaSignatures.WEBP_TAG) -> Format.WEBP
        else -> null
    }
}
