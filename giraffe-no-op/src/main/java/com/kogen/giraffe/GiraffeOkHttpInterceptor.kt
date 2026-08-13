package com.kogen.giraffe

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response

/** No-op counterpart to the real `GiraffeOkHttpInterceptor` (`io.github.eugenprog:giraffe`) - see `GiraffeInterceptor` in this same module for why this artifact exists and how to wire it in. */
class GiraffeOkHttpInterceptor(
    context: Context,
    loggingEnabled: Boolean = true,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
