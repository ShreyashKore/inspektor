package com.gyanoba.inspektor.platform

internal actual val currentOs: Os
    get() {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> Os.Desktop.WINDOWS
            osName.contains("mac") -> Os.Desktop.MACOS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> Os.Desktop.LINUX
            else -> Os.Desktop.UNKNOWN
        }
    }