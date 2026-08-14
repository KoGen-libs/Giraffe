package com.kogen.giraffe.ui.features.chatList.presentation.screen

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.kogen.giraffe.di.koGenViewModel
import com.kogen.giraffe.navigation.ActionToChatDetails
import com.kogen.giraffe.navigation.ActionToRestCallDetails
import com.kogen.giraffe.navigation.navigateSafety
import com.kogen.androidarc.ui.ScreenContainerWrapper
import com.kogen.giraffe.ui.features.chatList.presentation.mvi.ChatListEffect
import com.kogen.giraffe.ui.features.chatList.presentation.mvi.ChatListViewModel
import kz.evko.navigation.annotation.KoGenScreen

/**
 * Nav-graph start destination for Giraffe's UI (registered via `@KoGenScreen` in Giraffe's own
 * generated nav graph, not something the embedding app navigates to directly): the top-level list
 * of logged calls.
 */
@KoGenScreen(startDestination = true)
@Composable
internal fun ChatListContainer(
    navController: NavHostController,
) {
    ScreenContainerWrapper(
        viewModel = koGenViewModel<ChatListViewModel>(),
        onEffect = {
            when (it) {
                is ChatListEffect.NavigateToChatDetails -> {
                    navController.navigateSafety(ActionToChatDetails(it.id))
                }

                is ChatListEffect.NavigateToRestCallDetails -> {
                    navController.navigateSafety(ActionToRestCallDetails(it.id))
                }
            }
        },
        screenContent = { state, action ->
            ChatListScreen(
                state = state,
                action = action,
            )
        }
    )
}