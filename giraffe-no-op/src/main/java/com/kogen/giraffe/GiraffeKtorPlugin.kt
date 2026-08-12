package com.kogen.giraffe

import android.content.Context
import io.ktor.client.plugins.api.createClientPlugin

/** No-op counterpart to the real `GiraffeKtorPluginConfig` (`io.github.eugenprog:giraffe`) - same shape, unused here. */
class GiraffeKtorPluginConfig {
    lateinit var context: Context
    var loggingEnabled: Boolean = true
}

/** No-op counterpart to the real `GiraffeKtorPlugin` (`io.github.eugenprog:giraffe`) - installs with no hooks, so it does nothing at all. See `GiraffeInterceptor` in this same module for why this artifact exists and how to wire it in. */
val GiraffeKtorPlugin = createClientPlugin("GiraffeKtorPlugin", ::GiraffeKtorPluginConfig) {
    // Intentionally no hooks registered.
}
