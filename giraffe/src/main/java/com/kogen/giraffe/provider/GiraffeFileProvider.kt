package com.kogen.giraffe.provider

import androidx.core.content.FileProvider

/**
 * Distinct [FileProvider] subclass so Giraffe's `<provider>` doesn't share an `android:name`
 * with the host app's own `androidx.core.content.FileProvider` declaration - AGP's manifest
 * merger keys `<provider>` elements by `android:name`, so two providers using the raw
 * [FileProvider] class (even with different `android:authorities`) are treated as the same
 * element and fail to merge. Subclassing keeps the authority namespacing in
 * AndroidManifest.xml effective. Behavior is unchanged: [FileProvider.getUriForFile] works
 * the same regardless of which subclass is registered under the resolved authority.
 */
class GiraffeFileProvider : FileProvider()
