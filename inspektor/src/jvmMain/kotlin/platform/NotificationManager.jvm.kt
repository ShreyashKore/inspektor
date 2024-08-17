import utils.log
import java.awt.Image
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File

internal actual fun NotificationManager(): NotificationManager {
    return NotificationManagerImpl()
}


internal class NotificationManagerImpl : NotificationManager {
    override fun notify(title: String, message: String) {
        val os = System.getProperty("os.name") ?: run {
            log("ShowNotifications") { "Unable to determine OS" }
            return
        }

        if (SystemTray.isSupported()) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO)
        } else if (os.contains("Linux")) {
            val builder = ProcessBuilder(
                "zenity",
                "--notification",
                "--text=$title\\n$message"
            )
            builder.inheritIO().start()
        } else if (os.contains("Mac")) {
            val builder = ProcessBuilder(
                "osascript", "-e",
                ("display notification \"" + message + "\""
                        + " with title \"" + title) + "\""
            )
            builder.inheritIO().start()
        } else {
            log("ShowNotifications") { "Unable to show notifications on this OS" }
        }

    }
}


public fun composeDesktopResourcesPath(): String? {
    return runCatching {
        val resourcesDirectory = File(System.getProperty("compose.application.resources.dir"))
        return resourcesDirectory.canonicalPath
    }.getOrNull()
}


private fun tryLoadImage(path: String): Image? {
    return try {
        Toolkit.getDefaultToolkit().getImage(path)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun getImagePath(imageName: String): String {
    return composeDesktopResourcesPath() + File.separator + imageName
}

private val image by lazy {
    tryLoadImage(getImagePath("drawable/bell.png"))
}

private val trayIcon by lazy {
    val tray = SystemTray.getSystemTray()
    val trayIcon = TrayIcon(image, "Inspektor")
    trayIcon.setImageAutoSize(true)
    tray.add(trayIcon)
    trayIcon
}
