package com.gyanoba.inspektor.platform

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.gyanoba.inspektor.utils.ContextInitializer
import com.gyanoba.inspektor.utils.logErr

internal actual fun getAppName(): String? {
    return getAppLabel(ContextInitializer.appContext)
}


private fun getAppLabel(context: Context): String? {
    val applicationInfo: ApplicationInfo = try {
        context.packageManager.getApplicationInfo(context.applicationInfo.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        logErr(e, "AppName.android") { "Error getting application label" }
        return null
    }
    return context.packageManager.getApplicationLabel(applicationInfo).toString()
}