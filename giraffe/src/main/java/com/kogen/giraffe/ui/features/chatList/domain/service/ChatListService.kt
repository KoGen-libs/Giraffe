package com.kogen.giraffe.ui.features.chatList.domain.service

import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import kotlinx.coroutines.flow.Flow

/** Source of the full chat list, and deletion of selected chats, for the chat list screen. */
internal interface ChatListService {
    /** Live list of all logged chats, most recent first. */
    suspend fun loadChatList(): Flow<List<GiraffeChat>>
    /** Deletes the given chats (and any media files they extracted). */
    suspend fun deleteChats(chatIds: List<String>)
}