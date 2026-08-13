package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.domain.models.GiraffeLogEntry
import com.kogen.giraffe.ui.common.mvi.BaseMviViewModel
import com.kogen.giraffe.ui.features.chatList.domain.useCases.DeleteChatsByIdUseCase
import com.kogen.giraffe.ui.features.chatList.domain.useCases.LoadChatListUseCase
import com.kogen.giraffe.ui.features.restCallList.domain.useCases.DeleteRestCallsByIdUseCase
import com.kogen.giraffe.ui.features.restCallList.domain.useCases.LoadRestCallListUseCase
import kotlinx.coroutines.flow.combine
import kz.evko.kogen_di.annotations.KoGenViewModel

/**
 * ViewModel for the unified call list screen: merges the gRPC and REST logs into one
 * timestamp-sorted [GiraffeLogEntry] feed and handles multi-select deletion across both.
 */
@KoGenViewModel
internal class ChatListViewModel(
    val loadChatListUseCase: LoadChatListUseCase,
    val loadRestCallListUseCase: LoadRestCallListUseCase,
    val deleteChatsByIdUseCase: DeleteChatsByIdUseCase,
    val deleteRestCallsByIdUseCase: DeleteRestCallsByIdUseCase,
) : BaseMviViewModel<ChatListAction, ChatListState, ChatListEffect>(
    ChatListState()
) {
    init {
        launchSafely {
            combine(
                loadChatListUseCase.execute(),
                loadRestCallListUseCase.execute(),
            ) { chats, restCalls ->
                (chats.map { chat -> GiraffeLogEntry.Grpc(chat) } +
                    restCalls.map { call -> GiraffeLogEntry.Rest(call) })
                    .sortedByDescending { entry -> entry.timestamp }
            }.collect { entries ->
                updateState {
                    it.copy(entries = entries)
                }
            }
        }
    }

    override fun handleAction(action: ChatListAction) {
        when (action) {
            is ChatListAction.DeleteChats -> {
                val selected = state.value.entries.filter { it.id in state.value.selectedIds }
                val chatIds = selected.filterIsInstance<GiraffeLogEntry.Grpc>().map { it.id }
                val callIds = selected.filterIsInstance<GiraffeLogEntry.Rest>().map { it.id }
                wrappedRequest(
                    call = {
                        if (chatIds.isNotEmpty()) deleteChatsByIdUseCase.execute(chatIds)
                        if (callIds.isNotEmpty()) deleteRestCallsByIdUseCase.execute(callIds)
                    },
                )
            }

            is ChatListAction.SelectChat -> {
                updateState {
                    val selectedIds = if (action.isSelected) {
                        it.selectedIds + action.id
                    } else {
                        it.selectedIds - action.id
                    }
                    it.copy(selectedIds = selectedIds)
                }
            }

            is ChatListAction.SelectAllChats -> {
                // In-progress calls are excluded from selection - they can't be deleted while
                // still active, mirroring the per-row checkbox being hidden for them below.
                updateState {
                    it.copy(selectedIds = it.entries.filter { entry ->
                        entry.status != GiraffeChatStatus.InProgress
                    }.map { entry ->
                        entry.id
                    }.toSet())
                }
            }

            is ChatListAction.UnSelectAllChats -> {
                updateState {
                    it.copy(selectedIds = emptySet())
                }
            }

            is ChatListAction.ShowDetails -> {
                when (val entry = action.entry) {
                    is GiraffeLogEntry.Grpc -> emitEffect(ChatListEffect.NavigateToChatDetails(entry.id))
                    is GiraffeLogEntry.Rest -> emitEffect(ChatListEffect.NavigateToRestCallDetails(entry.id))
                }
            }
        }
    }
}
