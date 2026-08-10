package com.kogen.giraffe.ui.features.chatDetails.presentation.mvi

import androidx.lifecycle.viewModelScope
import com.kogen.giraffe.ui.common.mvi.BaseMviViewModel
import com.kogen.giraffe.ui.common.presentation.AudioPlayer
import com.kogen.giraffe.ui.features.chatDetails.domain.useCases.LoadChatDetailsUseCase
import kotlinx.coroutines.launch
import kz.evko.kogen_di.annotations.KoGenViewModel

/** ViewModel for the chat details screen: streams the selected chat's details and drives voice-message playback. */
@KoGenViewModel
internal class ChatDetailsViewModel(
    private val loadChatDetailsUseCase: LoadChatDetailsUseCase,
    private val audioPlayer: AudioPlayer,
) :
    BaseMviViewModel<ChatDetailsAction, ChatDetailsState, ChatDetailsEffect>(
        ChatDetailsState()
    ) {

    init {
        viewModelScope.launch {
            loadChatDetailsUseCase.chatDetails.collect { chat ->
                updateState {
                    it.copy(chat = chat)
                }
            }
        }

        viewModelScope.launch {
            audioPlayer.state.collect { playback ->
                updateState {
                    it.copy(audioPlayback = playback)
                }
            }
        }
    }

    override fun handleAction(action: ChatDetailsAction) {
        when (action) {
            is ChatDetailsAction.LoadChatDetails -> {
                wrappedRequest(
                    call = { loadChatDetailsUseCase.execute(action.id) },
                )
            }

            is ChatDetailsAction.NavigateBack -> {
                emitEffect(ChatDetailsEffect.NavigateBack)
            }

            is ChatDetailsAction.ShowRequestDetail -> {
                updateState {
                    it.copy(showRequestDetails = true)
                }
            }

            is ChatDetailsAction.HideRequestDetail -> {
                updateState {
                    it.copy(showRequestDetails = false)
                }
            }

            is ChatDetailsAction.PlayAudio -> {
                val current = state.value.audioPlayback
                if (current.filePath == action.filePath && current.isPlaying) {
                    audioPlayer.pause()
                } else {
                    audioPlayer.play(action.filePath)
                }
            }

            is ChatDetailsAction.SeekAudio -> {
                audioPlayer.seekTo(action.positionMs)
            }

            is ChatDetailsAction.ShowImage -> {
                emitEffect(ChatDetailsEffect.ShowImage(action.filePath))
            }

            is ChatDetailsAction.ShowVideo -> {
                emitEffect(ChatDetailsEffect.ShowVideo(action.filePath))
            }

            is ChatDetailsAction.ShowPdf -> {
                emitEffect(ChatDetailsEffect.ShowPdf(action.filePath))
            }
        }
    }

    override fun onCleared() {
        audioPlayer.release()
        super.onCleared()
    }
}