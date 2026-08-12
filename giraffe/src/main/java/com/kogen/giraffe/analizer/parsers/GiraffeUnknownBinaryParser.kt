package com.kogen.giraffe.analizer.parsers

import android.content.Context
import com.kogen.giraffe.analizer.utils.saveMediaToCache
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/**
 * Fallback parser tried last: extracts the first opaque binary leaf found in a message even
 * though its format couldn't be identified, so unrecognized media still gets saved to a file
 * (as `Unknown`) instead of being dumped inline as an unreadable escaped byte string.
 */
internal class GiraffeUnknownBinaryParser : ContentParser {

    companion object {
        // Unlike the other parsers, this one has no signature to verify a leaf against - it
        // treats any sufficiently large opaque binary blob as "some unidentified file". The
        // shared findBinaryLeaves() floor (17 bytes, applied once upstream for every parser) is
        // far too low for that: a random UUID, hash, or token - routinely present in real traffic
        // and often wrapped in a nested single-field message, pushing it a few bytes over 17 - is
        // not a "file" and shouldn't render a file chip. Real media is already caught earlier in
        // the pipeline by a signature-matching parser, so filtering more strictly here only
        // affects genuinely unidentified content, not legitimate attachments.
        private const val MIN_UNKNOWN_LEAF_SIZE = 128
    }

    override fun parse(leaves: List<ByteArray>, context: Context): ParserResult? {
        val candidate = leaves.firstOrNull { it.size >= MIN_UNKNOWN_LEAF_SIZE } ?: return null

        val path = saveMediaToCache(context, candidate, "unknown", "bin")
        return ParserResult(
            contentType = GiraffeContentType.Unknown,
            bytes = candidate,
            filePath = path,
        )
    }
}