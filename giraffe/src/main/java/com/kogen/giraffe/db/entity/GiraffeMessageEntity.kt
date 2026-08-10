package com.kogen.giraffe.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/** One request or response message body within a call: its parsed [contentType], text form, and any extracted media's [filePath]. */
@Entity(
    tableName = "giraffe_messages",
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
data class GiraffeMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String,
    val isIncoming: Boolean,
    val contentType: GiraffeContentType,
    val textContent: String?,
    val filePath: String?,
    val timestamp: Long,
)
