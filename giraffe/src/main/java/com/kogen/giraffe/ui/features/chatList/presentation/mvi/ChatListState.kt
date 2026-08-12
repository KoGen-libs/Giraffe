package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import com.kogen.giraffe.ui.common.domain.models.GiraffeLogEntry
import com.kogen.giraffe.ui.common.mvi.UiState

/** UI state for the unified call list screen (gRPC and REST calls merged into one timestamp-sorted feed); [selectedIds] tracks the multi-select set used for bulk deletion. */
internal data class ChatListState(
    val entries: List<GiraffeLogEntry> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
) : UiState
