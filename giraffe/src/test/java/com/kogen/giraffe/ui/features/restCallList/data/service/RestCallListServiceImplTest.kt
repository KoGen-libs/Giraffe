package com.kogen.giraffe.ui.features.restCallList.data.service

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.db.entity.GiraffeRestCallEntity
import com.kogen.giraffe.db.entity.RestCallWithDetails
import com.kogen.giraffe.testutil.FakeGiraffeRestLogDao
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestCallListServiceImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dao = FakeGiraffeRestLogDao()
    private val service = RestCallListServiceImpl(dao)

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
    fun `loadRestCallList maps every emitted row to its domain model`() = runTest {
        dao.emitRestCalls(listOf(restCallWithDetails("call-1"), restCallWithDetails("call-2")))

        service.loadRestCallList().test {
            val calls = awaitItem()
            assertThat(calls.map { it.id }).containsExactly("call-1", "call-2").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadRestCallList reflects later updates from the dao`() = runTest {
        service.loadRestCallList().test {
            assertThat(awaitItem()).isEmpty()

            dao.emitRestCalls(listOf(restCallWithDetails("call-1")))

            assertThat(awaitItem().map { it.id }).containsExactly("call-1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteRestCalls removes the rows and deletes their media files from disk`() = runTest {
        val mediaFile = tempFolder.newFile("orphaned_media.png")
        mediaFile.writeText("fake image bytes")
        dao.filePathsToReturn = listOf(mediaFile.absolutePath)

        service.deleteRestCalls(listOf("call-1"))

        assertThat(dao.deleteRestCallsByIdsCalls).containsExactly(listOf("call-1"))
        assertThat(File(mediaFile.absolutePath).exists()).isFalse()
    }

    @Test
    fun `deleteRestCalls does not throw when a referenced file is already gone`() = runTest {
        dao.filePathsToReturn = listOf(tempFolder.root.resolve("never_existed.png").absolutePath)

        service.deleteRestCalls(listOf("call-1"))

        assertThat(dao.deleteRestCallsByIdsCalls).containsExactly(listOf("call-1"))
    }
}
