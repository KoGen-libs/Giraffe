package com.kogen.giraffe.ui.common.domain.models

import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.db.entity.GiraffeRestCallEntity
import com.kogen.giraffe.db.entity.GiraffeRestHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeRestMessageEntity
import com.kogen.giraffe.db.entity.RestCallWithDetails
import com.kogen.giraffe.ui.common.presentation.extensions.timestampToDateTime
import org.junit.Test

/** The REST counterpart to [DomainMappersTest] - same mapping behavior, HTTP-specific entities. */
class RestDomainMappersTest {

    @Test
    fun `GiraffeRestHeaderEntity maps field-for-field to its domain model`() {
        val entity = GiraffeRestHeaderEntity(
            id = 1,
            callId = "call-1",
            isResponse = true,
            key = "content-type",
            value = "application/json",
        )

        val domain = entity.toDomain()

        assertThat(domain.id).isEqualTo(1L)
        assertThat(domain.isResponse).isTrue()
        assertThat(domain.key).isEqualTo("content-type")
        assertThat(domain.value).isEqualTo("application/json")
    }

    @Test
    fun `GiraffeRestMessageEntity pretty-prints JSON text content, same as the gRPC mapper`() {
        val entity = restMessage(textContent = """{"a":1}""")

        assertThat(entity.toDomain().textContent).isEqualTo(
            org.json.JSONObject("""{"a":1}""").toString(2)
        )
    }

    @Test
    fun `GiraffeRestMessageEntity passes null text content through`() {
        val entity = restMessage(textContent = null)

        assertThat(entity.toDomain().textContent).isNull()
    }

    @Test
    fun `RestCallWithDetails maps the call plus all nested headers and messages`() {
        val call = GiraffeRestCallEntity(
            callId = "call-1",
            url = "example.com/users/1",
            httpMethod = "GET",
            timestamp = 42L,
            status = GiraffeChatStatus.Ok,
            httpStatusCode = 200,
        )
        val headers = listOf(
            GiraffeRestHeaderEntity(id = 1, callId = "call-1", isResponse = false, key = "k1", value = "v1"),
        )
        val messages = listOf(restMessage(id = 5, textContent = "hi"))
        val details = RestCallWithDetails(call = call, headers = headers, messages = messages)

        val domain = details.toDomain()

        assertThat(domain.id).isEqualTo("call-1")
        assertThat(domain.url).isEqualTo("example.com/users/1")
        assertThat(domain.httpMethod).isEqualTo("GET")
        assertThat(domain.timestamp).isEqualTo(42L)
        assertThat(domain.status).isEqualTo(GiraffeChatStatus.Ok)
        assertThat(domain.httpStatusCode).isEqualTo(200)
        assertThat(domain.headers).hasSize(1)
        assertThat(domain.headers.single().key).isEqualTo("k1")
        assertThat(domain.messages).hasSize(1)
        assertThat(domain.messages.single().id).isEqualTo(5L)
    }

    // --- GiraffeRestCall.request / .response ----------------------------------------------------

    private fun domainMessage(id: Long, isIncoming: Boolean, textContent: String?) = GiraffeMessage(
        id = id,
        isIncoming = isIncoming,
        contentType = GiraffeContentType.Json,
        textContent = textContent,
        filePath = null,
        timestamp = 2_000L,
    )

    private fun domainCall(
        status: GiraffeChatStatus = GiraffeChatStatus.Ok,
        httpStatusCode: Int? = 200,
        headers: List<GiraffeHeader> = emptyList(),
        messages: List<GiraffeMessage> = emptyList(),
    ) = GiraffeRestCall(
        id = "call-1",
        url = "host/path",
        httpMethod = "GET",
        timestamp = 1_000L,
        status = status,
        httpStatusCode = httpStatusCode,
        headers = headers,
        messages = messages,
    )

    @Test
    fun `request returns the outgoing message and response the incoming one`() {
        val request = domainMessage(id = 1, isIncoming = false, textContent = "req")
        val response = domainMessage(id = 2, isIncoming = true, textContent = "res")
        val call = domainCall(messages = listOf(request, response))

        assertThat(call.request).isSameInstanceAs(request)
        assertThat(call.response).isSameInstanceAs(response)
    }

    @Test
    fun `request and response are null when that side was never captured`() {
        val call = domainCall(messages = emptyList())

        assertThat(call.request).isNull()
        assertThat(call.response).isNull()
    }

    // --- GiraffeRestCall.toClipboardText ---------------------------------------------------------

    @Test
    fun `toClipboardText lists method, status code, headers and bodies in wire order`() {
        val call = domainCall(
            httpStatusCode = 404,
            status = GiraffeChatStatus.Error,
            headers = listOf(
                GiraffeHeader(id = 1, isResponse = false, key = "x-request-id", value = "abc"),
                GiraffeHeader(id = 2, isResponse = true, key = "x-trace", value = "def"),
            ),
            messages = listOf(
                domainMessage(id = 1, isIncoming = false, textContent = "{\"q\":1}"),
                domainMessage(id = 2, isIncoming = true, textContent = "{\"a\":1}"),
            ),
        )

        val text = call.toClipboardText()
        val lines = text.lines()

        assertThat(lines[0]).isEqualTo("URL: host/path")
        assertThat(lines[1]).isEqualTo("Method: GET")
        assertThat(lines[2]).isEqualTo("Status: Error (404)")
        assertThat(lines[3]).isEqualTo("Start: ${1_000L.timestampToDateTime()}")
        assertThat(text).contains("▶ REQUEST x-request-id: abc")
        assertThat(text).contains("◀ RESPONSE x-trace: def")
        assertThat(text.indexOf("▶ REQUEST\n{\"q\":1}")).isGreaterThan(-1)
        assertThat(text.indexOf("◀ RESPONSE\n{\"a\":1}")).isGreaterThan(text.indexOf("▶ REQUEST\n{\"q\":1}"))
    }

    @Test
    fun `toClipboardText omits the status code parens when it was never set`() {
        val call = domainCall(httpStatusCode = null, status = GiraffeChatStatus.InProgress)

        val text = call.toClipboardText()

        assertThat(text.lines()[2]).isEqualTo("Status: InProgress")
    }

    @Test
    fun `toClipboardText skips messages with no text content`() {
        val call = domainCall(
            messages = listOf(
                domainMessage(id = 1, isIncoming = false, textContent = "  "),
                domainMessage(id = 2, isIncoming = false, textContent = null),
                domainMessage(id = 3, isIncoming = true, textContent = "{\"ok\":true}"),
            ),
        )

        val text = call.toClipboardText()

        assertThat(text).contains("{\"ok\":true}")
        assertThat(text.lines().count { it == "▶ REQUEST" || it == "◀ RESPONSE" }).isEqualTo(1)
    }

    private fun restMessage(id: Long = 1, textContent: String?) = GiraffeRestMessageEntity(
        id = id,
        callId = "call-1",
        isIncoming = true,
        contentType = GiraffeContentType.PlainText,
        textContent = textContent,
        filePath = null,
        timestamp = 100L,
    )
}
