package com.gyanoba.inspektor.okhttp

import com.gyanoba.inspektor.InspektorCoreConfig
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.platform.NotificationManager
import okhttp3.Request
import kotlin.time.Duration

/**
 * Where in OkHttp's chain the interceptor sits. The two see genuinely different traffic, so this
 * is a real choice rather than a detail.
 *
 * See [InspektorInterceptor] for the trade-off.
 */
public enum class InterceptorMode {
    /** `OkHttpClient.Builder.addInterceptor` -- fires once, sees what the app wrote. */
    APPLICATION,

    /** `OkHttpClient.Builder.addNetworkInterceptor` -- sees real wire traffic. */
    NETWORK,
}

/** Configuration for [InspektorInterceptor]. Mirrors the Ktor plugin's `InspektorConfig`. */
public class InspektorOkHttpConfig internal constructor() {
    internal val core: InspektorCoreConfig = InspektorCoreConfig()
    internal val requestFilters: MutableList<(Request) -> Boolean> = mutableListOf()

    /** Specifies the logging level. */
    public var level: LogLevel
        get() = core.level
        set(value) {
            core.level = value
        }

    /** The maximum size of the request/response body to log. */
    public var maxContentLength: Int
        get() = core.maxContentLength
        set(value) {
            core.maxContentLength = value
        }

    /** The maximum duration for which logs are retained. */
    @UnstableInspektorAPI
    public var retentionDuration: Duration
        get() = core.retentionDuration
        set(value) {
            core.retentionDuration = value
        }

    /** Shows a notification when a request is sent. */
    @UnstableInspektorAPI
    public var showNotifications: Boolean
        get() = core.showNotifications
        set(value) {
            core.showNotifications = value
        }

    // Injection points for tests only, mirroring `InspektorConfig` in `:inspektor-ktor`. Kept
    // internal so the no-op twin does not have to mirror core types it must not depend on.
    internal var dataSource: InspektorDataSource
        get() = core.dataSource
        set(value) {
            core.dataSource = value
        }

    internal var overrideRepository: OverrideRepository
        get() = core.overrideRepository
        set(value) {
            core.overrideRepository = value
        }

    internal var notificationManager: NotificationManager
        get() = core.notificationManager
        set(value) {
            core.notificationManager = value
        }

    /**
     * Records calls matching [predicate] only. A call is recorded when no filter is registered, or
     * when any registered filter accepts it.
     */
    public fun filter(predicate: (Request) -> Boolean) {
        requestFilters.add(predicate)
    }

    /**
     * Replaces the value of headers matching [predicate] with [placeholder] before they are stored.
     *
     * ```kotlin
     * sanitizeHeader { header -> header == "Authorization" }
     * ```
     */
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        core.sanitizeHeader(placeholder, predicate)
    }
}
