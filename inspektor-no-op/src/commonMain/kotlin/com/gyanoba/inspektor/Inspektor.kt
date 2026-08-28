package com.gyanoba.inspektor

import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.utils.io.KtorDsl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * A configuration for the [Inspektor] plugin.
 *
 * Every option is accepted and then discarded. Nothing here allocates a data source, a repository
 * or a notification manager, and none of the predicates registered via [filter] / [sanitizeHeader]
 * are ever invoked.
 */
@KtorDsl
public class InspektorConfig internal constructor() {

    /**
     * Specifies the logging level. Ignored — this artifact never logs.
     */
    public var level: LogLevel = LogLevel.BODY

    /** The maximum size of the request/response body to log. Ignored. */
    public var maxContentLength: Int = 250_000

    /**
     * The maximum duration for which logs are retained. Ignored — nothing is ever retained.
     *
     * The validation of the real implementation is kept so that a configuration which is invalid in
     * the dev build is also invalid here.
     */
    @UnstableInspektorAPI
    public var retentionDuration: Duration = 30.days
        set(value) {
            if (value < 5.minutes) {
                throw IllegalArgumentException("Retention duration must be at least 5 minutes")
            }
            field = value
        }

    /** Shows a notification when a request is sent. Ignored — no notification is ever shown. */
    @UnstableInspektorAPI
    public var showNotifications: Boolean = true

    /**
     * Allows you to filter log messages for calls matching a [predicate].
     *
     * The predicate is discarded and never invoked.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        // No-op
    }

    /**
     * Allows you to sanitize sensitive headers to avoid their values appearing in the logs.
     *
     * The predicate is discarded and never invoked.
     */
    @Suppress("UNUSED_PARAMETER")
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        // No-op
    }
}

/**
 * A no-op stand-in for the Inspektor Ktor client plugin.
 *
 * Installing it is safe and free: the plugin body registers no hooks, so requests and responses are
 * passed straight through untouched and nothing is ever read, buffered, persisted or logged.
 */
public val Inspektor: ClientPlugin<InspektorConfig> = createClientPlugin(
    "Inspektor", ::InspektorConfig,
) {
    // Intentionally empty. No hooks, no interceptors, no response observer.
}

/**
 * Opens the Inspektor UI. No-op — this artifact ships no UI.
 */
public expect fun openInspektor()
