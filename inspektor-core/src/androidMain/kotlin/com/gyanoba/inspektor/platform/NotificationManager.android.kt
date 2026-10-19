package com.gyanoba.inspektor.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.gyanoba.inspektor.core.R
import com.gyanoba.inspektor.utils.ContextInitializer

public actual fun NotificationManager(): NotificationManager {
    return NotificationManagerImpl()
}

internal class NotificationManagerImpl : NotificationManager {
    private val notificationManager =
        ContextInitializer.appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    private val notificationId = 244187619 // Random number :)

    override fun notify(title: String, message: String) {
        val context = ContextInitializer.appContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val notification = NotificationCompat.Builder(context, "inspektor")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.inspektor)
            .setOnlyAlertOnce(true)
            .setChannelId(channelId)
            .apply { viewerPendingIntent(context)?.let(::setContentIntent) }
            .build()
        notificationManager.notify(notificationId, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            channelId,
            "Inspektor",
            android.app.NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }
}

private const val channelId = "com.gyanoba.inspektor"

/**
 * An intent that opens the Inspektor UI, or null when `:inspektor-ui` is not on the classpath.
 *
 * Resolved by name rather than by class reference: core must not depend on the UI module, and a
 * headless consumer that only wants capture should still get the notification -- just without a
 * screen to tap through to.
 */
private fun viewerPendingIntent(context: Context): PendingIntent? {
    val intent = Intent().setClassName(context, "com.gyanoba.inspektor.MainActivity")
    if (intent.resolveActivity(context.packageManager) == null) return null
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}