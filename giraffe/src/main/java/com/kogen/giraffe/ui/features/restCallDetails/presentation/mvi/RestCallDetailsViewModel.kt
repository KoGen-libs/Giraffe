package com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi

import com.kogen.androidarc.mvi.BaseMviViewModel
import com.kogen.giraffe.ui.common.presentation.AudioPlayer
import com.kogen.giraffe.ui.features.restCallDetails.domain.useCases.LoadRestCallDetailsUseCase
import kz.evko.kogen_di.annotations.KoGenViewModel

/** ViewModel for the REST call details screen: streams the selected call's details and drives voice-message playback - the HTTP counterpart to [com.kogen.giraffe.ui.features.chatDetails.presentation.mvi.ChatDetailsViewModel]. */
@KoGenViewModel
internal class RestCallDetailsViewModel(
    private val loadRestCallDetailsUseCase: LoadRestCallDetailsUseCase,
    private val audioPlayer: AudioPlayer,
) : BaseMviViewModel<RestCallDetailsAction, RestCallDetailsState, RestCallDetailsEffect>(
    RestCallDetailsState()
) {

    init {
        launchSafely {
            loadRestCallDetailsUseCase.restCallDetails.collect { call ->
                updateState {
                    it.copy(call = call)
                }
            }
        }

        launchSafely {
            audioPlayer.state.collect { playback ->
                updateState {
                    it.copy(audioPlayback = playback)
                }
            }
        }
    }

    override fun handleAction(action: RestCallDetailsAction) {
        when (action) {
            is RestCallDetailsAction.LoadRestCallDetails -> {
                wrappedRequest(
                    call = { loadRestCallDetailsUseCase.execute(action.id) },
                )
            }

            is RestCallDetailsAction.NavigateBack -> {
                emitEffect(RestCallDetailsEffect.NavigateBack)
            }

            is RestCallDetailsAction.SelectTab -> {
                updateState {
                    it.copy(selectedTab = action.tab)
                }
            }

            is RestCallDetailsAction.ShowRequestHeaders -> {
                updateState {
                    it.copy(showRequestHeaders = true)
                }
            }

            is RestCallDetailsAction.HideRequestHeaders -> {
                updateState {
                    it.copy(showRequestHeaders = false)
                }
            }

            is RestCallDetailsAction.ShowResponseHeaders -> {
                updateState {
                    it.copy(showResponseHeaders = true)
                }
            }

            is RestCallDetailsAction.HideResponseHeaders -> {
                updateState {
                    it.copy(showResponseHeaders = false)
                }
            }

            is RestCallDetailsAction.PlayAudio -> {
                val current = state.value.audioPlayback
                if (current.filePath == action.filePath && current.isPlaying) {
                    audioPlayer.pause()
                } else {
                    audioPlayer.play(action.filePath)
                }
            }

            is RestCallDetailsAction.SeekAudio -> {
                audioPlayer.seekTo(action.positionMs)
            }

            is RestCallDetailsAction.ShowImage -> {
                emitEffect(RestCallDetailsEffect.ShowImage(action.filePath))
            }

            is RestCallDetailsAction.ShowVideo -> {
                emitEffect(RestCallDetailsEffect.ShowVideo(action.filePath))
            }

            is RestCallDetailsAction.ShowPdf -> {
                emitEffect(RestCallDetailsEffect.ShowPdf(action.filePath))
            }
        }
    }

    override fun onCleared() {
        // audioPlayer is a shared singleton (also used by ChatDetailsViewModel) - pause rather
        // than release, so leaving this screen doesn't tear down playback state a different,
        // currently-visible details screen might still own.
        audioPlayer.pause()
        super.onCleared()
    }
}
