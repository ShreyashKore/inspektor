package com.gyanoba.inspektor.platform

import com.gyanoba.inspektor.data.APPLICATION_ID
import java.nio.file.Paths

internal actual fun getAppDataDir(): String {
    require(APPLICATION_ID != null) {
        "Application ID must be provided for the desktop platforms"
    }
    val appId = APPLICATION_ID!!
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