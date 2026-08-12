package com.kogen.giraffeapp

import android.content.Context
import com.kogen.giraffe.GiraffeOkHttpInterceptor
import com.kogen.giraffe.installGiraffeKtor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.evko.kogen_di.annotations.KoGenComponent
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Outcome of one REST call from either [OkHttpRestClient] or [KtorRestClient] - [bytes] is kept around (not just a [preview]) so a round trip can re-send exactly what came back (see [RestViewModel.roundTripImage]). */
data class RestResult(
    val contentType: String? = null,
    val sizeBytes: Int = 0,
    val preview: String = "",
    val bytes: ByteArray = ByteArray(0),
    val httpStatus: Int? = null,
    val error: String? = null,
)

/** Shortens [bytes] to a readable preview if [contentType] looks textual, or a size placeholder otherwise. */
private fun previewOf(bytes: ByteArray, contentType: String?): String {
    val type = contentType?.substringBefore(";")?.trim()?.lowercase().orEmpty()
    val looksTextual = type.startsWith("text/") || type == "application/json" || type.endsWith("+json")
    if (!looksTextual) return "<бинарные данные, ${bytes.size} байт>"
    return try {
        val text = String(bytes, Charsets.UTF_8)
        if (text.length > 300) text.take(300) + "…" else text
    } catch (_: Exception) {
        "<не удалось декодировать как текст, ${bytes.size} байт>"
    }
}

/** Common shape of [OkHttpRestClient]/[KtorRestClient] so [RestViewModel] can pick one at runtime without caring which. */
interface RestClient {
    suspend fun get(baseUrl: String, path: String): RestResult
    suspend fun post(baseUrl: String, path: String, contentType: String, body: ByteArray): RestResult
}

/** OkHttp-based REST client wired with [GiraffeOkHttpInterceptor] - also covers what Retrofit would exercise, since Retrofit is itself built on OkHttp. */
@KoGenComponent(singleton = true)
class OkHttpRestClient(context: Context) : RestClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(GiraffeOkHttpInterceptor(context))
        .build()

    override suspend fun get(baseUrl: String, path: String): RestResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(baseUrl + path).build()
            client.newCall(request).execute().use { toResult(it) }
        }.getOrElse { RestResult(error = it.message ?: it.toString()) }
    }

    override suspend fun post(baseUrl: String, path: String, contentType: String, body: ByteArray): RestResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(baseUrl + path)
                    .post(body.toRequestBody(contentType.toMediaTypeOrNull()))
                    .build()
                client.newCall(request).execute().use { toResult(it) }
            }.getOrElse { RestResult(error = it.message ?: it.toString()) }
        }

    private fun toResult(response: Response): RestResult {
        val bytes = response.body?.bytes() ?: ByteArray(0)
        val contentType = response.header("Content-Type")
        return RestResult(
            contentType = contentType,
            sizeBytes = bytes.size,
            preview = previewOf(bytes, contentType),
            bytes = bytes,
            httpStatus = response.code,
        )
    }
}

/** Ktor-based REST client wired with [installGiraffeKtor], on the CIO engine specifically - not [io.ktor.client.engine.okhttp.OkHttp], so this genuinely exercises the non-OkHttp path [installGiraffeKtor] exists for. */
@KoGenComponent(singleton = true)
class KtorRestClient(context: Context) : RestClient {
    private val client: HttpClient = HttpClient(CIO) {
        installGiraffeKtor(context = context)
    }

    override suspend fun get(baseUrl: String, path: String): RestResult = runCatching {
        toResult(client.get(baseUrl + path))
    }.getOrElse { RestResult(error = it.message ?: it.toString()) }

    override suspend fun post(baseUrl: String, path: String, contentType: String, body: ByteArray): RestResult = runCatching {
        val response = client.post(baseUrl + path) {
            setBody(body)
            contentType(ContentType.parse(contentType))
        }
        toResult(response)
    }.getOrElse { RestResult(error = it.message ?: it.toString()) }

    private suspend fun toResult(response: HttpResponse): RestResult {
        val bytes = response.bodyAsBytes()
        val contentType = response.headers[HttpHeaders.ContentType]
        return RestResult(
            contentType = contentType,
            sizeBytes = bytes.size,
            preview = previewOf(bytes, contentType),
            bytes = bytes,
            httpStatus = response.status.value,
        )
    }
}
