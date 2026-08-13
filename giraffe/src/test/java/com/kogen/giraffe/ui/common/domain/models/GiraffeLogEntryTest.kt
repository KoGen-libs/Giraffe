package com.kogen.giraffe.ui.common.domain.models

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GiraffeLogEntryTest {

    private fun chat(
        id: String = "chat-1",
        timestamp: Long = 10L,
        status: GiraffeChatStatus = GiraffeChatStatus.Ok,
        messages: List<GiraffeMessage> = emptyList(),
    ) = GiraffeChat(
        id = id,
        url = "host/Service/Method",
        methodShortName = "Method",
        timestamp = timestamp,
        status = status,
        headers = emptyList(),
        messages = messages,
    )

    private fun restCall(
        id: String = "call-1",
        timestamp: Long = 10L,
        status: GiraffeChatStatus = GiraffeChatStatus.Ok,
        messages: List<GiraffeMessage> = emptyList(),
    ) = GiraffeRestCall(
        id = id,
        url = "host/path",
        httpMethod = "GET",
        timestamp = timestamp,
        status = status,
        httpStatusCode = 200,
        headers = emptyList(),
        messages = messages,
    )

    private fun message(textContent: String?) = GiraffeMessage(
        id = 1,
        isIncoming = true,
        contentType = GiraffeContentType.PlainText,
        textContent = textContent,
        filePath = null,
        timestamp = 1L,
    )

    @Test
    fun `Grpc entry exposes the wrapped chat's id, timestamp and status`() {
        val entry = GiraffeLogEntry.Grpc(chat(id = "chat-9", timestamp = 99L, status = GiraffeChatStatus.Error))

        assertThat(entry.id).isEqualTo("chat-9")
        assertThat(entry.timestamp).isEqualTo(99L)
        assertThat(entry.status).isEqualTo(GiraffeChatStatus.Error)
    }

    @Test
    fun `Rest entry exposes the wrapped call's id, timestamp and status`() {
        val entry = GiraffeLogEntry.Rest(restCall(id = "call-9", timestamp = 77L, status = GiraffeChatStatus.InProgress))

        assertThat(entry.id).isEqualTo("call-9")
        assertThat(entry.timestamp).isEqualTo(77L)
        assertThat(entry.status).isEqualTo(GiraffeChatStatus.InProgress)
    }

    @Test
    fun `title is the chat's url for a Grpc entry`() {
        val entry = GiraffeLogEntry.Grpc(chat())

        assertThat(entry.title).isEqualTo("host/Service/Method")
    }

    @Test
    fun `title is METHOD plus url for a Rest entry`() {
        val entry = GiraffeLogEntry.Rest(restCall())

        assertThat(entry.title).isEqualTo("GET host/path")
    }

    @Test
    fun `lastMessagePreview is the last message's text for either entry type`() {
        val messages = listOf(message("first"), message("last"))

        assertThat(GiraffeLogEntry.Grpc(chat(messages = messages)).lastMessagePreview).isEqualTo("last")
        assertThat(GiraffeLogEntry.Rest(restCall(messages = messages)).lastMessagePreview).isEqualTo("last")
    }

    @Test
    fun `lastMessagePreview is null when there are no messages yet`() {
        assertThat(GiraffeLogEntry.Grpc(chat(messages = emptyList())).lastMessagePreview).isNull()
        assertThat(GiraffeLogEntry.Rest(restCall(messages = emptyList())).lastMessagePreview).isNull()
    }
}
