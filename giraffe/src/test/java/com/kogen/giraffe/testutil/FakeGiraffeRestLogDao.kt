package com.kogen.giraffe.testutil

import com.kogen.giraffe.db.dao.GiraffeRestLogDao
import com.kogen.giraffe.db.entity.GiraffeRestCallEntity
import com.kogen.giraffe.db.entity.GiraffeRestHeaderEntity
import com.kogen.giraffe.db.entity.GiraffeRestMessageEntity
import com.kogen.giraffe.db.entity.RestCallWithDetails
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A tiny in-memory stand-in for [GiraffeRestLogDao], used to test the REST service/use-case
 * layers above it without spinning up a real Room database. Mirrors [FakeGiraffeLogDao].
 */
internal class FakeGiraffeRestLogDao : GiraffeRestLogDao {

    val insertedRestCalls = mutableListOf<GiraffeRestCallEntity>()
    val insertedRestHeaders = mutableListOf<GiraffeRestHeaderEntity>()
    val insertedRestMessages = mutableListOf<GiraffeRestMessageEntity>()
    val deleteRestCallsByIdsCalls = mutableListOf<List<String>>()
    var filePathsToReturn: List<String> = emptyList()

    private val restCallsWithDetails = MutableStateFlow<List<RestCallWithDetails>>(emptyList())

    fun emitRestCalls(calls: List<RestCallWithDetails>) {
        restCallsWithDetails.value = calls
    }

    override fun getAllRestCallsWithDetails(): Flow<List<RestCallWithDetails>> = restCallsWithDetails

    override fun getRestCallDetailsById(callId: String): Flow<RestCallWithDetails?> =
        restCallsWithDetails.map { list -> list.firstOrNull { it.call.callId == callId } }

    override suspend fun insertRestCall(call: GiraffeRestCallEntity) {
        insertedRestCalls += call
    }

    override suspend fun insertRestHeaders(headers: List<GiraffeRestHeaderEntity>) {
        insertedRestHeaders += headers
    }

    override suspend fun insertRestMessage(message: GiraffeRestMessageEntity) {
        insertedRestMessages += message
    }

    override suspend fun sanitizeStuckRestCalls(activeStatus: GiraffeChatStatus, targetStatus: GiraffeChatStatus) {
        // No-op: nothing in the fake is ever left "in progress" across a process restart.
    }

    override suspend fun getFilePathsByRestCallIds(callIds: List<String>): List<String> = filePathsToReturn

    override suspend fun deleteRestCallsByIds(callIds: List<String>) {
        deleteRestCallsByIdsCalls += callIds
        restCallsWithDetails.value = restCallsWithDetails.value.filterNot { it.call.callId in callIds }
    }
}
