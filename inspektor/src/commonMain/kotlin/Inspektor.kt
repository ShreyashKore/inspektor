import data.InspektorDataSource
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.ClientPluginBuilder
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.KtorDsl
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import utils.HeaderSanitizer
import utils.ReceiveStateHook
import utils.ResponseReceiveHook
import utils.SendMonitoringHook
import utils.approxByteCount
import utils.logErr
import utils.observe
import utils.sanitizeHeaders
import utils.tryReadText
import utils.typeAndSubType

private val ClientCallLogger = AttributeKey<HttpClientCallLogger>("CallLogger")
private val DisableLogging = AttributeKey<Unit>("DisableLogging")

public enum class LogLevel(
    public val info: Boolean = false,
    public val headers: Boolean = false,
    public val body: Boolean = false,
) {
    NONE,
    INFO(info = true),
    HEADERS(info = true, headers = true),
    BODY(info = true, headers = true, body = true)
}


/**
 * A configuration for the [Inspektor] plugin.
 */
@KtorDsl
public class InspektorConfig internal constructor() {
    internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
    internal val headerSanitizers = mutableListOf<HeaderSanitizer>()

    /**
     * Specifies the logging level.
     */
    public var level: LogLevel = LogLevel.BODY

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

@OptIn(InternalAPI::class)
public val Inspektor: ClientPlugin<InspektorConfig> = createClientPlugin(
    "Inspektor", ::InspektorConfig,
) {
    val inspektorDataSource = InspektorDataSource.Instance
    val level: LogLevel = pluginConfig.level
    if (level == LogLevel.NONE) return@createClientPlugin

    val filters: List<(HttpRequestBuilder) -> Boolean> = pluginConfig.filters
    val headerSanitizers: List<HeaderSanitizer> = pluginConfig.headerSanitizers

    fun shouldBeLogged(request: HttpRequestBuilder): Boolean =
        filters.isEmpty() || filters.any { it(request) }

    on(SendMonitoringHook) { request ->
        if (level == LogLevel.NONE) return@on
        if (!shouldBeLogged(request)) {
            request.attributes.put(DisableLogging, Unit)
            return@on
        }
        val callLogger = HttpClientCallLogger(
            inspektorDataSource,
            Dispatchers.IO,
            NotificationManager()
        )
        request.attributes.put(ClientCallLogger, callLogger)
        val content = request.body as OutgoingContent

        callLogger.addRequestInfo(
            url = request.url.toString(),
            host = request.url.host,
            path = request.url.encodedPath,
            scheme = request.url.protocol.name,
            method = request.method.value,
            requestHeadersSize = request.headers.build().approxByteCount(),
            requestContentType = request.contentType()?.typeAndSubType,
            requestPayloadSize = content.contentLength,
            requestDate = Clock.System.now()
        )

        if (level.headers) {
            val requestHeaders =
                Json.encodeToString(
                    request.headers.build().sanitizeHeaders(headerSanitizers).entries()
                )
            callLogger.addRequestHeaders(headers = Json.decodeFromString(requestHeaders))
        }

        val loggedContent = try {
            val charset = content.contentType?.charset() ?: Charsets.UTF_8
            val channel = ByteChannel()
            var requestBody: String? = null
            GlobalScope.launch(Dispatchers.Unconfined) {
                requestBody = channel.tryReadText(charset)
            }.invokeOnCompletion {
                requestBody?.let { callLogger.addRequestBody(it) }
                callLogger.closeRequestLog()
            }
            content.observe(channel)
        } catch (_: Throwable) {
            null
        }

        try {
            proceedWith(loggedContent ?: request.body)
        } catch (cause: Throwable) {
            callLogger.addRequestException(cause)
            throw cause
        } finally {
        }
    }

    on(ReceiveStateHook) { response ->
        if (shouldNotLog(response.call)) return@on

        val callLogger = response.call.attributes[ClientCallLogger]

        var failed = false

        callLogger.addResponseInfo(
            protocol = response.version.toString(),
            responseCode = response.status.value,
            responseContentType = response.contentType()?.typeAndSubType,
            responsePayloadSize = response.contentLength(),
            responseHeadersSize = response.headers.approxByteCount(),
            responseDate = Clock.System.now()
        )

        if (level.headers) {
            val headers = Json.encodeToString(
                response.headers.sanitizeHeaders(headerSanitizers).entries()
            )
            callLogger.addResponseHeaders(headers = Json.decodeFromString(headers))
        }

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
        if (shouldNotLog(call)) return@on
        try {
            proceed()
        } catch (cause: Throwable) {
            val callLogger = call.attributes[ClientCallLogger]
            callLogger.addResponseException(cause)
            callLogger.closeResponseLog()
            throw cause
        }
    }

    if (!level.body) return@createClientPlugin

    val observer: ResponseHandler = observer@{ response ->
        if (shouldNotLog(response.call)) return@observer

        val callLogger = response.call.attributes[ClientCallLogger]
        try {
            val message =
                response.content.tryReadText(response.contentType()?.charset() ?: Charsets.UTF_8)
            message?.let { callLogger.addResponseBody(it) }
        } catch (e: Throwable) {
            logErr(e, "Inspektor") { "Failed to read response body" }
        } finally {
            callLogger.closeResponseLog()
        }
    }

    ResponseObserver.install(ResponseObserver.prepare { onResponse(observer) }, client)
}

private inline fun ClientPluginBuilder<InspektorConfig>.shouldNotLog(call: HttpClientCall): Boolean {
    return pluginConfig.level == LogLevel.NONE && call.attributes.contains(DisableLogging)
}

public expect fun openInspektor()