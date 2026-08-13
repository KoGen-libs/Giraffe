package com.kogen.giraffe.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One HTTP header, tagged [isResponse] to distinguish request from response headers, and deleted when its parent [GiraffeRestCallEntity] is. Mirrors [GiraffeHeaderEntity] for gRPC's metadata headers. */
@Entity(
    tableName = "giraffe_rest_headers",
    foreignKeys = [
        ForeignKey(
            entity = GiraffeRestCallEntity::class,
            parentColumns = [CALL_ID],
            childColumns = [CALL_ID],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = [CALL_ID])]
)
internal data class GiraffeRestHeaderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callId: String,
    val isResponse: Boolean,
    val key: String,
    val value: String,
)
