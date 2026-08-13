package com.kogen.giraffe.ui.common.presentation.extensions

import android.media.MediaMetadataRetriever

/**
 * Mirrors [decodeImageAspectRatio], but for video files - used to size a video message's
 * thumbnail bubble before the frame itself has finished decoding.
 */
internal fun decodeVideoAspectRatio(path: String): Float? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (width == null || height == null || width <= 0f || height <= 0f) {
            null
        } else if (rotation == 90 || rotation == 270) {
            height / width
        } else {
            width / height
        }
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}
