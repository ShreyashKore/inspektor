package platform

import java.nio.file.Paths

internal fun getAppDataDir(appId: String): String {
    return when (currentOs) {
        DesktopOs.WINDOWS -> {
            // %APPDATA% or a custom directory
            val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
            Paths.get(appData, appId).toString()
        }

        DesktopOs.MACOS -> {
            // ~/Library/Application Support/YourApp
            val homeDir = System.getProperty("user.home")
            Paths.get(homeDir, "Library", "Application Support", appId).toString()
        }

        DesktopOs.LINUX -> {
            // use ~/.local/share/YourApp
            val homeDir = System.getProperty("user.home")
            Paths.get(homeDir, ".local", "share", appId).toString()
        }

        DesktopOs.UNKNOWN -> throw UnsupportedOperationException("Unsupported operating system")
    }
}