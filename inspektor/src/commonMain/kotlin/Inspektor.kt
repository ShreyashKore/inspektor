import data.InspektorDataSource
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.KtorDsl
import io.ktor.utils.io.charsets.Charsets
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val ClientCallLogger = AttributeKey<HttpClientCallLogger>("CallLogger")
private val DisableLogging = AttributeKey<Unit>("DisableLogging")

/**
 * A configuration for the [Inspektor] plugin.
 */
@KtorDsl
public class InspektorConfig internal constructor() {
    internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
    internal val headerSanitizers = mutableListOf<HeaderSanitizer>()

    private var _logger: Logger? = null

    /**
     * Specifies a [Logger] instance.
     */
    public var logger: Logger
        get() = _logger ?: Logger.DEFAULT
        set(value) {
            _logger = value
        }

    /**
     * Specifies the logging level.
     */
    public var level: LogLevel = LogLevel.HEADERS

    /**
     * Allows you to filter log messages for calls matching a [predicate].
     */
    public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        filters.add(predicate)
    }

    /**
     * Allows you to sanitize sensitive headers to avoid their values appearing in the logs.
     * In the example below, Authorization header value will be replaced with '***' when logging:
     * ```kotlin
     * sanitizeHeader { header -> header == HttpHeaders.Authorization }
     * ```
     */
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        headerSanitizers.add(HeaderSanitizer(placeholder, predicate))
    }
}


public val Inspektor: ClientPlugin<InspektorConfig> =
    createClientPlugin("Inspektor", ::InspektorConfig) {
        val inspektorDataSource = InspektorDataSource.Instance
        val level: LogLevel = pluginConfig.level
        if (level == LogLevel.NONE) return@createClientPlugin

        val filters: List<(HttpRequestBuilder) -> Boolean> = pluginConfig.filters
        val headerSanitizers: List<HeaderSanitizer> = pluginConfig.headerSanitizers

        fun shouldBeLogged(request: HttpRequestBuilder): Boolean =
            filters.isEmpty() || filters.any { it(request) }

        on(SendMonitoringHook) { request ->
            if (!shouldBeLogged(request)) {
                request.attributes.put(DisableLogging, Unit)
                return@on
            }
            val callLogger = HttpClientCallLogger(inspektorDataSource)
            request.attributes.put(ClientCallLogger, callLogger)
            val loggedRequest = try {
                callLogger.logRequestReturnContent(request, level, headerSanitizers)
            } catch (_: Throwable) {
                null
            }

            try {
                proceedWith(loggedRequest ?: request.body)
            } catch (cause: Throwable) {
                callLogger.addRequestException(cause)
                throw cause
            } finally {
            }
        }

        on(ReceiveStateHook) { response ->
            if (level == LogLevel.NONE || response.call.attributes.contains(DisableLogging)) return@on

            val callLogger = response.call.attributes[ClientCallLogger]

            var failed = false
            val responseCode = if (level.info) response.status.value else null
            val headers = if (level.headers)
                Json.encodeToString(
                    response.headers.sanitizeHeaders(headerSanitizers).entries()
                )
            else null
            callLogger.addResponseHeader(
                responseCode = responseCode,
                responseHeaders = headers,
                responseDate = Clock.System.now()
            )
            try {
                proceed()
            } catch (cause: Throwable) {
                callLogger.addResponseException(cause)
                failed = true
                throw cause
            } finally {
                if (failed || !level.body) callLogger.closeResponseLog()
            }
        }

        on(ResponseReceiveHook) { call ->
            if (level == LogLevel.NONE || call.attributes.contains(DisableLogging)) return@on
            try {
                proceed()
            } catch (cause: Throwable) {
                val callLogger = call.attributes[ClientCallLogger]
                if (level.info) {
                    callLogger.addResponseException(cause)
                }
                callLogger.closeResponseLog()
                throw cause
            }
        }

        if (!level.body) return@createClientPlugin

        @OptIn(InternalAPI::class)
        val observer: ResponseHandler = observer@{
            if (level == LogLevel.NONE || it.call.attributes.contains(DisableLogging))
                return@observer

            val callLogger = it.call.attributes[ClientCallLogger]
            try {
                val message = it.content.tryReadText(it.contentType()?.charset() ?: Charsets.UTF_8)
                    ?: "[response body omitted]"
                callLogger.addResponseBody(message, it.contentType())
            } catch (_: Throwable) {
            } finally {
                callLogger.closeResponseLog()
            }
        }

        ResponseObserver.install(ResponseObserver.prepare { onResponse(observer) }, client)
    }

