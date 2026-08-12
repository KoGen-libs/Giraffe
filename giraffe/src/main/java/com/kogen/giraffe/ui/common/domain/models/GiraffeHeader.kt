package com.kogen.giraffe.ui.common.domain.models

import com.kogen.giraffe.db.entity.GiraffeHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeRestHeaderEntity

/** UI-layer view of one header - a gRPC metadata header (via [GiraffeHeaderEntity.toDomain]) or an HTTP header (via [GiraffeRestHeaderEntity.toDomain]); both map to this same shape. */
internal data class GiraffeHeader(
    val id: Long,
    val isResponse: Boolean,
    val key: String,
    val value: String,
)

/** Maps the Room entity to the UI-layer [GiraffeHeader] model. */
internal fun GiraffeHeaderEntity.toDomain(): GiraffeHeader {
    return GiraffeHeader(
        id = this.id,
        isResponse = this.isResponse,
        key = this.key,
        value = this.value,
    )
}

/** Maps the REST Room entity to the same [GiraffeHeader] model an HTTP header carries no extra fields the gRPC one doesn't. */
internal fun GiraffeRestHeaderEntity.toDomain(): GiraffeHeader {
    return GiraffeHeader(
        id = this.id,
        isResponse = this.isResponse,
        key = this.key,
        value = this.value,
    )
}