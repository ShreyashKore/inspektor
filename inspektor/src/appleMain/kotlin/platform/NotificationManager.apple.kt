import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

internal actual fun NotificationManager(): NotificationManager {
    return NotificationManagerImpl()
}

internal class NotificationManagerImpl : NotificationManager {
    private val notificationId: String = "com.inspektor.notification.main"
    private var created = false

    override fun notify(title: String, message: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(message)
        }

        if (!created) {
            // Create a new notification
            created = true
            val request = UNNotificationRequest.requestWithIdentifier(
                notificationId,
                content,
                UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
            )
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    println("Error adding notification: $error")
                }
            }
        } else {
            // Update the existing notification
            val request = UNNotificationRequest.requestWithIdentifier(
                notificationId,
                content,
                UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, false)
            )
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    println("Error updating notification: $error")
                }
            }
        }
    }
}