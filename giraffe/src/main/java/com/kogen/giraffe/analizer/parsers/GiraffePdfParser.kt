package com.kogen.giraffe.analizer.parsers

import android.content.Context
import com.kogen.giraffe.analizer.utils.MediaSignatures
import com.kogen.giraffe.analizer.utils.saveMediaToCache
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/**
 * Detects PDF inside a message's binary leaves by its `%PDF-` header, bounded by the *last*
 * `%%EOF` marker - incrementally-updated PDFs can contain more than one (one per revision), and
 * only the final one marks the true end of the file.
 */
internal class GiraffePdfParser : ContentParser {
    override fun parse(leaves: List<ByteArray>, context: Context): ParserResult? {
        for (leaf in leaves) {
            if (!MediaSignatures.matchesAt(leaf, 0, MediaSignatures.PDF)) continue

            val endIndex = MediaSignatures.findLastEndOfMedia(leaf, 0, MediaSignatures.PDF_EOF)
                .let { if (it == -1) leaf.size else it }
            val chunk = leaf.copyOfRange(0, endIndex)
            val path = saveMediaToCache(context, chunk, "pdf", "pdf")

            path?.let {
                return ParserResult(contentType = GiraffeContentType.Pdf, filePath = it, bytes = chunk)
            }
        }
        return null
    }
}
