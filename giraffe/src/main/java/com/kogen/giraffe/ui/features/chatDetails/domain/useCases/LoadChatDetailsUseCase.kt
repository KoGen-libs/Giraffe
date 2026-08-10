package com.kogen.giraffe.ui.features.chatDetails.domain.useCases

import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import com.kogen.giraffe.ui.features.chatDetails.domain.service.ChatDetailsService
import kotlinx.coroutines.flow.Flow
import kz.evko.kogen_di.annotations.KoGenComponent

/** Use case wrapping [ChatDetailsService] for [ChatDetailsViewModel][com.kogen.giraffe.ui.features.chatDetails.presentation.mvi.ChatDetailsViewModel]. */
internal interface LoadChatDetailsUseCase {
    val chatDetails: Flow<GiraffeChat?>
    suspend fun execute(id: String)
}

@KoGenComponent
internal class LoadChatDetailsUseCaseImpl(
    val service: ChatDetailsService,
) : LoadChatDetailsUseCase {
    override val chatDetails: Flow<GiraffeChat?> = service.chatDetails

    override suspend fun execute(id: String) {
         service.loadChatDetails(id)
    }
}