package com.kogen.giraffe.ui.features.chatList.data.service

import com.kogen.giraffe.db.dao.GiraffeLogDao
import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import com.kogen.giraffe.ui.common.domain.models.toDomain
import com.kogen.giraffe.ui.features.chatList.domain.service.ChatListService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.evko.kogen_di.annotations.KoGenComponent
import java.io.File

@KoGenComponent(true)
internal class ChatListServiceImpl(
    val dao: GiraffeLogDao,
) : ChatListService {
    override suspend fun loadChatList(): Flow<List<GiraffeChat>> {
        return dao.getAllChatsWithDetails().map {
            it.map { details ->
                details.toDomain()
            }
        }
    }

    /** Deletes the given chats' DB rows and best-effort cleans up their cached media files - a missing/already-deleted file is not treated as an error. */
    override suspend fun deleteChats(chatIds: List<String>) {
        val filePaths = dao.getFilePathsByChatIds(chatIds)
        dao.deleteChatsByIds(chatIds)
        try {
            filePaths.forEach { path ->
                File(path).delete()
            }
        } catch (_: Exception) {
        }
    }
}