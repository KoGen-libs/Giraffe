package com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi

import android.util.Log
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.common.presentation.AudioPlaybackState
import com.kogen.giraffe.ui.common.presentation.AudioPlayer
import com.kogen.giraffe.ui.features.restCallDetails.domain.useCases.LoadRestCallDetailsUseCase
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
class RestCallDetailsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val restCallDetailsFlow = MutableStateFlow<GiraffeRestCall?>(null)
    private val audioState = MutableStateFlow(AudioPlaybackState())

    private val loadRestCallDetailsUseCase = mockk<LoadRestCallDetailsUseCase>(relaxed = true) {
        every { restCallDetails } returns restCallDetailsFlow
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

    private fun viewModel() = RestCallDetailsViewModel(loadRestCallDetailsUseCase, audioPlayer)

    private fun call(id: String) = GiraffeRestCall(
        id = id,
        url = "host/path",
        httpMethod = "GET",
        timestamp = 1L,
        status = GiraffeChatStatus.Ok,
        httpStatusCode = 200,
        headers = emptyList(),
        messages = emptyList(),
    )

    @Test
    fun `mirrors the use case's call and the audio player's playback state`() = runTest(dispatcher) {
        val vm = viewModel()

        restCallDetailsFlow.value = call("call-1")
        assertThat(vm.state.value.call?.id).isEqualTo("call-1")

        audioState.value = AudioPlaybackState(filePath = "a.wav", isPlaying = true)
        assertThat(vm.state.value.audioPlayback.filePath).isEqualTo("a.wav")
        assertThat(vm.state.value.audioPlayback.isPlaying).isTrue()
    }

    @Test
    fun `starts on the Request tab with both header drawers closed`() = runTest(dispatcher) {
        val vm = viewModel()

        assertThat(vm.state.value.selectedTab).isEqualTo(RestCallTab.Request)
        assertThat(vm.state.value.showRequestHeaders).isFalse()
        assertThat(vm.state.value.showResponseHeaders).isFalse()
    }

    @Test
    fun `SelectTab switches the selected tab`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(RestCallDetailsAction.SelectTab(RestCallTab.Response))

        assertThat(vm.state.value.selectedTab).isEqualTo(RestCallTab.Response)
    }

    @Test
    fun `ShowRequestHeaders and HideRequestHeaders toggle only the request drawer`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(RestCallDetailsAction.ShowRequestHeaders)
        assertThat(vm.state.value.showRequestHeaders).isTrue()
        assertThat(vm.state.value.showResponseHeaders).isFalse()

        vm.dispatch(RestCallDetailsAction.HideRequestHeaders)
        assertThat(vm.state.value.showRequestHeaders).isFalse()
    }

    @Test
    fun `ShowResponseHeaders and HideResponseHeaders toggle only the response drawer`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(RestCallDetailsAction.ShowResponseHeaders)
        assertThat(vm.state.value.showResponseHeaders).isTrue()
        assertThat(vm.state.value.showRequestHeaders).isFalse()

        vm.dispatch(RestCallDetailsAction.HideResponseHeaders)
        assertThat(vm.state.value.showResponseHeaders).isFalse()
    }

    @Test
    fun `NavigateBack emits the NavigateBack effect`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.effects.test {
            vm.dispatch(RestCallDetailsAction.NavigateBack)
            assertThat(awaitItem()).isEqualTo(RestCallDetailsEffect.NavigateBack)
        }
    }

    @Test
    fun `PlayAudio starts playback for a file that is not currently playing`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(RestCallDetailsAction.PlayAudio("a.wav"))

        verify(exactly = 1) { audioPlayer.play("a.wav") }
        verify(exactly = 0) { audioPlayer.pause() }
    }

    @Test
    fun `PlayAudio pauses when the same file is already playing`() = runTest(dispatcher) {
        audioState.value = AudioPlaybackState(filePath = "a.wav", isPlaying = true)
        val vm = viewModel()

        vm.dispatch(RestCallDetailsAction.PlayAudio("a.wav"))

        verify(exactly = 1) { audioPlayer.pause() }
        verify(exactly = 0) { audioPlayer.play(any()) }
    }

    @Test
    fun `PlayAudio switches tracks instead of pausing when a different file is requested`() = runTest(dispatcher) {
        audioState.value = AudioPlaybackState(filePath = "a.wav", isPlaying = true)
        val vm = viewModel()

        vm.dispatch(RestCallDetailsAction.PlayAudio("b.wav"))

        verify(exactly = 1) { audioPlayer.play("b.wav") }
        verify(exactly = 0) { audioPlayer.pause() }
    }

    @Test
    fun `SeekAudio forwards the position to the audio player`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(RestCallDetailsAction.SeekAudio(1500))

        verify(exactly = 1) { audioPlayer.seekTo(1500) }
    }

    @Test
    fun `ShowImage, ShowVideo and ShowPdf each emit their matching effect`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.effects.test {
            vm.dispatch(RestCallDetailsAction.ShowImage("a.png"))
            assertThat(awaitItem()).isEqualTo(RestCallDetailsEffect.ShowImage("a.png"))

            vm.dispatch(RestCallDetailsAction.ShowVideo("a.mp4"))
            assertThat(awaitItem()).isEqualTo(RestCallDetailsEffect.ShowVideo("a.mp4"))

            vm.dispatch(RestCallDetailsAction.ShowPdf("a.pdf"))
            assertThat(awaitItem()).isEqualTo(RestCallDetailsEffect.ShowPdf("a.pdf"))
        }
    }

    @Test
    fun `LoadRestCallDetails delegates to the use case with the requested id`() = runTest(dispatcher) {
        // wrappedRequest hops onto the real Dispatchers.IO, so waiting on the test scheduler
        // alone can't observe it - a plain latch gives a dispatcher-agnostic sync point instead.
        val invoked = CountDownLatch(1)
        coEvery { loadRestCallDetailsUseCase.execute(any()) } coAnswers { invoked.countDown() }

        val vm = viewModel()
        vm.dispatch(RestCallDetailsAction.LoadRestCallDetails("call-9"))

        assertThat(invoked.await(2, TimeUnit.SECONDS)).isTrue()
        coVerify(exactly = 1) { loadRestCallDetailsUseCase.execute("call-9") }
    }

    @Test
    fun `onCleared pauses (not releases) the shared audio player`() = runTest(dispatcher) {
        // audioPlayer is a shared singleton also used by ChatDetailsViewModel - onCleared must
        // not release it, since a different, still-visible details screen might own it.
        val vm = viewModel()

        vm.callOnCleared()

        verify(exactly = 1) { audioPlayer.pause() }
        verify(exactly = 0) { audioPlayer.release() }
    }
}

private fun RestCallDetailsViewModel.callOnCleared() {
    val method = this.javaClass.getDeclaredMethod("onCleared")
    method.isAccessible = true
    method.invoke(this)
}
