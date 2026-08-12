package com.kogen.giraffe.ui.features.restCallList.domain.useCases

import com.kogen.giraffe.ui.features.restCallList.domain.service.RestCallListService
import kz.evko.kogen_di.annotations.KoGenComponent

/** Use case wrapping [RestCallListService.deleteRestCalls] for [RestCallListViewModel][com.kogen.giraffe.ui.features.restCallList.presentation.mvi.RestCallListViewModel]. */
internal interface DeleteRestCallsByIdUseCase {
    suspend fun execute(callIds: List<String>)
}

@KoGenComponent
internal class DeleteRestCallsByIdUseCaseImpl(
    private val service: RestCallListService,
) : DeleteRestCallsByIdUseCase {
    override suspend fun execute(callIds: List<String>) {
        return service.deleteRestCalls(callIds)
    }
}
