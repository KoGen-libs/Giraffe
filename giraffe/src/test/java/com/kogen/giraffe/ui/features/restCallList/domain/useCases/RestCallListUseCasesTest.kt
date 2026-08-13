package com.kogen.giraffe.ui.features.restCallList.domain.useCases

import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.features.restCallList.domain.service.RestCallListService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RestCallListUseCasesTest {

    @Test
    fun `LoadRestCallListUseCase forwards the service's flow untouched`() = runTest {
        val calls = listOf<GiraffeRestCall>()
        val service = mockk<RestCallListService> {
            coEvery { loadRestCallList() } returns flowOf(calls)
        }

        val result = LoadRestCallListUseCaseImpl(service).execute()

        assertThat(result.first()).isSameInstanceAs(calls)
        coVerify(exactly = 1) { service.loadRestCallList() }
    }

    @Test
    fun `DeleteRestCallsByIdUseCase forwards the id list to the service unchanged`() = runTest {
        val service = mockk<RestCallListService>(relaxed = true)
        val ids = listOf("call-1", "call-2")

        DeleteRestCallsByIdUseCaseImpl(service).execute(ids)

        coVerify(exactly = 1) { service.deleteRestCalls(ids) }
    }
}
