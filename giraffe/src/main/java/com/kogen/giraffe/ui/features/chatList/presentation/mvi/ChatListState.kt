package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import com.kogen.giraffe.ui.common.domain.models.GiraffeChat
import com.kogen.giraffe.ui.common.mvi.UiState

/** UI state for the chat list screen; [selectedIds] tracks the multi-select set used for bulk deletion. */
internal data class ChatListState(
    val chatList: List<GiraffeChat> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) : UiState
