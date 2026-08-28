package com.gyanoba.inspektor.urlsession

import com.gyanoba.inspektor.InspektorRequest
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.darwin.NSObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * A no-op counterpart of Inspektor's URLSession configuration.
 *
 * Every option is accepted and discarded, and neither predicate is ever invoked.
 */
public class InspektorUrlSessionConfig internal constructor() {

    /** Specifies the logging level. Ignored -- this artifact never logs. */
    public var level: LogLevel = LogLevel.BODY

    /** The maximum size of the request/response body to log. Ignored. */
    public var maxContentLength: Int = 250_000

    /**
     * The maximum duration for which logs are retained. Ignored -- nothing is ever retained.
     *
     * The real implementation's validation is kept, so a configuration that is invalid in the dev
     * build is also invalid here.
     */
    @UnstableInspektorAPI
    public var retentionDuration: Duration = 30.days
        set(value) {
            if (value < 5.minutes) {
                throw IllegalArgumentException("Retention duration must be at least 5 minutes")
            }
            field = value
        }

    /** Shows a notification when a request is sent. Ignored. */
    @UnstableInspektorAPI
    public var showNotifications: Boolean = true

    /** The predicate is discarded and never invoked. */
    @Suppress("UNUSED_PARAMETER")
    public fun filter(predicate: (InspektorRequest) -> Boolean) {
        // No-op
    }

    /** The predicate is discarded and never invoked. */
    @Suppress("UNUSED_PARAMETER")
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        // No-op
    }
}

/**
 * A no-op stand-in for Inspektor's URLSession proxy delegate.
 *
 * It implements no delegate method of its own, so `respondsToSelector:` reports false for all of
 * them and URLSession falls back to its default behaviour -- nothing is observed, buffered or
 * persisted.
 */
public class InspektorUrlSessionDelegate internal constructor(
    private val forwardTo: NSURLSessionDataDelegateProtocol?,
) : NSObject(), NSURLSessionDataDelegateProtocol

/** Builds a plain `NSURLSession`. Nothing is recorded. */
public fun inspektorUrlSession(
    configuration: NSURLSessionConfiguration = NSURLSessionConfiguration.defaultSessionConfiguration,
    delegateQueue: NSOperationQueue? = null,
    forwardTo: NSURLSessionDataDelegateProtocol? = null,
    configure: InspektorUrlSessionConfig.() -> Unit = {},
): NSURLSession = NSURLSession.sessionWithConfiguration(
    configuration = configuration,
    delegate = forwardTo,
    delegateQueue = delegateQueue,
)

/** A delegate that records nothing. */
public fun inspektorUrlSessionDelegate(
    forwardTo: NSURLSessionDataDelegateProtocol? = null,
    configure: InspektorUrlSessionConfig.() -> Unit = {},
): InspektorUrlSessionDelegate = InspektorUrlSessionDelegate(forwardTo)

/**
 * A no-op stand-in for Inspektor's opt-in `URLProtocol`.
 *
 * [register] does not register anything, so the URL loading system is untouched.
 */
public object InspektorUrlProtocol {
    /** No-op -- nothing is registered, so no request is intercepted. */
    @Suppress("UNUSED_PARAMETER")
    public fun register(
        loaderConfiguration: NSURLSessionConfiguration =
            NSURLSessionConfiguration.ephemeralSessionConfiguration,
        configure: InspektorUrlSessionConfig.() -> Unit = {},
    ) {
        // No-op
    }

    /** No-op. */
    public fun unregister() {
        // No-op
    }

    /** Always false: this artifact never intercepts a request. */
    @Suppress("UNUSED_PARAMETER")
    public fun canInitWithRequest(request: NSURLRequest): Boolean = false
}
