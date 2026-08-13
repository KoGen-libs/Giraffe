package com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi

import com.kogen.giraffe.ui.common.mvi.UiAction

/** User-triggered intents on the REST call details screen. */
internal sealed interface RestCallDetailsAction : UiAction {
    data class LoadRestCallDetails(val id: String) : RestCallDetailsAction
    data object NavigateBack : RestCallDetailsAction
    data class SelectTab(val tab: RestCallTab) : RestCallDetailsAction
    data object ShowRequestHeaders : RestCallDetailsAction
    data object HideRequestHeaders : RestCallDetailsAction
    data object ShowResponseHeaders : RestCallDetailsAction
    data object HideResponseHeaders : RestCallDetailsAction
    data class PlayAudio(val filePath: String) : RestCallDetailsAction
    data class SeekAudio(val positionMs: Int) : RestCallDetailsAction
    data class ShowImage(val filePath: String) : RestCallDetailsAction
    data class ShowVideo(val filePath: String) : RestCallDetailsAction
    data class ShowPdf(val filePath: String) : RestCallDetailsAction
}
