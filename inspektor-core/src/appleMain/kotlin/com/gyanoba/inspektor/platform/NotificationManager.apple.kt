package com.gyanoba.inspektor.platform

import com.gyanoba.inspektor.utils.InspektorLog
import platform.Foundation.NSError
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter


public actual fun NotificationManager(): NotificationManager {
    return NotificationManagerImpl()
}

internal class NotificationManagerImpl : NotificationManager {
    private val notificationId: String = "com.gyanoba.inspektor.notification"

    override fun notify(title: String, message: String) {
        val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
        // The completion runs later, on a queue of the system's choosing, so nothing here can
        // catch what it throws -- and a Kotlin exception escaping into an Objective-C block
        // terminates the process. Failures are logged, never thrown.
        notificationCenter.requestAuthorizationWithOptions(options = UNAuthorizationOptionAlert + UNAuthorizationOptionBadge + UNAuthorizationOptionSound) { granted, error ->
            if (error != null) {
                InspektorLog.error(error.toKotlinThrowable(), NotificationManager.TAG) {
                    "Error requesting notification permissions: $error"
                }
                return@requestAuthorizationWithOptions
            }
            // The user said no. That is their call, not an error, and there is nothing to post.
            if (!granted) return@requestAuthorizationWithOptions

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
            notificationCenter.addNotificationRequest(request) { addError ->
                if (addError != null) {
                    InspektorLog.error(addError.toKotlinThrowable(), NotificationManager.TAG) {
                        "Error adding notification request: $addError"
                    }
                }
            }
        }
    }
}

internal fun NSError.toKotlinThrowable(): Throwable {
    return Throwable(this.localizedDescription)
}