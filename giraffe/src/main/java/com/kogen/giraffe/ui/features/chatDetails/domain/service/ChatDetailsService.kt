package com.kogen.giraffe.ui.features.chatDetails.domain.service

import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import kotlinx.coroutines.flow.Flow

/** Live source of a single chat's full details, for the chat details screen. */
internal interface ChatDetailsService {
    /** Emits the currently-loaded chat (updating live as new messages/headers arrive), or `null` before a chat has been requested. */
    val chatDetails: Flow<GiraffeChat?>
    /** Switches [chatDetails] to follow the chat identified by [id]. */
    suspend fun loadChatDetails(id: String)
}