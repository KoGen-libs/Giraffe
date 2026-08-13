package com.kogen.giraffe

import com.google.common.truth.Truth.assertThat
import io.grpc.InternalMetadata
import org.junit.Test
import java.util.UUID
import java.nio.charset.StandardCharsets.US_ASCII

/**
 * Regression coverage for a real crash: a gRPC transport can surface HTTP/2 pseudo-headers
 * (":status", ":path", ...) in [io.grpc.Metadata.keys], and [io.grpc.Metadata.Key.of] throws
 * `IllegalArgumentException` for any key name containing ':' - which used to propagate straight
 * out of [GiraffeInterceptor]'s uncaught `scope.launch` and crash the host app, just for trying to
 * log trailers/headers for the debug inspector.
 *
 * [io.grpc.InternalMetadata.newMetadata] builds a real [io.grpc.Metadata] from raw name/value
 * bytes, bypassing [io.grpc.Metadata.Key] validation entirely - exactly how a pseudo-header would
 * actually arrive in practice (it's never `put` through a validated [io.grpc.Metadata.Key] to
 * begin with).
 */
class GiraffeInterceptorTest {

    private val chatId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    private fun metadataOf(vararg namesAndValues: String): io.grpc.Metadata {
        val bytes = namesAndValues.map { it.toByteArray(US_ASCII) }.toTypedArray()
        return InternalMetadata.newMetadata(*bytes)
    }

    @Test
    fun `ordinary ASCII headers map to entities with the right chatId, direction and value`() {
        val metadata = metadataOf("content-type", "application/grpc")

        val result = headerEntities(chatId, metadata, isResponse = true)

        assertThat(result).hasSize(1)
        val entity = result.single()
        assertThat(entity.chatId).isEqualTo(chatId.toString())
        assertThat(entity.isResponse).isTrue()
        assertThat(entity.key).isEqualTo("content-type")
        assertThat(entity.value).isEqualTo("application/grpc")
    }

    @Test
    fun `HTTP2 pseudo-headers are filtered out instead of crashing`() {
        val metadata = metadataOf(
            ":status", "200",
            "content-type", "application/grpc",
        )

        val result = headerEntities(chatId, metadata, isResponse = true)

        assertThat(result.map { it.key }).containsExactly("content-type")
    }

    @Test
    fun `a metadata set containing only pseudo-headers yields an empty list, not a crash`() {
        val metadata = metadataOf(":status", "200", ":path", "/pkg.Service/Method")

        val result = headerEntities(chatId, metadata, isResponse = false)

        assertThat(result).isEmpty()
    }

    @Test
    fun `a key the ASCII marshaller rejects for other reasons is skipped, not just colon-prefixed ones`() {
        // "-bin"-suffixed names are reserved for Metadata.BINARY_BYTE_MARSHALLER - asking for them
        // with ASCII_STRING_MARSHALLER throws IllegalArgumentException too, same as a pseudo-header
        // does, just for a different validation reason. The runCatching net has to cover this case
        // as well, not just the ':' filter.
        val metadata = metadataOf(
            "x-trace-bin", "not-actually-binary",
            "content-type", "application/grpc",
        )

        val result = headerEntities(chatId, metadata, isResponse = true)

        assertThat(result.map { it.key }).containsExactly("content-type")
    }

    @Test
    fun `isResponse is threaded through unchanged for both directions`() {
        val metadata = metadataOf("k", "v")

        assertThat(headerEntities(chatId, metadata, isResponse = false).single().isResponse).isFalse()
        assertThat(headerEntities(chatId, metadata, isResponse = true).single().isResponse).isTrue()
    }
}
