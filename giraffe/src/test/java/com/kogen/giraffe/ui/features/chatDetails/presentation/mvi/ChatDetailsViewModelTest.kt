package com.kogen.giraffe.ui.features.chatDetails.presentation.mvi

import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.presentation.AudioPlaybackState
import com.kogen.giraffe.ui.common.presentation.AudioPlayer
import com.kogen.giraffe.ui.features.chatDetails.domain.useCases.LoadChatDetailsUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
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
class ChatDetailsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val chatDetailsFlow = MutableStateFlow<GiraffeChat?>(null)
    private val audioState = MutableStateFlow(AudioPlaybackState())

    private val loadChatDetailsUseCase = mockk<LoadChatDetailsUseCase>(relaxed = true) {
        every { chatDetails } returns chatDetailsFlow
    }
    private val audioPlayer = mockk<AudioPlayer>(relaxed = true) {
        every { state } returns audioState
    }

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

    private fun viewModel() = ChatDetailsViewModel(loadChatDetailsUseCase, audioPlayer)

    private fun chat(id: String) = GiraffeChat(
        id = id,
        url = "host/Service/Method",
        methodShortName = "Method",
        timestamp = 1L,
        status = GiraffeChatStatus.Ok,
        headers = emptyList(),
        messages = emptyList(),
    )

    @Test
    fun `mirrors the use case's chat and the audio player's playback state`() = runTest(dispatcher) {
        val vm = viewModel()

        chatDetailsFlow.value = chat("chat-1")
        assertThat(vm.state.value.chat?.id).isEqualTo("chat-1")

        audioState.value = AudioPlaybackState(filePath = "a.wav", isPlaying = true)
        assertThat(vm.state.value.audioPlayback.filePath).isEqualTo("a.wav")
        assertThat(vm.state.value.audioPlayback.isPlaying).isTrue()
    }

    @Test
    fun `ShowRequestDetail and HideRequestDetail toggle the flag`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(ChatDetailsAction.ShowRequestDetail)
        assertThat(vm.state.value.showRequestDetails).isTrue()

        vm.dispatch(ChatDetailsAction.HideRequestDetail)
        assertThat(vm.state.value.showRequestDetails).isFalse()
    }

    @Test
    fun `NavigateBack emits the NavigateBack effect`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.effects.test {
            vm.dispatch(ChatDetailsAction.NavigateBack)
            assertThat(awaitItem()).isEqualTo(ChatDetailsEffect.NavigateBack)
        }
    }

    @Test
    fun `PlayAudio starts playback for a file that is not currently playing`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(ChatDetailsAction.PlayAudio("a.wav"))

        verify(exactly = 1) { audioPlayer.play("a.wav") }
        verify(exactly = 0) { audioPlayer.pause() }
    }

    @Test
    fun `PlayAudio pauses when the same file is already playing`() = runTest(dispatcher) {
        audioState.value = AudioPlaybackState(filePath = "a.wav", isPlaying = true)
        val vm = viewModel()

        vm.dispatch(ChatDetailsAction.PlayAudio("a.wav"))

        verify(exactly = 1) { audioPlayer.pause() }
        verify(exactly = 0) { audioPlayer.play(any()) }
    }

    @Test
    fun `PlayAudio switches tracks instead of pausing when a different file is requested`() = runTest(dispatcher) {
        audioState.value = AudioPlaybackState(filePath = "a.wav", isPlaying = true)
        val vm = viewModel()

        vm.dispatch(ChatDetailsAction.PlayAudio("b.wav"))

        verify(exactly = 1) { audioPlayer.play("b.wav") }
        verify(exactly = 0) { audioPlayer.pause() }
    }

    @Test
    fun `SeekAudio forwards the position to the audio player`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(ChatDetailsAction.SeekAudio(1500))

        verify(exactly = 1) { audioPlayer.seekTo(1500) }
    }

    @Test
    fun `LoadChatDetails delegates to the use case with the requested id`() = runTest(dispatcher) {
        // wrappedRequest hops onto the real Dispatchers.IO, so waiting on the test scheduler
        // alone can't observe it - a plain latch gives a dispatcher-agnostic sync point instead.
        val invoked = CountDownLatch(1)
        coEvery { loadChatDetailsUseCase.execute(any()) } coAnswers { invoked.countDown() }

        val vm = viewModel()
        vm.dispatch(ChatDetailsAction.LoadChatDetails("chat-9"))

        assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue()
        coVerify(exactly = 1) { loadChatDetailsUseCase.execute("chat-9") }
    }

    @Test
    fun `onCleared pauses (not releases) the shared audio player`() = runTest(dispatcher) {
        // audioPlayer is a shared singleton also used by RestCallDetailsViewModel - onCleared
        // must not release it, since a different, still-visible details screen might own it.
        val vm = viewModel()

        vm.callOnCleared()

        verify(exactly = 1) { audioPlayer.pause() }
        verify(exactly = 0) { audioPlayer.release() }
    }
}

private fun ChatDetailsViewModel.callOnCleared() {
    val method = this.javaClass.getDeclaredMethod("onCleared")
    method.isAccessible = true
    method.invoke(this)
}
