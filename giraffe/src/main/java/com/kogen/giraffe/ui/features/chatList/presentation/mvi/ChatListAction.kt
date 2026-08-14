package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import com.kogen.giraffe.ui.common.domain.models.GiraffeLogEntry
import com.kogen.androidarc.mvi.UiAction

/** User-triggered intents on the unified call list screen. */
internal sealed interface ChatListAction : UiAction {
    data object DeleteChats : ChatListAction
    data class SelectChat(val id: String, val isSelected: Boolean) : ChatListAction
    data object SelectAllChats : ChatListAction
    data object UnSelectAllChats : ChatListAction
    data class ShowDetails(val entry: GiraffeLogEntry) : ChatListAction
}