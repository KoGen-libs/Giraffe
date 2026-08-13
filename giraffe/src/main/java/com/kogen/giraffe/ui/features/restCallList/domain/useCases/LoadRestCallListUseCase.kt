package com.kogen.giraffe.ui.features.restCallList.domain.useCases

import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.features.restCallList.domain.service.RestCallListService
import kotlinx.coroutines.flow.Flow
import kz.evko.kogen_di.annotations.KoGenComponent

/** Use case wrapping [RestCallListService.loadRestCallList] for [RestCallListViewModel][com.kogen.giraffe.ui.features.restCallList.presentation.mvi.RestCallListViewModel]. */
internal interface LoadRestCallListUseCase {
    suspend fun execute(): Flow<List<GiraffeRestCall>>
}

@KoGenComponent
internal class LoadRestCallListUseCaseImpl(
    val service: RestCallListService,
) : LoadRestCallListUseCase {
    override suspend fun execute(): Flow<List<GiraffeRestCall>> {
        return service.loadRestCallList()
    }
}
