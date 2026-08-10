package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import com.kogen.giraffe.ui.common.mvi.UiAction

/** User-triggered intents on the chat list screen. */
sealed interface ChatListAction : UiAction {
    data object DeleteChats : ChatListAction
    data class SelectChat(val chatId: String, val isSelected: Boolean) : ChatListAction
    data object SelectAllChats : ChatListAction
    data object UnSelectAllChats : ChatListAction
    data class ShowChatDetails(val id: String) : ChatListAction
}