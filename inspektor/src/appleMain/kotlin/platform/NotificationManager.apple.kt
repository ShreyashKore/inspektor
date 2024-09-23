package platform

import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import utils.logErr


internal actual fun NotificationManager(): NotificationManager {
    return NotificationManagerImpl()
}

internal class NotificationManagerImpl : NotificationManager {
    private val notificationId: String = "com.gyanoba.inspektor.notification"

    override fun notify(title: String, message: String) {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        try {
            // Requesting notification permissions
            notificationCenter.requestAuthorizationWithOptions(options = UNAuthorizationOptionAlert + UNAuthorizationOptionBadge + UNAuthorizationOptionSound) { granted, error ->
                if (!granted || error != null) throw IllegalStateException(
                    error?.localizedDescription ?: "Error requesting notification permissions."
                )
            }

            val content = UNMutableNotificationContent().apply {
                setTitle(title)
                setBody(message)
            }

            // Create a new notification
            val request = UNNotificationRequest.requestWithIdentifier(
                notificationId,
                content,
                null
            )
            notificationCenter.addNotificationRequest(request) { error ->
                if (error != null) {
                    logErr(error.toKotlinThrowable(), NotificationManager.TAG) {
                        "Error adding notification request: $error"
                    }
                }
            }
        } catch (e: IllegalStateException) {
            logErr(e, NotificationManager.TAG)
        }
    }
}

internal fun NSError.toKotlinThrowable(): Throwable {
    return Throwable(this.localizedDescription)
}