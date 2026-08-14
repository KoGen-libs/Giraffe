package com.kogen.giraffe.ui.features.restCallDetails.presentation.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.kogen.giraffe.di.koGenViewModel
import com.kogen.giraffe.navigation.ActionToImagePreview
import com.kogen.giraffe.navigation.ActionToPdfPreview
import com.kogen.giraffe.navigation.ActionToVideoPreview
import com.kogen.giraffe.navigation.navigateSafety
import com.kogen.giraffe.navigation.popBackSafety
import com.kogen.androidarc.ui.ScreenContainerWrapper
import com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi.RestCallDetailsAction
import com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi.RestCallDetailsEffect
import com.kogen.giraffe.ui.features.restCallDetails.presentation.mvi.RestCallDetailsViewModel
import kz.evko.navigation.annotation.KoGenScreen

/**
 * Nav-graph destination for the REST call details screen (registered via `@KoGenScreen` in
 * Giraffe's own generated nav graph) - the HTTP counterpart to
 * [com.kogen.giraffe.ui.features.chatDetails.presentation.screens.ChatDetailsContainer]. Loads
 * [callId]'s details on first composition and maps [RestCallDetailsEffect]s to navigation.
 */
@KoGenScreen
@Composable
internal fun RestCallDetailsContainer(
    navController: NavHostController,
    callId: String,
) {
    ScreenContainerWrapper(
        viewModel = koGenViewModel<RestCallDetailsViewModel>(),
        onEffect = {
            when (it) {
                is RestCallDetailsEffect.NavigateBack -> navController.popBackSafety()
                is RestCallDetailsEffect.ShowImage -> {
                    navController.navigateSafety(ActionToImagePreview(Uri.encode(it.filePath)))
                }
                is RestCallDetailsEffect.ShowVideo -> {
                    navController.navigateSafety(ActionToVideoPreview(Uri.encode(it.filePath)))
                }
                is RestCallDetailsEffect.ShowPdf -> {
                    navController.navigateSafety(ActionToPdfPreview(Uri.encode(it.filePath)))
                }
            }
        },
        screenContent = { state, action ->
            LaunchedEffect(callId) {
                action(RestCallDetailsAction.LoadRestCallDetails(callId))
            }

            RestCallDetailsScreen(
                state = state,
                action = action,
            )
        }
    )
}
