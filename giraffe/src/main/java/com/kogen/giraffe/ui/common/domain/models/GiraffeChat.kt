package com.kogen.giraffe.ui.common.domain.models

import com.kogen.giraffe.db.entity.ChatWithDetails
import com.kogen.giraffe.ui.common.presentation.extensions.timestampToDateTime

/** UI-layer view of a gRPC call, mapped from the Room [ChatWithDetails] projection via [toDomain]. */
internal data class GiraffeChat(
    val id: String,
    val url: String,
    val methodShortName: String,
    val timestamp: Long,
    val status: GiraffeChatStatus,
    val headers: List<GiraffeHeader>,
    val messages: List<GiraffeMessage>,
)

/** Maps the Room join projection to the UI-layer [GiraffeChat] model. */
internal fun ChatWithDetails.toDomain(): GiraffeChat {
    return GiraffeChat(
        id = this.chat.chatId,
        url = this.chat.url,
        methodShortName = this.chat.methodShortName,
        timestamp = this.chat.timestamp,
        status = this.chat.status,
        headers = this.headers.map { header ->
            header.toDomain()
        },
        messages = this.messages.map { message ->
            message.toDomain()
        }
    )
}

/**
 * Renders the whole call as plain text for the "copy everything" action: request line, headers,
 * then every request/response body in wire order. Deliberately text-only - message.textContent
 * never holds raw media bytes in the first place (GiraffeMessageAnalyzer replaces them with a
 * placeholder before anything reaches the DB), so there's no file content to strip here.
 */
internal fun GiraffeChat.toClipboardText(): String = buildString {
    appendLine("URL: $url")
    appendLine("Method: $methodShortName")
    appendLine("Status: $status")
    appendLine("Start: ${timestamp.timestampToDateTime()}")
    if (status != GiraffeChatStatus.InProgress) {
        appendLine("End: ${messages.lastOrNull()?.timestamp?.timestampToDateTime().orEmpty()}")
    }

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