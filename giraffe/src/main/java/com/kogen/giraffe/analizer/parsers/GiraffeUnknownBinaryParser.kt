package com.kogen.giraffe.analizer.parsers

import android.content.Context
import com.kogen.giraffe.analizer.utils.ProtoWireScanner
import com.kogen.giraffe.analizer.utils.saveMediaToCache
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/**
 * Fallback parser tried last: extracts the first opaque binary leaf found in a message even
 * though its format couldn't be identified, so unrecognized media still gets saved to a file
 * (as `Unknown`) instead of being dumped inline as an unreadable escaped byte string.
 */
internal class GiraffeUnknownBinaryParser : ContentParser {
    override fun parse(originalBytes: ByteArray, context: Context): ParserResult? {
        val candidate = ProtoWireScanner().findBinaryLeaves(originalBytes).firstOrNull()
            ?: return null

        val path = saveMediaToCache(context, candidate, "unknown", "bin")
        return ParserResult(
            contentType = GiraffeContentType.Unknown,
            bytes = candidate,
            filePath = path,
        )
    }
}