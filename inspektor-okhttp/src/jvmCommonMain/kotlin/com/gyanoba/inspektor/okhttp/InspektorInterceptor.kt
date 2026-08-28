package com.gyanoba.inspektor.okhttp

import com.gyanoba.inspektor.InspektorRequest
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.RetentionManager
import com.gyanoba.inspektor.TransactionRecorder
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideEngine
import com.gyanoba.inspektor.utils.approxByteCount
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.time.Clock

private const val NOTIFICATION_TITLE = "Recording OkHttp Activity"

/**
 * Records every call through an [OkHttpClient] into Inspektor's store, and applies any matching
 * override.
 *
 * Register it with [installInspektor] rather than constructing it directly, which also forces the
 * choice of where it sits:
 *
 * - As an **application** interceptor it fires exactly once per call and sees the request as the
 *   app wrote it -- but it reports a cache hit as if it were a network call, and it never sees the
 *   individual hops of a redirect chain.
 * - As a **network** interceptor it sees real wire traffic, including `Content-Encoding: gzip` and
 *   every redirect hop -- but it does not fire at all on a cache hit, and it fires more than once
 *   per call when there are redirects or retries.
 *
 * Only a network interceptor has a connection, so TLS version and cipher suite are recorded in
 * that position only.
 *
 * Everything here is deliberately blocking-safe: [TransactionRecorder] never suspends, and
 * retention cleanup is fired and forgotten.
 */
