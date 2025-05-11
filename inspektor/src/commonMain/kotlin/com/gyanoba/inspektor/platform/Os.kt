package com.gyanoba.inspektor.platform

internal expect val currentOs: Os

/**
 * Operating system on which the application is running
 */
internal sealed interface Os {
    data object ANDROID : Os
    data object IOS : Os

    sealed interface Desktop: Os {
        data object WINDOWS : Desktop
        data object MACOS : Desktop
        data object LINUX : Desktop
        data object UNKNOWN : Desktop
    }
}