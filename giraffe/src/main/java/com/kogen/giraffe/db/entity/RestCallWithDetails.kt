package com.kogen.giraffe.db.entity

import androidx.room.Embedded
import androidx.room.Relation

/** Room relation projection: a [GiraffeRestCallEntity] joined with all its [headers] and [messages] in one query. Mirrors [ChatWithDetails]. */
internal data class RestCallWithDetails(
    @Embedded val call: GiraffeRestCallEntity,

    @Relation(parentColumn = CALL_ID, entityColumn = CALL_ID)
    val headers: List<GiraffeRestHeaderEntity>,

    @Relation(parentColumn = CALL_ID, entityColumn = CALL_ID)
    val messages: List<GiraffeRestMessageEntity>,
)
