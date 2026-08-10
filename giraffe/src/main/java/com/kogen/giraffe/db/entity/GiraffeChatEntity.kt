package com.kogen.giraffe.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus

/** Column name shared across entities/DAOs so the [GiraffeHeaderEntity]/[GiraffeMessageEntity] foreign keys stay in sync with this table's primary key. */
const val CHAT_ID = "chatId"

/** One gRPC call: its route, when it started, and its current lifecycle [status]. */
@Entity(tableName = "giraffe_chat")
data class GiraffeChatEntity(
    @PrimaryKey val chatId: String,
    val url: String,
    val methodShortName: String,
    val timestamp: Long,
    val status: GiraffeChatStatus,
)
