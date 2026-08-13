package com.kogen.giraffe.ui.features.restCallDetails.domain.useCases

import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.features.restCallDetails.domain.service.RestCallDetailsService
import kotlinx.coroutines.flow.Flow
import kz.evko.kogen_di.annotations.KoGenComponent

/** Use case wrapping [RestCallDetailsService] for [RestCallDetailsViewModel][com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi.RestCallDetailsViewModel]. */
internal interface LoadRestCallDetailsUseCase {
    val restCallDetails: Flow<GiraffeRestCall?>
    suspend fun execute(id: String)
}

@KoGenComponent
internal class LoadRestCallDetailsUseCaseImpl(
    val service: RestCallDetailsService,
) : LoadRestCallDetailsUseCase {
    override val restCallDetails: Flow<GiraffeRestCall?> = service.restCallDetails

    override suspend fun execute(id: String) {
        service.loadRestCallDetails(id)
    }
}
