package com.kogen.giraffe.ui.common.domain.models

import com.kogen.giraffe.db.entity.GiraffeMessageEntity
import com.kogen.giraffe.db.entity.GiraffeRestMessageEntity
import org.json.JSONArray
import org.json.JSONObject

/** UI-layer view of one request/response message body - a gRPC one (via [GiraffeMessageEntity.toDomain]) or a REST one (via [GiraffeRestMessageEntity.toDomain]); both map to this same shape since a body's content type/text/file is exactly as meaningful either way. */
internal data class GiraffeMessage(
    val id: Long,
    val isIncoming: Boolean,
    val contentType: GiraffeContentType,
    val textContent: String?,
    val filePath: String?,
    val timestamp: Long,
)

/** Maps the Room entity to the UI-layer [GiraffeMessage] model, pretty-printing JSON text content for display. */
internal fun GiraffeMessageEntity.toDomain(): GiraffeMessage {
    return GiraffeMessage(
        id = this.id,
        isIncoming = this.isIncoming,
        contentType = this.contentType,
        textContent = this.textContent.prettyPrintIfJson(),
        filePath = this.filePath,
        timestamp = this.timestamp,
    )
}

/** Maps the REST Room entity to the same [GiraffeMessage] model, same pretty-printing as the gRPC mapper above. */
internal fun GiraffeRestMessageEntity.toDomain(): GiraffeMessage {
    return GiraffeMessage(
        id = this.id,
        isIncoming = this.isIncoming,
        contentType = this.contentType,
        textContent = this.textContent.prettyPrintIfJson(),
        filePath = this.filePath,
        timestamp = this.timestamp,
    )
}

/** Re-formats a JSON string with 2-space indentation for readability; returns the input unchanged if it isn't valid JSON. */
private fun String?.prettyPrintIfJson(): String? {
    if (this == null) return this

    val trimmed = this.trim()
    return try {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            else -> this
        }
    } catch (_: Exception) {
        this
    }
}
