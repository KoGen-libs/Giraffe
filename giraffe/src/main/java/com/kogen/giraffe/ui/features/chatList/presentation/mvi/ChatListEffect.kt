package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import com.kogen.giraffe.ui.common.mvi.UiEffect

/** One-shot navigation effects emitted by [ChatListViewModel]. */
internal sealed interface ChatListEffect: UiEffect {
    data class NavigateToChatDetails(val id: String): ChatListEffect
    data class NavigateToRestCallDetails(val id: String): ChatListEffect
}