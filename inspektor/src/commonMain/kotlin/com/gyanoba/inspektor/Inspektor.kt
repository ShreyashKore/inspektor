package com.gyanoba.inspektor

import androidx.annotation.VisibleForTesting
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideEngine
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.utils.ReceiveStateHook
import com.gyanoba.inspektor.utils.ResponseReceiveHook
import com.gyanoba.inspektor.utils.SendMonitoringHook
import com.gyanoba.inspektor.utils.SendStateHook
import com.gyanoba.inspektor.utils.approxByteCount
import com.gyanoba.inspektor.utils.logErr
import com.gyanoba.inspektor.utils.observe
import com.gyanoba.inspektor.utils.toHeaderMap
import com.gyanoba.inspektor.utils.toInspektorRequest
import com.gyanoba.inspektor.utils.tryReadText
import com.gyanoba.inspektor.utils.typeAndSubType
import io.ktor.client.call.replaceResponse
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.ClientPluginBuilder
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
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
import io.ktor.utils.io.KtorDsl
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration

internal val ClientCallLogger = AttributeKey<TransactionRecorder>("CallLogger")
internal val DisableLogging = AttributeKey<Unit>("DisableLogging")

private const val NOTIFICATION_TITLE = "Recording Ktor Activity"

/**
 * A configuration for the [Inspektor] plugin.
 *
 * Every setting is stored on a shared [InspektorCoreConfig]; this class only adds the Ktor-typed
 * conveniences (a `filter` over `HttpRequestBuilder`) that existing consumers already write.
 */
@KtorDsl
public class InspektorConfig internal constructor() {
    internal val core = InspektorCoreConfig()

    /**
     * Specifies the logging level.
     */
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

    @UnstableInspektorAPI
    /** The maximum duration for which logs are retained. */
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

    /**
     * The data source to store the logs.
     */
    @VisibleForTesting
    internal var dataSource: InspektorDataSource
        get() = core.dataSource
        set(value) {
            core.dataSource = value
        }

    /**
     * The data source to store overrides.
     */
    @VisibleForTesting
    internal var overrideRepository: OverrideRepository
        get() = core.overrideRepository
        set(value) {
            core.overrideRepository = value
        }

    @VisibleForTesting
    internal var notificationManager: NotificationManager
        get() = core.notificationManager
        set(value) {
            core.notificationManager = value
        }

    internal val ktorFilters: MutableList<(HttpRequestBuilder) -> Boolean> = mutableListOf()

    /**
     * Allows you to filter log messages for calls matching a [predicate].
     *
     * Kept Ktor-typed so existing consumer source keeps compiling. It sits alongside the neutral
     * `InspektorCoreConfig.filter`; a call is recorded when no filter of either kind is registered,
     * or when any one of them accepts it.
     */
    public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        ktorFilters.add(predicate)
    }

    /**
     * Allows you to sanitize sensitive headers to avoid their values appearing in the logs.
     * In the example below, Authorization header value will be replaced with '***' when logging:
     * ```kotlin
     * sanitizeHeader { header -> header == HttpHeaders.Authorization }
     * ```
     */
    public fun sanitizeHeader(placeholder: String = "***", predicate: (String) -> Boolean) {
        core.sanitizeHeader(placeholder, predicate)
    }
}

