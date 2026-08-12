package com.kogen.giraffe.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/**
 * One request or response body within a REST call: its parsed [contentType], text form, and any
 * extracted media's [filePath]. Shape-identical to [GiraffeMessageEntity] on purpose - a body's
 * content (text/JSON/image/audio/video/pdf/unknown) is exactly as meaningful for a REST call as
 * for a gRPC one, and the existing [com.kogen.giraffe.ui.common.domain.models.GiraffeContentType]
 * rendering views are reused as-is. Only the parent foreign key differs (a REST call, not a
 * chat), so it can't literally be the same table without a foreign key pointing at two different
 * parent tables depending on the row.
 */
@Entity(
    tableName = "giraffe_rest_messages",
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
data class GiraffeRestMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callId: String,
    val isIncoming: Boolean,
    val contentType: GiraffeContentType,
    val textContent: String?,
    val filePath: String?,
    val timestamp: Long,
)
