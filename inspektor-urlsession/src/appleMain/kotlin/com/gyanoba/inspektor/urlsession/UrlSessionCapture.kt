package com.gyanoba.inspektor.urlsession

import com.gyanoba.inspektor.InspektorRequest
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.RetentionManager
import com.gyanoba.inspektor.TransactionRecorder
import com.gyanoba.inspektor.data.OverrideEngine
import com.gyanoba.inspektor.utils.approxByteCount
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.allHTTPHeaderFields
import platform.Foundation.appendData
import platform.Foundation.create
import platform.Foundation.subdataWithRange
import kotlin.concurrent.AtomicReference
import kotlin.time.Clock

internal const val NOTIFICATION_TITLE: String = "Recording URLSession Activity"

/**
 * Everything both URLSession entry points -- the proxy delegate and the opt-in `URLProtocol` --
 * need in order to drive a [TransactionRecorder].
 *
 * Nothing here suspends: delegate callbacks arrive on the session's own queue and a `URLProtocol`
 * runs on the loading thread, neither of which is a coroutine.
 */
internal class UrlSessionCapture(private val config: InspektorUrlSessionConfig) {
    private val core = config.core
    private val recorders = core.recorderFactory(NOTIFICATION_TITLE)
    private val overrideEngine = OverrideEngine(core.overrideRepository)
    private val retentionManager = RetentionManager(
        retentionDuration = core.retentionDuration,
        dataSource = core.dataSource,
    )

    val level: LogLevel get() = core.level
    val maxContentLength: Int get() = core.maxContentLength

    fun overrideEngine(): OverrideEngine = overrideEngine

    /**
     * Starts recording [request], or returns null when the level is NONE or a filter rejects it.
     */
    fun begin(request: NSURLRequest): Capture? {
        if (core.level == LogLevel.NONE) return null
        val view = request.toInspektorRequest()
        if (!core.shouldRecord(view)) return null

        retentionManager.checkAndCleanUpAsync()
        val recorder = recorders.newRecorder()
        val headers = request.headerMap()

        recorder.addRequestInfo(
            url = view.url,
            host = view.host,
            path = view.path,
            scheme = request.URL?.scheme,
            method = view.method,
            requestHeadersSize = headers.approxByteCount(),
            requestContentType = headers.contentType(),
            requestPayloadSize = request.HTTPBody?.length?.toLong(),
            requestDate = Clock.System.now(),
        )
        if (core.level.headers) recorder.addRequestHeaders(headers)
        if (core.level.body) {
            // An upload backed by `HTTPBodyStream` cannot be read without consuming it, which
            // would break the consumer's actual request, so those bodies stay uncaptured.
            request.HTTPBody?.decodeToText(core.maxContentLength)?.let(recorder::addRequestBody)
        }
        recorder.closeRequestLog()
        return Capture(recorder, view)
    }

    /** One in-flight call. */
    class Capture(val recorder: TransactionRecorder, val request: InspektorRequest) {
        private val body = NSMutableData()
        private var bodyBytes = 0L

        @OptIn(ExperimentalForeignApi::class)
        fun appendResponseData(data: NSData, max: Int) {
            val remaining = max - bodyBytes
            if (remaining <= 0) return
            if (data.length.toLong() <= remaining) {
                body.appendData(data)
                bodyBytes += data.length.toLong()
            } else {
                body.appendData(data.prefix(remaining))
                bodyBytes = max.toLong()
            }
        }

        fun responseText(): String? = body.takeIf { it.length.toULong() > 0u }?.toText()
    }

    fun onResponse(capture: Capture, response: NSURLResponse) {
        val http = response as? NSHTTPURLResponse
        val headers = http.headerMap()
        capture.recorder.addResponseInfo(
            // NSURLSession does not expose the negotiated HTTP version.
            protocol = null,
            responseCode = http?.statusCode?.toInt(),
            responseContentType = headers.contentType() ?: response.MIMEType,
            responsePayloadSize = response.expectedContentLength.takeIf { it >= 0 },
            responseHeadersSize = headers.approxByteCount(),
            responseDate = Clock.System.now(),
        )
        if (core.level.headers) capture.recorder.addResponseHeaders(headers)
    }

    fun onComplete(capture: Capture, error: NSError?) {
        if (error != null) {
            capture.recorder.addResponseException(
                Throwable(error.localizedDescription)
            )
        } else if (core.level.body) {
            capture.responseText()?.let(capture.recorder::addResponseBody)
        }
        capture.recorder.closeResponseLog()
    }
}

/**
 * A thread-safe task-to-capture map.
 *
 * Delegate callbacks for one session are serialized on its delegate queue, but a `URLProtocol` and
 * several sessions can share one recorder, so the map is updated with a compare-and-set loop
 * rather than assuming a single writer.
 */
internal class CaptureRegistry {
    private val entries = AtomicReference<Map<Any, UrlSessionCapture.Capture>>(emptyMap())

    fun put(key: Any, capture: UrlSessionCapture.Capture) {
        while (true) {
            val current = entries.value
            if (entries.compareAndSet(current, current + (key to capture))) return
        }
    }

    fun get(key: Any): UrlSessionCapture.Capture? = entries.value[key]

    fun remove(key: Any): UrlSessionCapture.Capture? {
        while (true) {
            val current = entries.value
            val found = current[key] ?: return null
            if (entries.compareAndSet(current, current - key)) return found
        }
    }
}

// ---- NSURLRequest / NSHTTPURLResponse adapters -----------------------------------------------

internal fun NSURLRequest.toInspektorRequest(): InspektorRequest = InspektorRequest(
    method = HTTPMethod ?: "GET",
    url = URL?.absoluteString.orEmpty(),
    host = URL?.host.orEmpty(),
    path = URL?.path.orEmpty(),
    headers = headerMap(),
)

internal fun NSURLRequest.headerMap(): Map<String, List<String>> =
    allHTTPHeaderFields.orEmpty().entries.mapNotNull { (key, value) ->
        val name = key as? String ?: return@mapNotNull null
        val single = value as? String ?: return@mapNotNull null
        name to listOf(single)
    }.toMap()

internal fun NSHTTPURLResponse?.headerMap(): Map<String, List<String>> =
    this?.allHeaderFields.orEmpty().entries.mapNotNull { (key, value) ->
        val name = key as? String ?: return@mapNotNull null
        val single = value as? String ?: return@mapNotNull null
        name to listOf(single)
    }.toMap()

private fun Map<String, List<String>>.contentType(): String? =
    entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
        ?.value?.firstOrNull()
        ?.substringBefore(';')
        ?.trim()

internal fun NSData.toText(): String? =
    NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()

internal fun NSData.decodeToText(max: Int): String? =
    (if (length.toLong() > max) prefix(max.toLong()) else this).toText()

/** The first [byteCount] bytes of this data. */
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.prefix(byteCount: Long): NSData =
    subdataWithRange(NSMakeRange(0u, byteCount.toULong()))
