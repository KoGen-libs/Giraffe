package com.kogen.giraffe.analizer.parsers

import android.content.Context
import com.kogen.giraffe.analizer.utils.MediaSignatures
import com.kogen.giraffe.analizer.utils.ProtoWireScanner
import com.kogen.giraffe.analizer.utils.saveMediaToCache
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/** Detects MP4 inside a message's binary leaves by its `ftyp` box signature at offset 4. */
internal class GiraffeVideoParser : ContentParser {
    override fun parse(originalBytes: ByteArray, context: Context): ParserResult? {
        for (leaf in ProtoWireScanner().findBinaryLeaves(originalBytes)) {
            if (!MediaSignatures.matchesAt(leaf, 4, MediaSignatures.MP4_FTYP)) continue

            val endIndex = MediaSignatures.findMp4End(leaf, 4).let { if (it == -1) leaf.size else it }
            val chunk = leaf.copyOfRange(0, endIndex)
            val path = saveMediaToCache(context, chunk, "video", "mp4")

            path?.let {
                return ParserResult(contentType = GiraffeContentType.Video, filePath = it, bytes = chunk)
            }
        }
        return null
    }
}
