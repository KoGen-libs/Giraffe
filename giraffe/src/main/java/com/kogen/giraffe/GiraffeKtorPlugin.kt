package com.kogen.giraffe

import android.content.Context
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import java.util.UUID

/** Configuration for [GiraffeKtorPlugin] - the [context]/[loggingEnabled] equivalents of [GiraffeInterceptor]'s constructor params, since a client plugin's config is set via a DSL block rather than a constructor. */
class GiraffeKtorPluginConfig {
    lateinit var context: Context
    var loggingEnabled: Boolean = true
}

/**
 * A Ktor client plugin that logs every REST call's method/URL/headers/status, persists it to
 * Giraffe's own database, and surfaces a live notification for it - the Ktor counterpart to
 * [GiraffeOkHttpInterceptor] (for OkHttp/Retrofit), sharing the same [GiraffeRestCallRecorder].
 * Separate from it rather than layered on top: Ktor's client engines (CIO, Darwin, Js, ...) don't
 * go through OkHttp at all, only the OkHttp *engine* does - relying on [GiraffeOkHttpInterceptor]
 * would silently do nothing on any other engine.
 *
 * ```
 * val client = HttpClient(CIO) {
 *     install(GiraffeKtorPlugin) {
 *         context = applicationContext
 *     }
 * }
 * ```
 *
 * **Bodies, for now:** the request body is captured when it's already fully in memory as
 * [OutgoingContent.ByteArrayContent] (true for the overwhelming majority of JSON/form REST
 * bodies) - a genuinely streamed upload isn't, rather than risk interfering with what actually
 * gets sent. The response body is **not captured at all yet** - reading it here without consuming
 * the copy the caller still needs to read requires duplicating Ktor's raw response channel, and
 * the only mechanism found to do that (`HttpResponse.rawContent.split()` /
 * `HttpClientCall.replaceResponse()`, which is exactly how Ktor's own first-party `Logging`/
 * `ResponseObserver` plugins do it) is explicitly marked `@InternalAPI` by Ktor itself ("could be
 * removed or changed without notice") - not something to build a published library's stable
 * feature on. This call/status/headers-only version is a deliberate scope cut, not an oversight;
 * response body support is a documented follow-up once there's a public, stable way to do it (or
 * consumers can additionally install Ktor's own `ResponseObserver` themselves in the meantime).
 */
val GiraffeKtorPlugin = createClientPlugin("GiraffeKtorPlugin", ::GiraffeKtorPluginConfig) {
    val recorder = GiraffeRestCallRecorder(pluginConfig.context, pluginConfig.loggingEnabled)

    on(Send) { request ->
        if (!recorder.isEnabled) return@on proceed(request)

        val callId = UUID.randomUUID().toString()
        val startTimestamp = System.currentTimeMillis()
        val requestHeaders = request.headers.entries().flatMap { (key, values) -> values.map { key to it } }
        val requestContentType = request.contentType()?.toString()
        val requestBody = captureRequestBody(request)
        val url = request.url.buildString()
        val method = request.method.value

        val originalCall = try {
            proceed(request)
        } catch (e: Throwable) {
            recordSafely(
                recorder, callId, url, method, startTimestamp,
                requestHeaders, requestContentType, requestBody,
                emptyList(), null, null, null, e,
            )
            throw e
        }

        val response = originalCall.response
        recordSafely(
            recorder, callId, url, method, startTimestamp,
            requestHeaders, requestContentType, requestBody,
            response.headers.entries().flatMap { (key, values) -> values.map { key to it } },
            response.contentType()?.toString(),
            responseBody = null,
            httpStatusCode = response.status.value,
            error = null,
        )

        originalCall
    }
}

/** Reads bytes from [request]'s body only when it's already fully in memory - see the class KDoc above for why streaming content isn't captured. */
private fun captureRequestBody(request: HttpRequestBuilder): ByteArray? {
    return when (val content = request.body) {
        is OutgoingContent.ByteArrayContent -> try {
            content.bytes()
        } catch (_: Exception) {
            null
        }
        else -> null
    }
}

/** Never let a failure in Giraffe's own bookkeeping propagate into the app's actual HTTP call. */
private fun recordSafely(
    recorder: GiraffeRestCallRecorder,
    callId: String,
    url: String,
    method: String,
    startTimestamp: Long,
    requestHeaders: List<Pair<String, String>>,
    requestContentType: String?,
    requestBody: ByteArray?,
    responseHeaders: List<Pair<String, String>>,
    responseContentType: String?,
    responseBody: ByteArray?,
    httpStatusCode: Int?,
    error: Throwable?,
) {
    try {
        recorder.record(
            callId = callId,
            url = url,
            httpMethod = method,
            startTimestamp = startTimestamp,
            requestHeaders = requestHeaders,
            requestContentType = requestContentType,
            requestBody = requestBody,
            responseHeaders = responseHeaders,
            responseContentType = responseContentType,
            responseBody = responseBody,
            httpStatusCode = httpStatusCode,
            error = error,
        )
    } catch (_: Exception) {
    }
}
