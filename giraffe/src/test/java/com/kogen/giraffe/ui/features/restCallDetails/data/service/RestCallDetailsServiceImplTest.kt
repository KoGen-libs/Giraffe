package com.kogen.giraffe.ui.features.restCallDetails.data.service

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.db.entity.GiraffeRestCallEntity
import com.kogen.giraffe.db.entity.RestCallWithDetails
import com.kogen.giraffe.testutil.FakeGiraffeRestLogDao
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RestCallDetailsServiceImplTest {

    private val dao = FakeGiraffeRestLogDao()
    private val service = RestCallDetailsServiceImpl(dao)

    private fun restCallWithDetails(id: String) = RestCallWithDetails(
        call = GiraffeRestCallEntity(
            callId = id,
            url = "host/path",
            httpMethod = "GET",
            timestamp = 1L,
            status = GiraffeChatStatus.Ok,
            httpStatusCode = 200,
        ),
        headers = emptyList(),
        messages = emptyList(),
    )

    @Test
    fun `restCallDetails starts out null before anything is loaded`() = runTest {
        service.restCallDetails.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadRestCallDetails switches the stream to the requested call`() = runTest {
        dao.emitRestCalls(listOf(restCallWithDetails("call-1"), restCallWithDetails("call-2")))

        service.restCallDetails.test {
            assertThat(awaitItem()).isNull()

            service.loadRestCallDetails("call-2")

            assertThat(awaitItem()?.id).isEqualTo("call-2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching to a different id re-queries and follows that call's own updates`() = runTest {
        dao.emitRestCalls(listOf(restCallWithDetails("call-1")))

        service.restCallDetails.test {
            assertThat(awaitItem()).isNull()

            service.loadRestCallDetails("call-1")
            assertThat(awaitItem()?.id).isEqualTo("call-1")

            service.loadRestCallDetails("call-2")
            assertThat(awaitItem()).isNull() // call-2 does not exist yet

            dao.emitRestCalls(listOf(restCallWithDetails("call-1"), restCallWithDetails("call-2")))
            assertThat(awaitItem()?.id).isEqualTo("call-2")

            cancelAndIgnoreRemainingEvents()
        }
    }
}
