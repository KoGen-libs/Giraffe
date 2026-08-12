package com.kogen.giraffe

import android.content.Context
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.UUID

// Response bodies are captured via Response.peekBody(), which buffers up to this many bytes into
// memory without disturbing the stream the app itself still reads afterward - see intercept()'s
// KDoc for why this is capped rather than reading the whole thing.
private const val MAX_PEEK_BYTES = 20L * 1024 * 1024

/**
 * An OkHttp [Interceptor] that logs every REST call's headers and body, persists them to
 * Giraffe's own database, and surfaces a live notification for it - the REST counterpart to
 * [GiraffeInterceptor]. Works for Retrofit too, since Retrofit is itself built on OkHttp.
 *
 * Attach it to the [okhttp3.OkHttpClient] you want to inspect:
 * ```
 * OkHttpClient.Builder()
 *     .addInterceptor(GiraffeOkHttpInterceptor(context))
 *     .build()
 * ```
 *
 * @param context used to bootstrap Giraffe's DI graph and database, and to read whether the host
 * app is debuggable - see [GiraffeRestCallRecorder.isEnabled]. A short-lived `Activity` context is
 * safe to pass here; the application context is retained internally.
 * @param loggingEnabled when `false`, suppresses this interceptor's own `Log.d` output while
 * still recording traffic to the database and notifications.
 */
class GiraffeOkHttpInterceptor(
    context: Context,
    loggingEnabled: Boolean = true,
) : Interceptor {

    private val recorder = GiraffeRestCallRecorder(context, loggingEnabled)

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!recorder.isEnabled) return chain.proceed(request)

        val callId = UUID.randomUUID().toString()
        val startTimestamp = System.currentTimeMillis()
        val requestHeaders = request.headers.toPairs()
        val requestContentType = request.body?.contentType()?.toString()
        val requestBody = readRequestBody(request)

        try {
            val response = chain.proceed(request)

            record(
                callId = callId,
                request = request,
                startTimestamp = startTimestamp,
                requestHeaders = requestHeaders,
                requestContentType = requestContentType,
                requestBody = requestBody,
                responseHeaders = response.headers.toPairs(),
                responseContentType = response.body?.contentType()?.toString(),
                responseBody = peekResponseBody(response),
                httpStatusCode = response.code,
                error = null,
            )

            return response
        } catch (e: IOException) {
            record(
                callId = callId,
                request = request,
                startTimestamp = startTimestamp,
                requestHeaders = requestHeaders,
                requestContentType = requestContentType,
                requestBody = requestBody,
                responseHeaders = emptyList(),
                responseContentType = null,
                responseBody = null,
                httpStatusCode = null,
                error = e,
            )
            throw e
        }
    }

    private fun record(
        callId: String,
        request: Request,
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
        // Never let a failure in Giraffe's own bookkeeping take down the real request/response
        // this interceptor is wrapping.
        try {
            recorder.record(
                callId = callId,
                url = request.url.toString(),
                httpMethod = request.method,
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

    /** Buffers [request]'s body into memory without consuming it - the same `writeTo(Buffer())` trick OkHttp's own logging interceptor uses, so the real network write that happens later is untouched. */
    private fun readRequestBody(request: Request): ByteArray? {
        val body = request.body ?: return null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Peeks up to [MAX_PEEK_BYTES] of [response]'s body without disturbing the stream the app
     * itself still reads afterward. Capped rather than reading the whole thing: an uncapped peek
     * of a multi-hundred-MB download would have to buffer all of it into memory right here just to
     * log it, which is a worse outcome than an occasional large body simply not being captured.
     */
    private fun peekResponseBody(response: Response): ByteArray? {
        if (response.body == null) return null
        return try {
            response.peekBody(MAX_PEEK_BYTES).bytes()
        } catch (_: Exception) {
            null
        }
    }

    private fun Headers.toPairs(): List<Pair<String, String>> = (0 until size).map { name(it) to value(it) }
}
