package platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.gyanoba.inspektor.MainActivity
import com.gyanoba.inspektor.R
import com.gyanoba.inspektor.utils.ContextInitializer

internal actual fun NotificationManager(): NotificationManager {
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
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
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