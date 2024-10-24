package com.gyanoba.inspektor.platform

import com.gyanoba.inspektor.utils.ContextInitializer.Companion.appContext

internal actual fun getAppDataDir(): String {
    return appContext.dataDir.absolutePath
}