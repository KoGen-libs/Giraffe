package com.kogen.giraffe.ui.features.restCallList.domain.service

import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import kotlinx.coroutines.flow.Flow

/** Source of the full REST call list, and deletion of selected calls - the HTTP counterpart to [com.kogen.giraffe.ui.features.chatList.domain.service.ChatListService]. */
internal interface RestCallListService {
    /** Live list of all logged REST calls, most recent first. */
    suspend fun loadRestCallList(): Flow<List<GiraffeRestCall>>
    /** Deletes the given calls (and any media files they extracted). */
    suspend fun deleteRestCalls(callIds: List<String>)
}
