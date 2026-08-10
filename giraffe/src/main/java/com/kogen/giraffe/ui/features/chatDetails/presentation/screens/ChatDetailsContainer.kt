package com.kogen.giraffe.ui.features.chatDetails.presentation.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.kogen.giraffe.di.koGenViewModel
import com.kogen.giraffe.navigation.ActionToImagePreview
import com.kogen.giraffe.navigation.ActionToVideoPreview
import com.kogen.giraffe.navigation.navigateSafety
import com.kogen.giraffe.navigation.popBackSafety
import com.kogen.giraffe.ui.common.ScreenContainerWrapper
import com.kogen.giraffe.ui.features.chatDetails.presentation.mvi.ChatDetailsAction
import com.kogen.giraffe.ui.features.chatDetails.presentation.mvi.ChatDetailsEffect
import com.kogen.giraffe.ui.features.chatDetails.presentation.mvi.ChatDetailsViewModel
import kz.evko.navigation.annotation.KoGenScreen

/**
 * Nav-graph destination for the chat details screen (registered via `@KoGenScreen` in Giraffe's
 * own generated nav graph, not something the embedding app navigates to directly). Loads
 * [chatId]'s details on first composition and maps [ChatDetailsEffect]s to navigation.
 */
@KoGenScreen
@Composable
fun ChatDetailsContainer(
    navController: NavHostController,
    chatId: String,
) {
    ScreenContainerWrapper(
        viewModel = koGenViewModel<ChatDetailsViewModel>(),
        onEffect = {
            when (it) {
                is ChatDetailsEffect.NavigateBack -> navController.popBackSafety()
                is ChatDetailsEffect.ShowImage -> {
                    navController.navigateSafety(ActionToImagePreview(Uri.encode(it.filePath)))
                }
                is ChatDetailsEffect.ShowVideo -> {
                    navController.navigateSafety(ActionToVideoPreview(Uri.encode(it.filePath)))
                }
            }
        },
        screenContent = { state, action ->
            LaunchedEffect(chatId) {
                action(ChatDetailsAction.LoadChatDetails(chatId))
            }

            ChatDetailsScreen(
                state = state,
                action = action,
            )
        }
    )
}