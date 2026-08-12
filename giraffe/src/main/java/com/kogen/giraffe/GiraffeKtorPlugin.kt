package com.kogen.giraffe

import android.content.Context
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.request
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import java.util.UUID

/**
 * Installs Giraffe's REST logging on a Ktor [io.ktor.client.HttpClient] - the Ktor counterpart to
 * [GiraffeOkHttpInterceptor] (for OkHttp/Retrofit), sharing the same [GiraffeRestCallRecorder].
 * Separate from it rather than layered on top: Ktor's client engines (CIO, Darwin, Js, ...) don't
 * go through OkHttp at all, only the OkHttp *engine* does - relying on [GiraffeOkHttpInterceptor]
 * would silently do nothing on any other engine.
 *
 * ```
 * val client = HttpClient(CIO) {
 *     installGiraffeKtor(context = applicationContext)
 * }
 * ```
 *
 * Built entirely on Ktor's own public [ResponseObserver]/[HttpResponseValidator] plugins rather
 * than a hand-rolled `on(Send)` hook: [ResponseObserver] already solves "read the response body
 * without consuming the copy the caller still needs to read" - by forking the raw response
 * channel internally - using Ktor's stable public API surface. An earlier version of this
 * function tried to fork that channel itself and could only find a mechanism Ktor marks
 * `@InternalAPI` ("could be removed or changed without notice") for it; building on the
 * already-public plugin that solves the same problem is the right call instead of depending on
 * that.
 *
 * Request and response are both captured: [ResponseObserver.onResponse] fires with a completed
 * [HttpResponse] whose [HttpResponse.request] carries the original request (including its body,
 * via [HttpRequest.content]) - one callback, both sides of the call. A response body is only
 * skipped if the request never got one at all (a network failure, not an HTTP error status -
 * [HttpResponseValidator] catches that case separately below).
 *
 * One known edge case: if the consumer sets `expectSuccess = true` on a request (Ktor's default
 * is `false`), a non-2xx response is recorded twice - once via [ResponseObserver] (which still
 * sees the real response and body) and once more via [HttpResponseValidator]'s exception handler
 * (since Ktor then throws for that same response). Not deduplicated for this first version;
 * flagging it rather than leaving it a silent surprise.
 */
fun HttpClientConfig<*>.installGiraffeKtor(context: Context, loggingEnabled: Boolean = true) {
    val recorder = GiraffeRestCallRecorder(context, loggingEnabled)
    if (!recorder.isEnabled) return

    ResponseObserver { response ->
        recordResponse(recorder, response)
    }

    HttpResponseValidator {
        handleResponseExceptionWithRequest { cause, request ->
            recordFailure(recorder, request, cause)
        }
    }
}

private suspend fun recordResponse(recorder: GiraffeRestCallRecorder, response: HttpResponse) {
    val request = response.request
    val responseBody = try {
        response.bodyAsBytes()
    } catch (_: Exception) {
        null
    }

    recordSafely(
        recorder = recorder,
        callId = UUID.randomUUID().toString(),
        url = request.url.toString(),
        method = request.method.value,
        startTimestamp = response.requestTime.timestamp,
        requestHeaders = request.headers.entries().flatMap { (key, values) -> values.map { key to it } },
        requestContentType = request.content.contentType?.toString(),
        requestBody = captureRequestBody(request.content),
        responseHeaders = response.headers.entries().flatMap { (key, values) -> values.map { key to it } },
        responseContentType = response.contentType()?.toString(),
        responseBody = responseBody,
        httpStatusCode = response.status.value,
        error = null,
    )
}

private fun recordFailure(recorder: GiraffeRestCallRecorder, request: HttpRequest, cause: Throwable) {
    recordSafely(
        recorder = recorder,
        callId = UUID.randomUUID().toString(),
        url = request.url.toString(),
        method = request.method.value,
        startTimestamp = System.currentTimeMillis(),
        requestHeaders = request.headers.entries().flatMap { (key, values) -> values.map { key to it } },
        requestContentType = request.content.contentType?.toString(),
        requestBody = captureRequestBody(request.content),
        responseHeaders = emptyList(),
        responseContentType = null,
        responseBody = null,
        httpStatusCode = null,
        error = cause,
    )
}

/** Reads bytes from [content] only when it's already fully in memory - a genuinely streamed upload isn't captured, rather than risk interfering with what actually gets sent. */
private fun captureRequestBody(content: OutgoingContent): ByteArray? {
    return when (content) {
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
