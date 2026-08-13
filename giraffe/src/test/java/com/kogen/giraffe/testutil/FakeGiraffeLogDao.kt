package com.kogen.giraffe.testutil

import com.kogen.giraffe.db.dao.GiraffeLogDao
import com.kogen.giraffe.db.entity.ChatWithDetails
import com.kogen.giraffe.db.entity.GiraffeChatEntity
import com.kogen.giraffe.db.entity.GiraffeHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeMessageEntity
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A tiny in-memory stand-in for [GiraffeLogDao], used to test the service/use-case layers above
 * it without spinning up a real Room database.
 */
internal class FakeGiraffeLogDao : GiraffeLogDao {

    val insertedChats = mutableListOf<GiraffeChatEntity>()
    val insertedHeaders = mutableListOf<GiraffeHeaderEntity>()
    val insertedMessages = mutableListOf<GiraffeMessageEntity>()
    val updatedStatuses = mutableListOf<Pair<String, GiraffeChatStatus>>()
    val deleteChatsByIdsCalls = mutableListOf<List<String>>()
    var filePathsToReturn: List<String> = emptyList()

    private val chatsWithDetails = MutableStateFlow<List<ChatWithDetails>>(emptyList())

    fun emitChats(chats: List<ChatWithDetails>) {
        chatsWithDetails.value = chats
    }

    override fun getAllChatsWithDetails(): Flow<List<ChatWithDetails>> = chatsWithDetails

    override fun getChatDetailsById(chatId: String): Flow<ChatWithDetails?> =
        chatsWithDetails.map { list -> list.firstOrNull { it.chat.chatId == chatId } }

    override suspend fun insertChat(chat: GiraffeChatEntity) {
        insertedChats += chat
    }

    override suspend fun insertHeaders(headers: List<GiraffeHeaderEntity>) {
        insertedHeaders += headers
    }

    override suspend fun insertMessage(message: GiraffeMessageEntity) {
        insertedMessages += message
    }

    override suspend fun updateChat(chat: GiraffeChatEntity) {
        insertedChats.removeAll { it.chatId == chat.chatId }
        insertedChats += chat
    }

    override suspend fun updateChatStatus(chatId: String, finalStatus: GiraffeChatStatus) {
        updatedStatuses += chatId to finalStatus
    }

    override suspend fun sanitizeStuckChats(activeStatus: GiraffeChatStatus, targetStatus: GiraffeChatStatus) {
        // No-op: nothing in the fake is ever left "in progress" across a process restart.
    }

    override suspend fun getFilePathsByChatIds(chatIds: List<String>): List<String> = filePathsToReturn

    override suspend fun deleteChatsByIds(chatIds: List<String>) {
        deleteChatsByIdsCalls += chatIds
        chatsWithDetails.value = chatsWithDetails.value.filterNot { it.chat.chatId in chatIds }
    }
}
