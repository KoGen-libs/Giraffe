package com.kogen.giraffe.ui.features.chatDetails.presentation.mvi

import com.kogen.giraffe.ui.common.mvi.UiEffect

/** One-shot navigation effects emitted by [ChatDetailsViewModel]. */
sealed interface ChatDetailsEffect: UiEffect {
    data object NavigateBack: ChatDetailsEffect
    data class ShowImage(val filePath: String): ChatDetailsEffect
    data class ShowVideo(val filePath: String): ChatDetailsEffect
    data class ShowPdf(val filePath: String): ChatDetailsEffect
}