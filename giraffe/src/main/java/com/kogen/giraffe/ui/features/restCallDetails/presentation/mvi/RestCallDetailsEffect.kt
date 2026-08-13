package com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi

import com.kogen.giraffe.ui.common.mvi.UiEffect

/** One-shot navigation effects emitted by [RestCallDetailsViewModel]. */
internal sealed interface RestCallDetailsEffect : UiEffect {
    data object NavigateBack : RestCallDetailsEffect
    data class ShowImage(val filePath: String) : RestCallDetailsEffect
    data class ShowVideo(val filePath: String) : RestCallDetailsEffect
    data class ShowPdf(val filePath: String) : RestCallDetailsEffect
}
