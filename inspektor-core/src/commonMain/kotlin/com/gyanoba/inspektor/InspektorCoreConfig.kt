package com.gyanoba.inspektor

import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.utils.HeaderSanitizer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * A client-library-neutral view of an outgoing request, used for filtering and override matching.
 *
 * Every integration can build one of these: [method], [url], [host] and [path] are the only things
 * the override matchers ever look at, and [headers] is what filters need.
 */
@UnstableInspektorAPI
public class InspektorRequest(
    public val method: String,
    public val url: String,
    public val host: String,
    public val path: String,
    public val headers: Map<String, List<String>> = emptyMap(),
)

/**
 * The settings shared by every integration.
 *
 * Integration configs (`InspektorConfig` for Ktor, and the equivalents for OkHttp / URLSession)
 * delegate to an instance of this rather than extending it, so each can keep whatever
 * client-typed conveniences its users expect while the behaviour stays in one place.
 */
@UnstableInspektorAPI
public class InspektorCoreConfig {
    internal val filters: MutableList<(InspektorRequest) -> Boolean> = mutableListOf()
    internal val headerSanitizers: MutableList<HeaderSanitizer> = mutableListOf()

    /** Specifies the logging level. */
    public var level: LogLevel = LogLevel.BODY

    /** The maximum size of the request/response body to log. */
    public var maxContentLength: Int = 250_000

    /** The maximum duration for which logs are retained. */
    public var retentionDuration: Duration = 30.days
        set(value) {
            if (value < 5.minutes) {
                throw IllegalArgumentException("Retention duration must be at least 5 minutes")
            }
            field = value
        }

    /** Shows a notification when a request is sent. */
    public var showNotifications: Boolean = true

    /**
     * Where transactions are stored. Defaults to the on-device database on first read, so tests can
     * substitute a fake by assigning before the first call is made.
     */
    public var dataSource: InspektorDataSource
        get() = _dataSource ?: InspektorDataSourceImpl.Instance.also { _dataSource = it }
        set(value) {
            _dataSource = value
        }
    private var _dataSource: InspektorDataSource? = null

    /** Where overrides are stored. Lazily defaulted like [dataSource]. */
    public var overrideRepository: OverrideRepository
        get() = _overrideRepository ?: OverrideRepositoryImpl.Instance.also {
            _overrideRepository = it
        }
        set(value) {
            _overrideRepository = value
        }
    private var _overrideRepository: OverrideRepository? = null

    /** How "a request was recorded" is surfaced to the user. Lazily defaulted like [dataSource]. */
    public var notificationManager: NotificationManager
        get() = _notificationManager ?: NotificationManager().also { _notificationManager = it }
        set(value) {
            _notificationManager = value
        }
    private var _notificationManager: NotificationManager? = null

    /** Records calls only when at least one [predicate] matches. */
    public fun filter(predicate: (InspektorRequest) -> Boolean) {
        filters.add(predicate)
    }

    /**
     * Replaces the value of headers matching [predicate] with [placeholder] before they are stored.
     *
     * Applied inside [TransactionRecorder], so every integration gets it for free and none of them
     * can forget it.
     */
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        headerSanitizers.add(HeaderSanitizer(placeholder, predicate))
    }

    /** True when at least one neutral filter has been registered. */
    public val hasFilters: Boolean get() = filters.isNotEmpty()

    /** True when any registered neutral filter accepts [request]. */
    public fun matchesAnyFilter(request: InspektorRequest): Boolean = filters.any { it(request) }

    /** True when no filter was registered, or when at least one accepts [request]. */
    public fun shouldRecord(request: InspektorRequest): Boolean =
        filters.isEmpty() || matchesAnyFilter(request)

    /** Builds the recorder factory an integration should use for this configuration. */
    public fun recorderFactory(notificationTitle: String): RecorderFactory = RecorderFactory(
        dataSource = dataSource,
        notificationManager = if (showNotifications) notificationManager else null,
        notificationTitle = notificationTitle,
        headerSanitizers = headerSanitizers.toList(),
    )
}