public class InspektorInterceptor internal constructor(
    private val config: InspektorOkHttpConfig,
) : Interceptor {

    private val core = config.core
    private val recorders = core.recorderFactory(NOTIFICATION_TITLE)
    private val overrideEngine = OverrideEngine(core.overrideRepository)
    private val retentionManager = RetentionManager(
        retentionDuration = core.retentionDuration,
        dataSource = core.dataSource,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val level = core.level
        val request = chain.request()
        if (level == LogLevel.NONE) return chain.proceed(request)

        val view = request.toInspektorRequest()
        if (!shouldRecord(request, view)) return chain.proceed(request)

        val recorder = recorders.newRecorder()
        retentionManager.checkAndCleanUpAsync()

        val sendRequest = applyRequestOverride(request, recorder, view)
        recordRequest(sendRequest, recorder, level)
        recorder.closeRequestLog()

        val response = try {
            chain.proceed(sendRequest)
        } catch (cause: Throwable) {
            recorder.addResponseException(cause)
            recorder.closeResponseLog()
            throw cause
        }

        recordTls(chain, recorder)
        return try {
            recordResponse(response, recorder, level, view)
        } finally {
            recorder.closeResponseLog()
        }
    }

    private fun shouldRecord(request: Request, view: InspektorRequest): Boolean {
        val filters = config.requestFilters
        if (filters.isEmpty() && !core.hasFilters) return true
        return filters.any { it(request) } || core.matchesAnyFilter(view)
    }

    // ---- request ---------------------------------------------------------------------------

    private fun applyRequestOverride(
        request: Request,
        recorder: TransactionRecorder,
        view: InspektorRequest,
    ): Request {
        val override = overrideEngine.findRequestOverride(view) ?: return request
        if (!override.action.isFixedRequest) return request

        var built = request.newBuilder()
        var originalBody: String? = null

        override.action.requestBody?.takeIf { it.isNotEmpty() }?.let { newBody ->
            val contentType = request.body?.contentType()
            originalBody = request.body?.peekText(core.maxContentLength)
            built = built.method(request.method, newBody.toRequestBody(contentType))
        }

        val originalHeaders = mutableMapOf<String, List<String>>()
        override.action.requestHeaders.forEach { (name, values) ->
            val existing = request.headers.values(name)
            if (existing.isNotEmpty() && existing != values) originalHeaders[name] = existing
            built = built.removeHeader(name)
            values.forEach { built = built.addHeader(name, it) }
        }

        recorder.addOriginalRequest(headers = originalHeaders, body = originalBody)
        return built.build()
    }

    private fun recordRequest(request: Request, recorder: TransactionRecorder, level: LogLevel) {
        val headers = request.headers.toHeaderMap()
        recorder.addRequestInfo(
            url = request.url.toString(),
            host = request.url.host,
            path = request.url.encodedPath,
            scheme = request.url.scheme,
            method = request.method,
            requestHeadersSize = headers.approxByteCount(),
            requestContentType = request.body?.contentType()?.typeAndSubType,
            requestPayloadSize = request.body?.contentLength()?.takeIf { it >= 0 },
            requestDate = Clock.System.now(),
        )
        if (level.headers) recorder.addRequestHeaders(headers)
        if (level.body) {
            request.body?.peekText(core.maxContentLength)?.let(recorder::addRequestBody)
        }
    }

    // ---- response --------------------------------------------------------------------------

    private fun recordTls(chain: Interceptor.Chain, recorder: TransactionRecorder) {
        // Only a network interceptor has a connection. These two columns have existed in the
        // schema since the beginning and OkHttp is the first integration able to fill them.
        val handshake = chain.connection()?.handshake() ?: return
        recorder.addTlsInfo(
            tlsVersion = handshake.tlsVersion.javaName,
            cipherSuite = handshake.cipherSuite.javaName,
        )
    }

    private fun recordResponse(
        response: Response,
        recorder: TransactionRecorder,
        level: LogLevel,
        view: InspektorRequest,
    ): Response {
        val headers = response.headers.toHeaderMap()
        recorder.addResponseInfo(
            protocol = response.protocol.toString(),
            responseCode = response.code,
            responseContentType = response.body.contentType()?.typeAndSubType,
            responsePayloadSize = response.body.contentLength().takeIf { it >= 0 },
            responseHeadersSize = headers.approxByteCount(),
            responseDate = Clock.System.now(),
        )

        val override = overrideEngine.findResponseOverride(view)
            ?.takeIf { it.action.isFixedResponse }

        if (override == null) {
            if (level.headers) recorder.addResponseHeaders(headers)
            if (level.body) peekResponseBody(response)?.let(recorder::addResponseBody)
            return response
        }
        return applyResponseOverride(response, recorder, level, override, headers)
    }

    private fun applyResponseOverride(
        response: Response,
        recorder: TransactionRecorder,
        level: LogLevel,
        override: Override,
        headers: Map<String, List<String>>,
    ): Response {
        val newBodyText = override.action.responseBody?.takeIf { it.isNotEmpty() }
        val originalBody = if (newBodyText != null) peekResponseBody(response) else null

        val originalHeaders = mutableMapOf<String, List<String>>()
        var newHeaders: Headers? = null
        override.action.responseHeaders.takeIf { it.isNotEmpty() }?.let { overrideHeaders ->
            val builder = response.headers.newBuilder()
            overrideHeaders.forEach { (name, values) ->
                val existing = response.headers.values(name)
                if (existing.isNotEmpty() && existing != values) originalHeaders[name] = existing
                builder.removeAll(name)
                values.forEach { builder.add(name, it) }
            }
            newHeaders = builder.build()
        }

        recorder.addOriginalResponse(headers = originalHeaders, body = originalBody)

        val effectiveHeaders = newHeaders?.toHeaderMap() ?: headers
        if (level.headers) recorder.addResponseHeaders(effectiveHeaders)

        if (newBodyText == null) {
            if (level.body) peekResponseBody(response)?.let(recorder::addResponseBody)
            return newHeaders?.let { response.newBuilder().headers(it).build() } ?: response
        }

        if (level.body) recorder.addResponseBody(newBodyText)

        val contentType = response.body.contentType()
            ?: effectiveHeaders["Content-Type"]?.firstOrNull()?.toMediaTypeOrNull()
        val replaced = response.newBuilder()
            .apply { newHeaders?.let(::headers) }
            .body(newBodyText.toResponseBody(contentType))
            .build()
        // The original body was only peeked, never consumed; close it now that it is unreachable.
        response.body.close()
        return replaced
    }

    /** `peekBody` exists precisely for this: it never consumes the body the consumer will read. */
    private fun peekResponseBody(response: Response): String? = try {
        response.peekBody(core.maxContentLength.toLong()).string()
    } catch (_: Throwable) {
        null
    }
}

private val OverrideAction.isFixedRequest: Boolean
    get() = type == OverrideAction.Type.FixedRequest ||
        type == OverrideAction.Type.FixedRequestResponse

private val OverrideAction.isFixedResponse: Boolean
    get() = type == OverrideAction.Type.FixedResponse ||
        type == OverrideAction.Type.FixedRequestResponse

/**
 * Adds Inspektor to this client.
 *
 * ```kotlin
 * val client = OkHttpClient.Builder()
 *     .installInspektor {
 *         level = LogLevel.BODY
 *         sanitizeHeader { it == "Authorization" }
 *     }
 *     .build()
 * ```
 *
 * [mode] decides whether the interceptor is an application or a network interceptor; see
 * [InspektorInterceptor] for what each one can and cannot see. The default, [InterceptorMode.APPLICATION],
 * matches what most people mean by "log my requests": one record per call, showing what the app asked for.
 */
public fun OkHttpClient.Builder.installInspektor(
    mode: InterceptorMode = InterceptorMode.APPLICATION,
    configure: InspektorOkHttpConfig.() -> Unit = {},
): OkHttpClient.Builder {
    val interceptor = InspektorInterceptor(InspektorOkHttpConfig().apply(configure))
    return when (mode) {
        InterceptorMode.APPLICATION -> addInterceptor(interceptor)
        InterceptorMode.NETWORK -> addNetworkInterceptor(interceptor)
    }
}
