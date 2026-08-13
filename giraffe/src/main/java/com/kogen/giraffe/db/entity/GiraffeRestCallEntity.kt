package com.kogen.giraffe.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus

/** Column name shared across REST entities/DAOs so the [GiraffeRestHeaderEntity]/[GiraffeRestMessageEntity] foreign keys stay in sync with this table's primary key. */
const val CALL_ID = "callId"

/**
 * One REST call: its route, when it started, its coarse lifecycle [status] (shared with
 * [GiraffeChatEntity] - both are "did this network call finish, and how"), and the HTTP-specific
 * detail [status] alone doesn't capture: the actual [httpMethod] (GET/POST/...) and the numeric
 * [httpStatusCode] once the response lands. Deliberately its own table rather than reusing
 * [GiraffeChatEntity] - [httpStatusCode] in particular has no gRPC equivalent, and cramming it in
 * there as a column that's only ever populated for one of the two call kinds is the kind of
 * "sometimes-null-depending-on-another-column" shape normalization steers away from.
 */
@Entity(tableName = "giraffe_rest_call")
internal data class GiraffeRestCallEntity(
    @PrimaryKey val callId: String,
    val url: String,
    val httpMethod: String,
    val timestamp: Long,
    val status: GiraffeChatStatus,
    val httpStatusCode: Int?,
)
