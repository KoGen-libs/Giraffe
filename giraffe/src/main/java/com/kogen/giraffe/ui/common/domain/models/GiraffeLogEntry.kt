package com.kogen.giraffe.ui.common.domain.models

/**
 * One row in the unified call list: either a gRPC [GiraffeChat] or a REST [GiraffeRestCall],
 * merged into a single timestamp-sorted feed by [com.kogen.giraffe.ui.features.chatList.presentation.mvi.ChatListViewModel]
 * rather than kept as two separate lists/screens.
 */
internal sealed interface GiraffeLogEntry {
    val id: String
    val timestamp: Long
    val status: GiraffeChatStatus

    data class Grpc(val chat: GiraffeChat) : GiraffeLogEntry {
        override val id: String get() = chat.id
        override val timestamp: Long get() = chat.timestamp
        override val status: GiraffeChatStatus get() = chat.status
    }

    data class Rest(val call: GiraffeRestCall) : GiraffeLogEntry {
        override val id: String get() = call.id
        override val timestamp: Long get() = call.timestamp
        override val status: GiraffeChatStatus get() = call.status
    }
}

/** One-line row title: the gRPC call's URL, or the REST call's "METHOD url". */
internal val GiraffeLogEntry.title: String
    get() = when (this) {
        is GiraffeLogEntry.Grpc -> chat.url
        is GiraffeLogEntry.Rest -> "${call.httpMethod} ${call.url}"
    }

/** Last captured message's text, used as the row's subtitle preview - null/blank when there's nothing to preview yet. */
internal val GiraffeLogEntry.lastMessagePreview: String?
    get() = when (this) {
        is GiraffeLogEntry.Grpc -> chat.messages.lastOrNull()?.textContent
        is GiraffeLogEntry.Rest -> call.messages.lastOrNull()?.textContent
    }
