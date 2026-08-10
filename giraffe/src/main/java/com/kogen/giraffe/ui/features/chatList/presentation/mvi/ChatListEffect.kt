package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import com.kogen.giraffe.ui.common.mvi.UiEffect

/** One-shot navigation effects emitted by [ChatListViewModel]. */
sealed interface ChatListEffect: UiEffect {
    data class NavigateToDetails(val id: String): ChatListEffect
}