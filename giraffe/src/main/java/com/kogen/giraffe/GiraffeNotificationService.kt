package com.kogen.giraffe

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kogen.giraffe.ui.common.main.GiraffeActivity
import kz.evko.kogen_di.annotations.KoGenComponent
import java.util.UUID

// Renamed (was "grpc_traffic_channel") so devices that already created the old channel get a
// fresh one with vibration explicitly off - Android channel settings are locked in at creation
// and can't be changed from code afterwards, only by the user in system settings or by using a
// new channel id.
private const val CHANNEL_ID = "giraffe_traffic_channel_v2"

/** Posts one system notification per gRPC message or finished REST call, tapping it opens [GiraffeActivity] on that call's detail screen. */
@KoGenComponent(true)
internal class GiraffeNotificationService(private val context: Context) {

    init {
        createNotificationChannel(context)
    }

    /**
     * Builds and posts a notification for a single request/response message (gRPC) or a finished
     * call (REST). Uses [notificationId]'s hash as the notification ID so every message belonging
     * to the same call updates the same notification instead of stacking new ones. [isRestCall]
     * tags the tap intent so [GiraffeActivity] deep-links into the matching details screen -
     * `restcalldetails` vs `chatdetails` - instead of always assuming gRPC.
     */
    fun sendTrafficNotification(
        methodName: String,
        host: String,
        message: String,
        notificationId: UUID,
        isRestCall: Boolean = false,
    ) {
        // Protobuf's toString() prefixes messages with a "# comment" header line in some builds;
        // drop it so the notification body starts with actual content.
        val cleanBody = message.lineSequence()
            .dropWhile { it.trim().startsWith("#") }
            .joinToString("\n")

        val appIconResId = context.applicationInfo.icon

        val intent = Intent(context, GiraffeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_CHAT_ID", notificationId.toString())
            putExtra("EXTRA_IS_REST_CALL", isRestCall)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (appIconResId != 0) appIconResId else android.R.drawable.stat_notify_sync)
            .setContentTitle(methodName)
            .setSubText(host)
            .setContentText(cleanBody.lineSequence().firstOrNull())
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId.hashCode(), builder.build())
        }
    }


    /** Registers the high-importance notification channel used for all traffic notifications (a no-op if it already exists). */
    fun createNotificationChannel(context: Context) {
        val name = "gRaffe Traffic"
        val descriptionText = "gRPC traffic interceptor alerts"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            // Every connection notification was buzzing the device, gRPC and REST alike - kept
            // explicitly off rather than left unset, since an unset channel can pick up the
            // device's own default vibration setting instead of truly being silent.
            enableVibration(false)
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}