@OptIn(DelicateCoroutinesApi::class, UnstableInspektorAPI::class)
public val Inspektor: ClientPlugin<InspektorConfig> = createClientPlugin(
    "Inspektor", ::InspektorConfig,
) {
    val config = pluginConfig.core
    val level: LogLevel = config.level
    if (level == LogLevel.NONE) return@createClientPlugin

    val ktorFilters = pluginConfig.ktorFilters
    val recorders = config.recorderFactory(NOTIFICATION_TITLE)
    val overrideEngine = OverrideEngine(config.overrideRepository)
    val retentionManger = RetentionManager(
        retentionDuration = config.retentionDuration,
        dataSource = config.dataSource,
    )

    fun shouldBeLogged(request: HttpRequestBuilder, view: InspektorRequest): Boolean =
        if (ktorFilters.isEmpty() && !config.hasFilters) true
        else ktorFilters.any { it(request) } || config.matchesAnyFilter(view)

    on(SendStateHook) { request ->
        if (level == LogLevel.NONE) return@on

        val inspektorRequest = request.toInspektorRequest()
        if (!shouldBeLogged(request, inspektorRequest)) {
            request.attributes.put(DisableLogging, Unit)
            return@on
        }

        val callLogger = recorders.newRecorder()
        request.attributes.put(ClientCallLogger, callLogger)
        retentionManger.checkAndCleanUp()

        val override = overrideEngine.findRequestOverride(inspektorRequest) ?: return@on

        when (override.action.type) {
            OverrideAction.Type.FixedRequest, OverrideAction.Type.FixedRequestResponse -> {
                var originalBody: String? = null
                override.action.requestBody?.takeIf { it.isNotEmpty() }?.let { newBody ->
                    originalBody = (request.body as? TextContent)?.text?.run {
                        substring(0..minOf(lastIndex, config.maxContentLength))
                    }
                    request.setBody(
                        TextContent(
                            newBody, request.contentType() ?: ContentType.Text.Any
                        )
                    )
                }

                val originalHeaders = mutableMapOf<String, List<String>>()
                override.action.requestHeaders.takeIf { it.isNotEmpty() }?.let { overrideHeaders ->
                    request.apply {
                        overrideHeaders.forEach { overrideHeader ->
                            val isOverriding = headers.contains(overrideHeader.key) &&
                                headers.getAll(overrideHeader.key) != overrideHeader.value
                            if (isOverriding) {
                                val values = headers.getAll(overrideHeader.key)!!
                                originalHeaders[overrideHeader.key] = values
                            }
                            headers.apply {
                                remove(overrideHeader.key)
                                appendAll(overrideHeader.key, overrideHeader.value)
                            }
                        }
                    }
                }

                callLogger.addOriginalRequest(headers = originalHeaders, body = originalBody)
            }

            else -> throw IllegalArgumentException("Unsupported action type")
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
            callLogger.addRequestHeaders(headers = request.headers.build().toHeaderMap())
        }

        val loggedContent = if (level.body) {
            try {
                val charset = content.contentType?.charset() ?: Charsets.UTF_8
                val channel = ByteChannel()
                var requestBody: String? = null
                GlobalScope.launch(Dispatchers.Unconfined) {
                    requestBody = channel.tryReadText(charset, config.maxContentLength)
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
            val override = overrideEngine.findResponseOverride(
                response.request.toInspektorRequest()
            )

            if (override == null) {
                if (level.headers) {
                    callLogger.addResponseHeaders(headers = response.headers.toHeaderMap())
                }
                proceed()
            } else {
                when (override.action.type) {
                    OverrideAction.Type.FixedResponse, OverrideAction.Type.FixedRequestResponse -> {
                        var originalBody: String? = null
                        val originalHeaders = mutableMapOf<String, List<String>>()
                        val originalChannel = response.bodyAsChannel()

                        val newBody: String? = override.action.responseBody
                            ?.takeIf { it.isNotEmpty() }?.let { newBodyString ->
                                originalBody = originalChannel.tryReadText(
                                    response.charset() ?: Charsets.UTF_8, config.maxContentLength
                                )?.run {
                                    substring(0..minOf(lastIndex, config.maxContentLength))
                                }
                                newBodyString
                            }

                        val newHeaders = override.action.responseHeaders.takeIf { it.isNotEmpty() }
                            ?.let { overrideHeaders ->
                                val responseHeaders = response.headers
                                // before overriding headers, store the original headers
                                overrideHeaders.forEach { overrideHeader ->
                                    val isOverriding =
                                        responseHeaders.contains(overrideHeader.key) &&
                                            responseHeaders.getAll(overrideHeader.key) != overrideHeader.value
                                    if (isOverriding) {
                                        val values = responseHeaders.getAll(overrideHeader.key)!!
                                        originalHeaders[overrideHeader.key] = values
                                    }
                                }
                                buildHeaders {
                                    (responseHeaders.toMap() + overrideHeaders).forEach {
                                        appendAll(it.key, it.value)
                                    }
                                }
                            }

                        callLogger.addOriginalResponse(
                            headers = originalHeaders, body = originalBody
                        )
                        if (level.headers) {
                            callLogger.addResponseHeaders(
                                headers = (newHeaders ?: response.headers).toHeaderMap()
                            )
                        }
                        proceedWith(
                            response.call.replaceResponse(headers = newHeaders ?: response.headers) {
                                newBody?.let(::ByteReadChannel) ?: originalChannel
                            }.response
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
            val message = response.bodyAsChannel().tryReadText(charset, config.maxContentLength)
            message?.let { callLogger.addResponseBody(it) }
        } catch (e: Throwable) {
            logErr(e, "Inspektor") { "Failed to read response body" }
        } finally {
            callLogger.closeResponseLog()
        }
    }

    ResponseObserver.install(ResponseObserver.prepare { onResponse(observer) }, client)
}

private fun ClientPluginBuilder<InspektorConfig>.shouldNotLog(attributes: Attributes): Boolean {
    return pluginConfig.level == LogLevel.NONE || attributes.contains(DisableLogging)
}
