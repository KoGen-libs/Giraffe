package com.kogen.giraffe.ui.features.restCallDetails.domain.useCases

import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.features.restCallDetails.domain.service.RestCallDetailsService
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoadRestCallDetailsUseCaseTest {

    @Test
    fun `exposes the service's restCallDetails flow as-is`() = runTest {
        val call = GiraffeRestCall(
            id = "call-1",
            url = "host/path",
            httpMethod = "GET",
            timestamp = 1L,
            status = GiraffeChatStatus.Ok,
            httpStatusCode = 200,
            headers = emptyList(),
            messages = emptyList(),
        )
        val service = mockk<RestCallDetailsService> {
            every { restCallDetails } returns flowOf(call)
        }

        val result = LoadRestCallDetailsUseCaseImpl(service).restCallDetails.first()

        assertThat(result).isSameInstanceAs(call)
    }

    @Test
    fun `execute forwards the requested id to the service`() = runTest {
        val service = mockk<RestCallDetailsService>(relaxed = true)

        LoadRestCallDetailsUseCaseImpl(service).execute("call-42")

        coVerify(exactly = 1) { service.loadRestCallDetails("call-42") }
    }
}
