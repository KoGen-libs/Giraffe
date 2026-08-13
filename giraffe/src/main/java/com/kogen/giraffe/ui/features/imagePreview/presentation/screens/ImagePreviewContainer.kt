package com.kogen.giraffe.ui.features.imagePreview.presentation.screens

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.kogen.giraffe.navigation.popBackSafety
import kz.evko.navigation.annotation.KoGenScreen

/**
 * Full-screen image viewer, opened from a chat message's thumbnail.
 *
 * [filePath] arrives Uri-encoded (see [com.kogen.giraffe.ui.features.chatDetails.presentation.screens.ChatDetailsContainer])
 * since the koGenNavigation route embeds it as a raw query string and an absolute cache path can
 * otherwise carry characters that would confuse route matching.
 */
@KoGenScreen
@Composable
internal fun ImagePreviewContainer(
    navController: NavHostController,
    filePath: String,
) {
    ImagePreviewScreen(
        filePath = Uri.decode(filePath),
        onBack = { navController.popBackSafety() },
    )
}
