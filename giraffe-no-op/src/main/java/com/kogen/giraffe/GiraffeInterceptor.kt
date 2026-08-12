package com.kogen.giraffe

import android.content.Context
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.MethodDescriptor

/**
 * No-op counterpart to the real `GiraffeInterceptor` (`io.github.eugenprog:giraffe`): same class
 * name, package, and constructor, but [interceptCall] does nothing but forward to [next] - no
 * logging, no database, no notification.
 *
 * The point of publishing this as its own artifact rather than just gating the real one behind
 * `if (BuildConfig.DEBUG)` at the call site: wire both in via build-variant-specific
 * configurations -
 * ```
 * debugImplementation("io.github.eugenprog:giraffe:<version>")
 * releaseImplementation("io.github.eugenprog:giraffe-no-op:<version>")
 * ```
 * - and the exact same line, `channel.intercept(GiraffeInterceptor(context))`, compiles unchanged
 * for both variants. Nothing needs conditional wiring, and a release build never pulls in the real
 * module's dependencies (Room, Compose, media3, Coil) at all - not just unused at runtime, but
 * absent from the APK.
 *
 * @param context unused - present only to keep this constructor's signature identical to the real
 * `GiraffeInterceptor`'s.
 * @param loggingEnabled unused, for the same reason.
 */
class GiraffeInterceptor(
    context: Context,
    loggingEnabled: Boolean = true,
) : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel,
    ): ClientCall<ReqT, RespT> = next.newCall(method, callOptions)
}
