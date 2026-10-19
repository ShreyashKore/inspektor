package com.gyanoba.inspektor.urlsession

import com.gyanoba.inspektor.InspektorCoreConfig
import com.gyanoba.inspektor.InspektorRequest
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.platform.NotificationManager
import kotlin.time.Duration

/**
 * Configuration for URLSession capture.
 *
 * Deliberately plain: no `suspend` functions, no generics, no sealed types on the surface, so that
 * everything here survives the Objective-C bridge unmangled for a Swift caller.
 */
public class InspektorUrlSessionConfig internal constructor() {
    internal val core: InspektorCoreConfig = InspektorCoreConfig()

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

    // Injection points for tests only, mirroring the other integrations.
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
     *
     * The predicate takes the neutral [InspektorRequest] rather than an `NSURLRequest`, so the same
     * filter reads the same on every platform.
     */
    public fun filter(predicate: (InspektorRequest) -> Boolean) {
        core.filter(predicate)
    }

    /**
     * Replaces the value of headers matching [predicate] with [placeholder] before they are stored.
     */
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        core.sanitizeHeader(placeholder, predicate)
    }
}
