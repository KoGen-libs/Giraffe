package com.kogen.giraffe.ui.common.domain.models

import com.kogen.giraffe.db.entity.RestCallWithDetails
import com.kogen.giraffe.ui.common.presentation.extensions.timestampToDateTime

/** UI-layer view of a REST call, mapped from the Room [RestCallWithDetails] projection via [toDomain] - the REST counterpart to [GiraffeChat]. */
internal data class GiraffeRestCall(
    val id: String,
    val url: String,
    val httpMethod: String,
    val timestamp: Long,
    val status: GiraffeChatStatus,
    val httpStatusCode: Int?,
    val headers: List<GiraffeHeader>,
    val messages: List<GiraffeMessage>,
)

/** Maps the Room join projection to the UI-layer [GiraffeRestCall] model. */
internal fun RestCallWithDetails.toDomain(): GiraffeRestCall {
    return GiraffeRestCall(
        id = this.call.callId,
        url = this.call.url,
        httpMethod = this.call.httpMethod,
        timestamp = this.call.timestamp,
        status = this.call.status,
        httpStatusCode = this.call.httpStatusCode,
        headers = this.headers.map { header -> header.toDomain() },
        messages = this.messages.map { message -> message.toDomain() },
    )
}

/** The request message (isIncoming = false), if one was captured. */
internal val GiraffeRestCall.request: GiraffeMessage?
    get() = messages.firstOrNull { !it.isIncoming }

/** The response message (isIncoming = true), if one was captured. */
internal val GiraffeRestCall.response: GiraffeMessage?
    get() = messages.firstOrNull { it.isIncoming }

/** Same shape as [GiraffeChat.toClipboardText] - request line (with the HTTP method/status code a REST call has and a gRPC one doesn't), headers, then each body in wire order. */
internal fun GiraffeRestCall.toClipboardText(): String = buildString {
    appendLine("URL: $url")
    appendLine("Method: $httpMethod")
    appendLine("Status: $status${httpStatusCode?.let { " ($it)" }.orEmpty()}")
    appendLine("Start: ${timestamp.timestampToDateTime()}")

    if (headers.isNotEmpty()) {
        appendLine()
        appendLine("Headers:")
        headers.forEach { header ->
            val direction = if (header.isResponse) "◀ RESPONSE" else "▶ REQUEST"
            appendLine("$direction ${header.key}: ${header.value}")
        }
    }

    messages.filterNot { it.textContent.isNullOrBlank() }.forEach { message ->
        appendLine()
        appendLine(if (message.isIncoming) "◀ RESPONSE" else "▶ REQUEST")
        appendLine(message.textContent.orEmpty())
    }
}.trim()
