package platform

import platform.Foundation.NSBundle

internal actual fun getAppName(): String? {
    return NSBundle.mainBundle.applicationName
}

private val NSBundle.applicationName: String?
    get() {
        val displayName = objectForInfoDictionaryKey("CFBundleDisplayName") as? String
        return displayName ?: objectForInfoDictionaryKey("CFBundleName") as? String
    }