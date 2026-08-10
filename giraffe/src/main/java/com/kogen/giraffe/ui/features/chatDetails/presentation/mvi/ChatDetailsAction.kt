package com.kogen.giraffe.ui.features.chatDetails.presentation.mvi

import com.kogen.giraffe.ui.common.mvi.UiAction

/** User-triggered intents on the chat details screen. */
sealed interface ChatDetailsAction : UiAction {
    data class LoadChatDetails(val id: String) : ChatDetailsAction
    data object NavigateBack : ChatDetailsAction
    data object ShowRequestDetail: ChatDetailsAction
    data object HideRequestDetail: ChatDetailsAction
    data class PlayAudio(val filePath: String) : ChatDetailsAction
    data class SeekAudio(val positionMs: Int) : ChatDetailsAction
    data class ShowImage(val filePath: String) : ChatDetailsAction
    data class ShowVideo(val filePath: String) : ChatDetailsAction
    data class ShowPdf(val filePath: String) : ChatDetailsAction
}