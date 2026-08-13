package com.kogen.giraffe.ui.features.restCallDetails.domain.service

import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import kotlinx.coroutines.flow.Flow

/** Live source of a single REST call's full details, for the REST call details screen - the HTTP counterpart to [com.kogen.giraffe.ui.features.chatDetails.domain.service.ChatDetailsService]. */
internal interface RestCallDetailsService {
    /** Emits the currently-loaded call (updating live as it completes), or `null` before one has been requested. */
    val restCallDetails: Flow<GiraffeRestCall?>
    /** Switches [restCallDetails] to follow the call identified by [id]. */
    suspend fun loadRestCallDetails(id: String)
}
