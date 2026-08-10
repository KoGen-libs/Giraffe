package com.kogen.giraffe.ui.common.domain.models

import com.kogen.giraffe.db.entity.GiraffeHeaderEntity

/** UI-layer view of a gRPC metadata header, mapped from [GiraffeHeaderEntity] via [toDomain]. */
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