package com.kogen.giraffe.ui.common.presentation.extensions

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private const val PROVIDER_AUTHORITY_SUFFIX = ".giraffeprovider"

/**
 * Shares this file via the system share sheet, exposing it through Giraffe's own [FileProvider]
 * rather than a raw `file://` Uri (which [android.os.FileUriExposedException] would reject on
 * API 24+). Defaults to a generic mime type since files of [com.kogen.giraffe.ui.common.domain.models.GiraffeContentType.Unknown]
 * have no reliable one to infer.
 */
internal fun File.shareFile(context: Context, mimeType: String = "*/*") {
    val authority = context.packageName + PROVIDER_AUTHORITY_SUFFIX
    val uri = FileProvider.getUriForFile(context, authority, this)

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val chooser = Intent.createChooser(shareIntent, null)
    if (context !is Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
