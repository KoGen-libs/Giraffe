package com.kogen.giraffe.ui.common.main

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import com.kogen.giraffe.di.setApplicationContext
import com.kogen.giraffe.navigation.ActionToChatDetails
import com.kogen.giraffe.navigation.AppNavHost
import com.kogen.giraffe.navigation.navigateSafety
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Host activity for Giraffe's whole in-app debug UI (chat list, chat details, media previews),
 * declared once in the library's own manifest and launched implicitly - from a traffic
 * notification's tap, or from [GiraffeNotificationService] - rather than something the embedding
 * app starts directly.
 *
 * `EXTRA_CHAT_ID` in the launching [Intent] deep-links straight into that call's details screen,
 * whether the activity is being created fresh or is already on top (`singleTask`/[onNewIntent]).
 */
@SuppressLint("RestrictedApi")
class GiraffeActivity : ComponentActivity() {
    // Replay=1 so a chat ID delivered before the NavHost's collector is up (e.g. arriving with
    // the very Intent that creates this Activity) isn't lost.
    private val _pendingChatId = MutableSharedFlow<String>(replay = 1)
    private val pendingChatId = _pendingChatId.asSharedFlow()

    private fun navigateTo(chatId: String) {
        _pendingChatId.tryEmit(chatId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setApplicationContext(this.applicationContext)
        enableEdgeToEdge(
            statusBarStyle = getStatusBarStyle(),
            navigationBarStyle = getStatusBarStyle(),
        )
        setContent {
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                pendingChatId.collect { chatId ->
                    navController.navigateSafety(ActionToChatDetails(chatId))
                }
            }

            AppNavHost(navController = navController)
        }
        intent?.getStringExtra("EXTRA_CHAT_ID")?.let {
            navigateTo(it)
        }
    }

    /** Status/navigation bar scrim style - always the dark variant, matching this screen's fixed dark color scheme. */
    private fun getStatusBarStyle(): SystemBarStyle {
        val lightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val darkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
        return SystemBarStyle.auto(
            lightScrim = lightScrim,
            darkScrim = darkScrim,
            detectDarkMode = {
                true
            },
        )
    }

    /** Handles a new deep-link Intent arriving while this `singleTask` activity is already running (e.g. tapping another traffic notification). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        intent.getStringExtra("EXTRA_CHAT_ID")?.let {
            navigateTo(it)
        }
    }
}