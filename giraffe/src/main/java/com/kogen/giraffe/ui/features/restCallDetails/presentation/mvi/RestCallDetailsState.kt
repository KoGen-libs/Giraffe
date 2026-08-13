package com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi

import com.kogen.giraffe.ui.common.domain.models.GiraffeRestCall
import com.kogen.giraffe.ui.common.mvi.UiState
import com.kogen.giraffe.ui.common.presentation.AudioPlaybackState

/** Which side of the call the details screen is currently showing - the Chucker-style Request/Response tabs. */
internal enum class RestCallTab {
    Request,
    Response,
}

/** UI state for the REST call details screen; each tab keeps its own collapsible-headers-drawer visibility. */
internal data class RestCallDetailsState(
    val call: GiraffeRestCall? = null,
    val selectedTab: RestCallTab = RestCallTab.Request,
    val showRequestHeaders: Boolean = false,
    val showResponseHeaders: Boolean = false,
    val audioPlayback: AudioPlaybackState = AudioPlaybackState(),
) : UiState
