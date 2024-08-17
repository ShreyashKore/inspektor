internal expect fun NotificationManager(): NotificationManager

internal interface NotificationManager {
    fun notify(title: String, message: String)
}