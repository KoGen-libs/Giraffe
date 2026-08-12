package com.kogen.giraffeapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.evko.kogen_di.annotations.KoGenViewModel

data class RestLogEntry(
    val timestamp: Long,
    val direction: String,
    val stack: String,
    val contentType: String?,
    val sizeBytes: Int,
    val httpStatus: Int?,
    val preview: String,
    val error: String?,
)

data class RestUiState(
    val serverHost: String = "192.168.5.193",
    val serverPort: String = "8080",
    val useKtor: Boolean = false,
    val log: List<RestLogEntry> = emptyList(),
)

/**
 * Drives the REST test screen: every button maps to one call against the TestGrpc server's
 * `/rest/...` endpoints (see that project's `RestDataController`), through whichever client
 * ([okHttpRestClient] or [ktorRestClient]) [RestUiState.useKtor] currently selects - so the same
 * button set exercises both of Giraffe's REST interceptors depending on the toggle, without
 * duplicating a screen per stack.
 */
@KoGenViewModel
class RestViewModel(
    private val okHttpRestClient: OkHttpRestClient,
    private val ktorRestClient: KtorRestClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RestUiState())
    val uiState: StateFlow<RestUiState> = _uiState.asStateFlow()

    fun setServerHost(value: String) = _uiState.update { it.copy(serverHost = value) }
    fun setServerPort(value: String) = _uiState.update { it.copy(serverPort = value) }
    fun setUseKtor(value: Boolean) = _uiState.update { it.copy(useKtor = value) }
    fun clearLog() = _uiState.update { it.copy(log = emptyList()) }

    // --- Server sends, client receives ---------------------------------------------------------

    fun getText() = get("Text", "/rest/text")
    fun getJsonObject() = get("JSON object", "/rest/json/object")
    fun getJsonArray() = get("JSON array", "/rest/json/array")
    fun getImageJpg() = get("Image JPG", "/rest/image/jpg")
    fun getImagePng() = get("Image PNG", "/rest/image/png")
    fun getImageWebp() = get("Image WEBP", "/rest/image/webp")
    fun getImage1Jpg() = get("Image1 JPG", "/rest/image1/jpg")
    fun getImage1Png() = get("Image1 PNG", "/rest/image1/png")
    fun getVideoMp4() = get("Video MP4", "/rest/video/mp4")
    fun getAudioMp3() = get("Audio MP3", "/rest/audio/mp3")
    fun getPdf() = get("PDF", "/rest/pdf")
    fun getUnknown() = get("Unknown", "/rest/unknown")
    fun getRandom() = get("Random", "/rest/random")

    // --- Client sends, server receives ----------------------------------------------------------

    fun sendText() = post(
        "Text (echo)", "/rest/echo", "text/plain",
        "Hello from Android! ${System.currentTimeMillis()}".toByteArray(),
    )

    fun sendJson() = post(
        "JSON (echo)", "/rest/echo", "application/json",
        """{"from":"android","ts":${System.currentTimeMillis()}}""".toByteArray(),
    )

    fun sendUnknown() = post(
        // 256 bytes - above Giraffe's "save as a file" floor, unlike /rest/unknown's own 32.
        "Unknown bytes (echo)", "/rest/echo", "application/octet-stream",
        ByteArray(256) { (it % 256).toByte() },
    )

    fun sendUploadAck() = post(
        "Upload (JSON ack, not mirrored)", "/rest/upload", "application/json",
        """{"from":"android"}""".toByteArray(),
    )

    /** Fetches a real image, then POSTs the exact same bytes back - exercises response-body capture and request-body capture for binary content in one round trip, without bundling a duplicate sample asset in the app. */
    fun roundTripImage() {
        viewModelScope.launch {
            val useKtor = _uiState.value.useKtor
            val client = restClient(useKtor)
            val stack = stackLabel(useKtor)
            val base = baseUrl()

            val fetched = client.get(base, "/rest/image/jpg")
            appendLog("GET /rest/image/jpg (round-trip fetch)", stack, fetched)
            if (fetched.error != null) return@launch

            val echoed = client.post(base, "/rest/echo", fetched.contentType ?: "image/jpeg", fetched.bytes)
            appendLog("POST /rest/echo (round-trip image)", stack, echoed)
        }
    }

    private fun get(label: String, path: String) {
        viewModelScope.launch {
            val useKtor = _uiState.value.useKtor
            val result = restClient(useKtor).get(baseUrl(), path)
            appendLog("GET $path ($label)", stackLabel(useKtor), result)
        }
    }

    private fun post(label: String, path: String, contentType: String, body: ByteArray) {
        viewModelScope.launch {
            val useKtor = _uiState.value.useKtor
            val result = restClient(useKtor).post(baseUrl(), path, contentType, body)
            appendLog("POST $path ($label)", stackLabel(useKtor), result)
        }
    }

    private fun restClient(useKtor: Boolean) = if (useKtor) ktorRestClient else okHttpRestClient
    private fun stackLabel(useKtor: Boolean) = if (useKtor) "Ktor" else "OkHttp"
    private fun baseUrl(): String {
        val state = _uiState.value
        return "http://${state.serverHost}:${state.serverPort}"
    }

    private fun appendLog(direction: String, stack: String, result: RestResult) {
        val entry = RestLogEntry(
            timestamp = System.currentTimeMillis(),
            direction = direction,
            stack = stack,
            contentType = result.contentType,
            sizeBytes = result.sizeBytes,
            httpStatus = result.httpStatus,
            preview = result.preview,
            error = result.error,
        )
        _uiState.update { it.copy(log = it.log + entry) }
    }
}
