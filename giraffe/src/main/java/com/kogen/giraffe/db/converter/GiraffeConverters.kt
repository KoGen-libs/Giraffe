package com.kogen.giraffe.db.converter

import androidx.room.TypeConverter
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.domain.models.GiraffeContentType

/** Room [TypeConverter]s for storing [GiraffeContentType]/[GiraffeChatStatus] enums as their name strings. */
internal class GiraffeConverters {
    @TypeConverter
    fun fromContentType(value: GiraffeContentType): String = value.name

    /** Falls back to [GiraffeContentType.Unknown] rather than crashing if a stored name doesn't match a current enum constant (e.g. after a rename). */
    @TypeConverter
    fun toContentType(value: String): GiraffeContentType {
        return try {
            GiraffeContentType.valueOf(value)
        } catch (_: IllegalArgumentException) {
            GiraffeContentType.Unknown
        }
    }

    @TypeConverter
    fun fromChatStatus(value: GiraffeChatStatus): String = value.name

    @TypeConverter
    fun toChatStatus(value: String): GiraffeChatStatus = GiraffeChatStatus.valueOf(value)
}