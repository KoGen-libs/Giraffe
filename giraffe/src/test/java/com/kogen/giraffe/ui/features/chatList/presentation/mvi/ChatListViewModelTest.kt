package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.domain.models.GiraffeLogEntry
import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.features.chatList.domain.useCases.DeleteChatsByIdUseCase
import com.kogen.giraffe.ui.features.chatList.domain.useCases.LoadChatListUseCase
import com.kogen.giraffe.ui.features.restCallList.domain.useCases.DeleteRestCallsByIdUseCase
import com.kogen.giraffe.ui.features.restCallList.domain.useCases.LoadRestCallListUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val chats = MutableStateFlow<List<GiraffeChat>>(emptyList())
    private val restCalls = MutableStateFlow<List<GiraffeRestCall>>(emptyList())
    private val loadChatListUseCase = mockk<LoadChatListUseCase> {
        coEvery { execute() } returns chats
    }
    private val loadRestCallListUseCase = mockk<LoadRestCallListUseCase> {
        coEvery { execute() } returns restCalls
    }
    private val deleteChatsByIdUseCase = mockk<DeleteChatsByIdUseCase>(relaxed = true)
    private val deleteRestCallsByIdUseCase = mockk<DeleteRestCallsByIdUseCase>(relaxed = true)

    private fun chat(id: String, timestamp: Long = 1L, status: GiraffeChatStatus = GiraffeChatStatus.Ok) =
        GiraffeChat(
            id = id,
            url = "host/Service/Method",
            methodShortName = "Method",
            timestamp = timestamp,
            status = status,
            headers = emptyList(),
            messages = emptyList(),
        )

    private fun restCall(id: String, timestamp: Long = 1L, status: GiraffeChatStatus = GiraffeChatStatus.Ok) =
        GiraffeRestCall(
            id = id,
            url = "host/path",
            httpMethod = "GET",
            timestamp = timestamp,
            status = status,
            httpStatusCode = 200,
            headers = emptyList(),
            messages = emptyList(),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChatListViewModel(
        loadChatListUseCase,
        loadRestCallListUseCase,
        deleteChatsByIdUseCase,
        deleteRestCallsByIdUseCase,
    )

    @Test
    fun `initial load merges gRPC chats and REST calls into one list`() = runTest(dispatcher) {
        chats.value = listOf(chat("chat-1"))
        restCalls.value = listOf(restCall("call-1"))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.state.value.entries.map { it.id }).containsExactly("chat-1", "call-1")
    }

    @Test
    fun `entries are sorted by timestamp, most recent first, across both sources`() = runTest(dispatcher) {
        chats.value = listOf(chat("chat-old", timestamp = 1L), chat("chat-new", timestamp = 30L))
        restCalls.value = listOf(restCall("call-mid", timestamp = 20L))

        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.state.value.entries.map { it.id })
            .containsExactly("chat-new", "call-mid", "chat-old")
            .inOrder()
    }

    @Test
    fun `SelectChat adds and removes ids from the selection`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.dispatch(ChatListAction.SelectChat("chat-1", isSelected = true))
        assertThat(vm.state.value.selectedIds).containsExactly("chat-1")

        vm.dispatch(ChatListAction.SelectChat("call-1", isSelected = true))
        assertThat(vm.state.value.selectedIds).containsExactly("chat-1", "call-1")

        vm.dispatch(ChatListAction.SelectChat("chat-1", isSelected = false))
        assertThat(vm.state.value.selectedIds).containsExactly("call-1")
    }

    @Test
    fun `SelectAllChats selects every entry that is not still in progress, gRPC and REST alike`() =
        runTest(dispatcher) {
            chats.value = listOf(
                chat("chat-done", status = GiraffeChatStatus.Ok),
                chat("chat-pending", status = GiraffeChatStatus.InProgress),
            )
            restCalls.value = listOf(
                restCall("call-failed", status = GiraffeChatStatus.Error),
                restCall("call-pending", status = GiraffeChatStatus.InProgress),
            )
            val vm = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            vm.dispatch(ChatListAction.SelectAllChats)

            assertThat(vm.state.value.selectedIds).containsExactly("chat-done", "call-failed")
        }

    @Test
    fun `UnSelectAllChats clears the current selection`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(ChatListAction.SelectChat("chat-1", isSelected = true))

        vm.dispatch(ChatListAction.UnSelectAllChats)

        assertThat(vm.state.value.selectedIds).isEmpty()
    }

    @Test
    fun `ShowDetails on a gRPC entry emits NavigateToChatDetails`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(ChatListAction.ShowDetails(GiraffeLogEntry.Grpc(chat("chat-7"))))
            assertThat(awaitItem()).isEqualTo(ChatListEffect.NavigateToChatDetails("chat-7"))
        }
    }

    @Test
    fun `ShowDetails on a REST entry emits NavigateToRestCallDetails`() = runTest(dispatcher) {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.effects.test {
            vm.dispatch(ChatListAction.ShowDetails(GiraffeLogEntry.Rest(restCall("call-7"))))
            assertThat(awaitItem()).isEqualTo(ChatListEffect.NavigateToRestCallDetails("call-7"))
        }
    }

    @Test
    fun `DeleteChats routes selected gRPC ids and REST ids to their own use cases`() = runTest(dispatcher) {
        // wrappedRequest hops onto the real Dispatchers.IO, so waiting on the test scheduler
        // alone can't observe it - a plain latch gives a dispatcher-agnostic sync point instead.
        val invoked = CountDownLatch(2)
        coEvery { deleteChatsByIdUseCase.execute(any()) } coAnswers { invoked.countDown() }
        coEvery { deleteRestCallsByIdUseCase.execute(any()) } coAnswers { invoked.countDown() }

        chats.value = listOf(chat("chat-1"), chat("chat-2"))
        restCalls.value = listOf(restCall("call-1"))
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(ChatListAction.SelectChat("chat-1", isSelected = true))
        vm.dispatch(ChatListAction.SelectChat("call-1", isSelected = true))

        vm.dispatch(ChatListAction.DeleteChats)

        assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue()
        coVerify(exactly = 1) { deleteChatsByIdUseCase.execute(listOf("chat-1")) }
        coVerify(exactly = 1) { deleteRestCallsByIdUseCase.execute(listOf("call-1")) }
    }

    @Test
    fun `DeleteChats skips a use case entirely when nothing of its type is selected`() = runTest(dispatcher) {
        val invoked = CountDownLatch(1)
        coEvery { deleteChatsByIdUseCase.execute(any()) } coAnswers { invoked.countDown() }

        chats.value = listOf(chat("chat-1"))
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        vm.dispatch(ChatListAction.SelectChat("chat-1", isSelected = true))

        vm.dispatch(ChatListAction.DeleteChats)

        assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue()
        coVerify(exactly = 1) { deleteChatsByIdUseCase.execute(listOf("chat-1")) }
        coVerify(exactly = 0) { deleteRestCallsByIdUseCase.execute(any()) }
    }
}
