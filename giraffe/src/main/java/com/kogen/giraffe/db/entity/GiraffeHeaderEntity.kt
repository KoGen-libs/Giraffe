package com.kogen.giraffe.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One gRPC metadata header, tagged [isResponse] to distinguish request from trailer headers, and deleted when its parent [GiraffeChatEntity] is. */
@Entity(
    tableName = "giraffe_headers",
    foreignKeys = [
        ForeignKey(
            entity = GiraffeChatEntity::class,
            parentColumns = [CHAT_ID],
            childColumns = [CHAT_ID],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = [CHAT_ID])]
)
internal data class GiraffeHeaderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val isResponse: Boolean,
    val key: String,
    val value: String
)
