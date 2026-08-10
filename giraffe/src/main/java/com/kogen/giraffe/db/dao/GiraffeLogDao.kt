package com.kogen.giraffe.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kogen.giraffe.db.GiraffeDb
import com.kogen.giraffe.db.entity.ChatWithDetails
import com.kogen.giraffe.db.entity.GiraffeChatEntity
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.db.entity.GiraffeHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeMessageEntity
import kotlinx.coroutines.flow.Flow
import kz.evko.kogen_di.annotations.KoGenBean

/** Room DAO for Giraffe's traffic log; chat rows cascade-delete their headers/messages (see the entities' foreign keys). */
@Dao
interface GiraffeLogDao {
    @Transaction
    @Query("SELECT * FROM giraffe_chat ORDER BY timestamp DESC")
    fun getAllChatsWithDetails(): Flow<List<ChatWithDetails>>

    @Transaction
    @Query("SELECT * FROM giraffe_chat WHERE chatId = :chatId")
    fun getChatDetailsById(chatId: String): Flow<ChatWithDetails?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChat(chat: GiraffeChatEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeaders(headers: List<GiraffeHeaderEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: GiraffeMessageEntity)

    @Update
    suspend fun updateChat(chat: GiraffeChatEntity)

    /** Records a new call: inserts its row and request headers together so a concurrent reader never sees the chat without its headers. */
    @Transaction
    suspend fun startChat(chat: GiraffeChatEntity, requestHeaders: List<GiraffeHeaderEntity>) {
        insertChat(chat)
        insertHeaders(requestHeaders)
    }

    /** Finalizes a call: applies its terminal [finalStatus] and inserts response headers together. */
    @Transaction
    suspend fun completeChat(
        chatId: String,
        finalStatus: GiraffeChatStatus,
        responseHeaders: List<GiraffeHeaderEntity>,
    ) {
        updateChatStatus(chatId, finalStatus)
        insertHeaders(responseHeaders)
    }

    @Query("UPDATE giraffe_chat SET status = :finalStatus WHERE chatId = :chatId")
    suspend fun updateChatStatus(chatId: String, finalStatus: GiraffeChatStatus)

    /** Marks any chat left in [activeStatus] (normally [GiraffeChatStatus.InProgress]) as [targetStatus] - run on startup to clean up calls that never reached [completeChat] because the process died mid-call. */
    @Query("UPDATE giraffe_chat SET status = :targetStatus WHERE status = :activeStatus")
    suspend fun sanitizeStuckChats(
        activeStatus: GiraffeChatStatus = GiraffeChatStatus.InProgress,
        targetStatus: GiraffeChatStatus = GiraffeChatStatus.Interrupted,
    )

    @Query("SELECT filePath FROM giraffe_messages WHERE chatId IN (:chatIds) AND filePath IS NOT NULL")
    suspend fun getFilePathsByChatIds(chatIds: List<String>): List<String>

    @Query("DELETE FROM giraffe_chat WHERE chatId IN (:chatIds)")
    suspend fun deleteChatsByIds(chatIds: List<String>)
}

/** DI factory exposing [GiraffeDb]'s DAO as its own injectable component. */
@KoGenBean(true)
internal fun provideGiraffeLogDao(db: GiraffeDb) = db.giraffeLogDao()