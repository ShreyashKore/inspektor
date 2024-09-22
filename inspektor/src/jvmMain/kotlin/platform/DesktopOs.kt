package platform

internal enum class DesktopOs {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN
}

internal val currentOs: DesktopOs
    get() {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> DesktopOs.WINDOWS
            osName.contains("mac") -> DesktopOs.MACOS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> DesktopOs.LINUX
            else -> DesktopOs.UNKNOWN
        }
    }