package com.kogen.giraffe

import android.content.Context
import io.ktor.client.HttpClientConfig

/** No-op counterpart to the real `installGiraffeKtor` (`io.github.eugenprog:giraffe`) - installs nothing at all. See `GiraffeInterceptor` in this same module for why this artifact exists and how to wire it in. */
fun HttpClientConfig<*>.installGiraffeKtor(context: Context, loggingEnabled: Boolean = true) {
}
