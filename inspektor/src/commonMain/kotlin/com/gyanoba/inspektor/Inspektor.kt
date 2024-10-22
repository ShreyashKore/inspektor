package com.gyanoba.inspektor

import androidx.annotation.VisibleForTesting
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.utils.HeaderSanitizer
import com.gyanoba.inspektor.utils.ReceiveStateHook
import com.gyanoba.inspektor.utils.ResponseReceiveHook
import com.gyanoba.inspektor.utils.SendMonitoringHook
import com.gyanoba.inspektor.utils.SendStateHook
import com.gyanoba.inspektor.utils.approxByteCount
import com.gyanoba.inspektor.utils.logErr
import com.gyanoba.inspektor.utils.observe
import com.gyanoba.inspektor.utils.sanitizeHeaders
import com.gyanoba.inspektor.utils.tryReadText
import com.gyanoba.inspektor.utils.typeAndSubType
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.ClientPluginBuilder
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.plugins.observer.wrap
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.content
import io.ktor.client.statement.request
import io.ktor.client.utils.buildHeaders
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes
import io.ktor.util.toMap
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.KtorDsl
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

internal val ClientCallLogger = AttributeKey<HttpClientCallLogger>("CallLogger")
internal val DisableLogging = AttributeKey<Unit>("DisableLogging")

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

    public var maxContentLength: Int = 250_000

    /**
     * The data source to store the logs.
     */
    @VisibleForTesting
    public var dataSource: InspektorDataSource = InspektorDataSourceImpl.Instance

    /**
     * The data source to store overrides.
     */
    @VisibleForTesting
    public var overrideRepository: OverrideRepository = OverrideRepositoryImpl.Instance

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
    val inspektorDataSource = pluginConfig.dataSource
    val level: LogLevel = pluginConfig.level
    if (level == LogLevel.NONE) return@createClientPlugin

    val filters: List<(HttpRequestBuilder) -> Boolean> = pluginConfig.filters
    val headerSanitizers: List<HeaderSanitizer> = pluginConfig.headerSanitizers

    fun shouldBeLogged(request: HttpRequestBuilder): Boolean =
        filters.isEmpty() || filters.any { it(request) }

    on(SendStateHook) { request ->
        if (level == LogLevel.NONE) return@on
        if (!shouldBeLogged(request)) {
            request.attributes.put(DisableLogging, Unit)
            return@on
        }

        val callLogger = HttpClientCallLogger(
            inspektorDataSource, Dispatchers.IO, NotificationManager()
        )
        request.attributes.put(ClientCallLogger, callLogger)

        val override = run {
            val allOverrides = pluginConfig.overrideRepository.all
            allOverrides.firstOrNull {
                it.action.request && (it.type is HttpRequest && it.type.method.name.equals(
                    request.method.value, true
                )) && it.matchers.all { matcher ->
                    matcher.matches(request)
                }
            }
        }

        override?.let {
            when (override.action.type) {
                OverrideAction.Type.FixedRequest, OverrideAction.Type.FixedRequestResponse -> {
                    var replacedBody: String? = null
                    override.action.requestBody?.takeIf { it.isNotEmpty() }?.let { newBody ->
                        replacedBody = (request.body as? TextContent)?.text?.run {
                            substring(0..minOf(lastIndex, pluginConfig.maxContentLength))
                        }
                        request.setBody(
                            TextContent(
                                newBody, request.contentType() ?: ContentType.Text.Any
                            )
                        )
                    }

                    val replacedHeaders = mutableMapOf<String, List<String>>()
                    override.action.requestHeaders.takeIf { it.isNotEmpty() }?.let { newHeaders ->
                        request.apply {
                            newHeaders.forEach { newHeader ->
                                if (headers.contains(newHeader.key)) {
                                    val values = headers.getAll(newHeader.key)
                                    if (values != null) replacedHeaders[newHeader.key] = values
                                }
                                headers.apply {
                                    remove(newHeader.key)
                                    appendAll(newHeader.key, newHeader.value)
                                }
                            }
                        }
                    }

                    callLogger.addOriginalRequest(
                        headers = replacedHeaders.entries, body = replacedBody
                    )
                }

                else -> throw IllegalArgumentException("Unsupported action type")
            }
        }
    }

    on(SendMonitoringHook) { request ->
        if (shouldNotLog(request.attributes)) {
            return@on
        }
        val callLogger = request.attributes[ClientCallLogger]
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
            callLogger.addRequestHeaders(
                headers = request.headers.build().sanitizeHeaders(headerSanitizers).entries()
            )
        }

        val loggedContent = if (level.body) {
            try {
                val charset = content.contentType?.charset() ?: Charsets.UTF_8
                val channel = ByteChannel()
                var requestBody: String? = null
                GlobalScope.launch(Dispatchers.Unconfined) {
                    requestBody = channel.tryReadText(charset, pluginConfig.maxContentLength)
                }.invokeOnCompletion {
                    requestBody?.let { callLogger.addRequestBody(it) }
                }
                content.observe(channel)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }


        try {
            proceedWith(loggedContent ?: request.body)
        } catch (cause: Throwable) {
            callLogger.addRequestException(cause)
            throw cause
        } finally {
            callLogger.closeRequestLog()
        }
    }

    on(ReceiveStateHook) { response ->
        if (shouldNotLog(response.call.attributes)) return@on

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

        try {
            val request = response.request
            val override = run {
                val allOverrides = pluginConfig.overrideRepository.all
                allOverrides.firstOrNull {
                    it.action.response && (it.type is HttpRequest && it.type.method.name.equals(
                        request.method.value, true
                    )) && it.matchers.all { matcher ->
                        matcher.matches(response)
                    }
                }
            }


            if (override == null) {
                proceed()
            } else {
                when (override.action.type) {
                    OverrideAction.Type.FixedResponse, OverrideAction.Type.FixedRequestResponse -> {
                        var replacedBody: String? = null
                        val replacedHeaders = mutableMapOf<String, List<String>>()

                        val newBody: ByteReadChannel? = override.action.responseBody?.takeIf { it.isNotEmpty() }?.let { newBodyString ->
                            replacedBody = response.content.tryReadText(
                                response.charset() ?: Charsets.UTF_8, pluginConfig.maxContentLength
                            )?.run {
                                substring(0..minOf(lastIndex, pluginConfig.maxContentLength))
                            }
                            ByteReadChannel(newBodyString)
                        }

                        val newHeaders =
                            override.action.responseHeaders.takeIf { it.isNotEmpty() }?.let { newHeaders ->
                                val originalHeaders = response.headers
                                newHeaders.forEach { newHeader ->
                                    if (originalHeaders.contains(newHeader.key)) {
                                        val values = originalHeaders.getAll(newHeader.key)
                                        if (values != null) replacedHeaders[newHeader.key] = values
                                    }
                                }
                                buildHeaders {
                                    (originalHeaders.toMap() + newHeaders).forEach {
                                        appendAll(it.key, it.value)
                                    }
                                }
                            }

                        callLogger.addOriginalResponse(
                            headers = replacedHeaders.entries, body = replacedBody
                        )
                        if (level.headers) {
                            callLogger.addResponseHeaders(
                                headers = (newHeaders ?: response.headers).sanitizeHeaders(
                                    headerSanitizers
                                ).entries()
                            )
                        }
                        proceedWith(
                            response.call.wrap(
                                content = newBody ?: response.rawContent,
                                headers = newHeaders ?: response.headers
                            ).response
                        )
                    }

                    else -> throw IllegalArgumentException("Unsupported action type")
                }

            }
        } catch (cause: Throwable) {
            callLogger.addResponseException(cause)
            failed = true
            throw cause
        } finally {
            if (failed || !level.body) callLogger.closeResponseLog()
        }
    }

    on(ResponseReceiveHook) { call ->
        if (shouldNotLog(call.attributes)) return@on
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
        if (shouldNotLog(response.call.attributes)) return@observer

        val callLogger = response.call.attributes[ClientCallLogger]
        try {
            val charset = response.contentType()?.charset() ?: Charsets.UTF_8
            val message = response.content.tryReadText(charset, pluginConfig.maxContentLength)
            message?.let { callLogger.addResponseBody(it) }
        } catch (e: Throwable) {
            logErr(e, "Inspektor") { "Failed to read response body" }
        } finally {
            callLogger.closeResponseLog()
        }
    }

    ResponseObserver.install(ResponseObserver.prepare { onResponse(observer) }, client)
}

private inline fun ClientPluginBuilder<InspektorConfig>.shouldNotLog(attributes: Attributes): Boolean {
    return pluginConfig.level == LogLevel.NONE && attributes.contains(DisableLogging)
}

internal fun Matcher.matches(request: HttpRequestBuilder): Boolean {
    return when (this) {
        is UrlMatcher -> url == request.url.toString()
        is HostMatcher -> host == request.url.host
        is PathMatcher -> path == request.url.encodedPath
        is UrlRegexMatcher -> Regex(url).matches(request.url.toString())
    }
}

internal fun Matcher.matches(response: HttpResponse): Boolean {
    val request = response.request
    return when (this) {
        is UrlMatcher -> url == request.url.toString()
        is HostMatcher -> host == request.url.host
        is PathMatcher -> path == request.url.encodedPath
        is UrlRegexMatcher -> Regex(url).matches(request.url.toString())
    }
}

public expect fun openInspektor()

@RequiresOptIn(
    message = "This API is unstable and may be removed in the future.",
    level = RequiresOptIn.Level.ERROR
)
public annotation class UnstableInspektorAPI