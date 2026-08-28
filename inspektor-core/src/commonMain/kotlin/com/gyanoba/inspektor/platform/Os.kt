package com.gyanoba.inspektor.platform

import com.gyanoba.inspektor.UnstableInspektorAPI

@UnstableInspektorAPI
public expect val currentOs: Os

/**
 * Operating system on which the application is running
 */
@UnstableInspektorAPI
public sealed interface Os {
    public data object ANDROID : Os
    public data object IOS : Os

    public sealed interface Desktop: Os {
        public data object WINDOWS : Desktop
        public data object MACOS : Desktop
        public data object LINUX : Desktop
        public data object UNKNOWN : Desktop
    }
}