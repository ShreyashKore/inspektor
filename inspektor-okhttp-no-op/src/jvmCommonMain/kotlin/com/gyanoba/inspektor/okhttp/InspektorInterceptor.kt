package com.gyanoba.inspektor.okhttp

import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** No-op counterpart of `com.gyanoba.inspektor.okhttp.InterceptorMode`. */
public enum class InterceptorMode {
    APPLICATION,
    NETWORK,
}

/**
 * A configuration for [InspektorInterceptor].
 *
 * Every option is accepted and then discarded, and none of the predicates registered via [filter] /
 * [sanitizeHeader] is ever invoked.
 */
public class InspektorOkHttpConfig internal constructor() {

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

    /** Shows a notification when a request is sent. Ignored -- no notification is ever shown. */
    @UnstableInspektorAPI
    public var showNotifications: Boolean = true

    /** The predicate is discarded and never invoked. */
    @Suppress("UNUSED_PARAMETER")
    public fun filter(predicate: (Request) -> Boolean) {
        // No-op
    }

    /** The predicate is discarded and never invoked. */
    @Suppress("UNUSED_PARAMETER")
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        // No-op
    }
}

/**
 * A no-op stand-in for Inspektor's OkHttp interceptor.
 *
 * It proceeds with the chain and returns the response untouched: no body is peeked, no header is
 * read, nothing is persisted.
 */
public class InspektorInterceptor internal constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}

/**
 * Adds the no-op interceptor to this client.
 *
 * Registering it is safe and free -- it forwards every call straight through.
 */
@Suppress("UNUSED_PARAMETER")
public fun OkHttpClient.Builder.installInspektor(
    mode: InterceptorMode = InterceptorMode.APPLICATION,
    configure: InspektorOkHttpConfig.() -> Unit = {},
): OkHttpClient.Builder = this
