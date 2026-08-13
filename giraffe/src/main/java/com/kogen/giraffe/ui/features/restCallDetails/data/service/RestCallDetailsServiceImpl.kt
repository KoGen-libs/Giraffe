package com.kogen.giraffe.ui.features.restCallDetails.data.service

import com.kogen.giraffe.db.dao.GiraffeRestLogDao
import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.common.domain.models.toDomain
import com.kogen.giraffe.ui.features.restCallDetails.domain.service.RestCallDetailsService
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
 * Keeps [restCallDetails] pointed at whichever call was most recently requested via
 * [loadRestCallDetails] - mirrors [com.kogen.giraffe.ui.features.chatDetails.data.service.ChatDetailsServiceImpl].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoGenComponent(true)
internal class RestCallDetailsServiceImpl(
    val dao: GiraffeRestLogDao,
) : RestCallDetailsService, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Job() + Dispatchers.IO
    private val _restCallDetails: MutableStateFlow<GiraffeRestCall?> = MutableStateFlow(null)
    override val restCallDetails: Flow<GiraffeRestCall?> = _restCallDetails
    private val currentCallId = MutableStateFlow<String?>(null)

    init {
        currentCallId
            .filterNotNull()
            .flatMapLatest { id -> dao.getRestCallDetailsById(id) }
            .map { it?.toDomain() }
            .onEach {
                _restCallDetails.value = it
            }
            .launchIn(this)
    }

    override suspend fun loadRestCallDetails(id: String) {
        currentCallId.value = id
    }
}
