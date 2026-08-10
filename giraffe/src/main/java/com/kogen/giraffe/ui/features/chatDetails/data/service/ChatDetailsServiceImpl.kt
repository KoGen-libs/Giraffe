package com.kogen.giraffe.ui.features.chatDetails.data.service

import com.kogen.giraffe.db.dao.GiraffeLogDao
import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import com.kogen.giraffe.ui.common.domain.models.toDomain
import com.kogen.giraffe.ui.features.chatDetails.domain.service.ChatDetailsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kz.evko.kogen_di.annotations.KoGenComponent
import kotlin.coroutines.CoroutineContext

/**
 * Keeps [chatDetails] pointed at whichever chat was most recently requested via
 * [loadChatDetails]: switching [currentChatId] re-subscribes the Room query via [flatMapLatest],
 * so the details screen keeps receiving live updates for the active chat and automatically drops
 * the previous subscription instead of leaking it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoGenComponent(true)
internal class ChatDetailsServiceImpl(
    val dao: GiraffeLogDao,
) : ChatDetailsService, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Job() + Dispatchers.IO
    private val _chatDetails: MutableStateFlow<GiraffeChat?> = MutableStateFlow(null)
    override val chatDetails: Flow<GiraffeChat?> = _chatDetails
    private val currentChatId = MutableStateFlow<String?>(null)

    init {
        currentChatId
            .filterNotNull()
            .flatMapLatest { id -> dao.getChatDetailsById(id) }
            .map { it?.toDomain() }
            .onEach {
                _chatDetails.value = it
            }
            .launchIn(this)
    }

    override suspend fun loadChatDetails(id: String) {
        currentChatId.value = id
    }
}