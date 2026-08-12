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
import com.kogen.giraffe.navigation.ActionToRestCallDetails
import com.kogen.giraffe.navigation.AppNavHost
import com.kogen.giraffe.navigation.navigateSafety
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Which details screen a pending deep-link should land on - carried alongside the id since a bare id string can't tell a gRPC chat from a REST call. */
private data class PendingDetailsTarget(val id: String, val isRestCall: Boolean)

/**
 * Host activity for Giraffe's whole in-app debug UI (chat list, chat details, media previews),
 * declared once in the library's own manifest and launched implicitly - from a traffic
 * notification's tap, or from [GiraffeNotificationService] - rather than something the embedding
 * app starts directly.
 *
 * `EXTRA_CHAT_ID`/`EXTRA_IS_REST_CALL` in the launching [Intent] deep-link straight into that
 * call's details screen - gRPC's or REST's, per the flag - whether the activity is being created
 * fresh or is already on top (`singleTask`/[onNewIntent]).
 */
@SuppressLint("RestrictedApi")
class GiraffeActivity : ComponentActivity() {
    // Replay=1 so a target delivered before the NavHost's collector is up (e.g. arriving with
    // the very Intent that creates this Activity) isn't lost.
    private val _pendingTarget = MutableSharedFlow<PendingDetailsTarget>(replay = 1)
    private val pendingTarget = _pendingTarget.asSharedFlow()

    private fun navigateTo(chatId: String, isRestCall: Boolean) {
        _pendingTarget.tryEmit(PendingDetailsTarget(chatId, isRestCall))
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
                pendingTarget.collect { target ->
                    if (target.isRestCall) {
                        navController.navigateSafety(ActionToRestCallDetails(target.id))
                    } else {
                        navController.navigateSafety(ActionToChatDetails(target.id))
                    }
                }
            }

            AppNavHost(navController = navController)
        }
        intent?.getStringExtra("EXTRA_CHAT_ID")?.let {
            navigateTo(it, intent?.getBooleanExtra("EXTRA_IS_REST_CALL", false) ?: false)
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
            navigateTo(it, intent.getBooleanExtra("EXTRA_IS_REST_CALL", false))
        }
    }
}