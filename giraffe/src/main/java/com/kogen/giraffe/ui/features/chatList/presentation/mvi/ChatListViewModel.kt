package com.kogen.giraffe.ui.features.chatList.presentation.mvi

import androidx.lifecycle.viewModelScope
import com.kogen.giraffe.ui.common.domain.models.GiraffeChatStatus
import com.kogen.giraffe.ui.common.mvi.BaseMviViewModel
import com.kogen.giraffe.ui.features.chatList.domain.useCases.DeleteChatsByIdUseCase
import com.kogen.giraffe.ui.features.chatList.domain.useCases.LoadChatListUseCase
import kotlinx.coroutines.launch
import kz.evko.kogen_di.annotations.KoGenViewModel

/** ViewModel for the chat list screen: streams the full chat list and handles multi-select deletion. */
@KoGenViewModel
internal class ChatListViewModel(
    val loadChatListUseCase: LoadChatListUseCase,
    val deleteChatsByIdUseCase: DeleteChatsByIdUseCase,
) : BaseMviViewModel<ChatListAction, ChatListState, ChatListEffect>(
    ChatListState()
) {
    init {
        viewModelScope.launch {
            loadChatListUseCase.execute().collect { items ->
                updateState {
                    it.copy(chatList = items)
                }
            }
        }
    }

    override fun handleAction(action: ChatListAction) {
        when (action) {
            is ChatListAction.DeleteChats -> {
                wrappedRequest(
                    call = {
                        deleteChatsByIdUseCase.execute(state.value.selectedIds.toList())
                    },
                )
            }

            is ChatListAction.SelectChat -> {
                updateState {
                    val selectedIds = if (action.isSelected) {
                        it.selectedIds + action.chatId
                    } else {
                        it.selectedIds - action.chatId
                    }
                    it.copy(selectedIds = selectedIds)
                }
            }

            is ChatListAction.SelectAllChats -> {
                // In-progress chats are excluded from selection - they can't be deleted while
                // still active, mirroring the per-row checkbox being hidden for them below.
                updateState {
                    it.copy(selectedIds = it.chatList.filter { chat ->
                        chat.status != GiraffeChatStatus.InProgress
                    }.map { chat ->
                        chat.id
                    }.toSet())
                }
            }

            is ChatListAction.UnSelectAllChats -> {
                updateState {
                    it.copy(selectedIds = emptySet())
                }
            }

            is ChatListAction.ShowChatDetails -> {
                emitEffect(ChatListEffect.NavigateToDetails(action.id))
            }
        }
    }
}