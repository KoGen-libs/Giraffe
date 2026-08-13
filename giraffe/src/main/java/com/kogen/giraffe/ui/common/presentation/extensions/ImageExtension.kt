package com.kogen.giraffe.ui.common.presentation.extensions

import android.graphics.BitmapFactory

/**
 * Reads just the image dimensions at [path] (via `inJustDecodeBounds`, without allocating a
 * bitmap) to size a message's image bubble before the full image has loaded. Returns `null` if
 * the file isn't a decodable image.
 */
internal fun decodeImageAspectRatio(path: String): Float? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return options.outWidth.toFloat() / options.outHeight.toFloat()
}