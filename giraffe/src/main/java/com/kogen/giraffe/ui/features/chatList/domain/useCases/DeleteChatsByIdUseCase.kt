package com.kogen.giraffe.ui.features.chatList.domain.useCases

import com.kogen.giraffe.ui.features.chatList.domain.service.ChatListService
import kz.evko.kogen_di.annotations.KoGenComponent

/** Use case wrapping [ChatListService.deleteChats] for [ChatListViewModel][com.kogen.giraffe.ui.features.chatList.presentation.mvi.ChatListViewModel]. */
internal interface DeleteChatsByIdUseCase {
    suspend fun execute(chatIds: List<String>)
}

@KoGenComponent
internal class DeleteChatByIdUseCaseImpl(
    private val service: ChatListService,
) : DeleteChatsByIdUseCase {
    override suspend fun execute(chatIds: List<String>) {
        return service.deleteChats(chatIds)
    }
}