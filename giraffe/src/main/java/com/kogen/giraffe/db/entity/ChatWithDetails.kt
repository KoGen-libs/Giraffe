package com.kogen.giraffe.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Room relation projection: a [GiraffeChatEntity] joined with all its [headers] and [messages] in one query. */
data class ChatWithDetails(
    @Embedded val chat: GiraffeChatEntity,

    @Relation(parentColumn = CHAT_ID, entityColumn = CHAT_ID)
    val headers: List<GiraffeHeaderEntity>,

    @Relation(parentColumn = CHAT_ID, entityColumn = CHAT_ID)
    val messages: List<GiraffeMessageEntity>,
)
