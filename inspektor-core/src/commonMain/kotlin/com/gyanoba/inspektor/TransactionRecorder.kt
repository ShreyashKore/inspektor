package com.gyanoba.inspektor

import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.MutableHttpTransaction
import com.gyanoba.inspektor.data.toImmutable
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.utils.HeaderSanitizer
import com.gyanoba.inspektor.utils.logErr
import com.gyanoba.inspektor.utils.sanitize
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Instant

private const val TAG = "Inspektor TransactionRecorder"

/**
 * Hands out a [TransactionRecorder] per call, pre-wired with the store, the notification manager
 * and the header sanitizers from an [InspektorCoreConfig].
 *
 * Integrations take one of these instead of reaching for a singleton, which is what makes them
 * testable and what lets two integrations coexist in one process with different settings.
 */
@UnstableInspektorAPI
public class RecorderFactory(
    private val dataSource: InspektorDataSource,
    private val notificationManager: NotificationManager?,
    private val notificationTitle: String,
    private val headerSanitizers: List<HeaderSanitizer> = emptyList(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    public fun newRecorder(): TransactionRecorder = TransactionRecorder(
        dataSource = dataSource,
        ioDispatcher = ioDispatcher,
        notificationManager = notificationManager,
        notificationTitle = notificationTitle,
        headerSanitizers = headerSanitizers,
    )
}

/**
 * Accumulates one HTTP call and writes it to the store.
 *
 * Every method is **non-suspending and fire-and-forget** on purpose. Recorders are driven from
 * blocking OkHttp interceptor threads and from Objective-C callback queues as well as from
 * coroutines, so nothing here may require a suspending caller or a particular thread.
 *
 * [closeRequestLog] inserts the row and [closeResponseLog] updates it. The two may be called in
 * either order and from different threads: the atomic flags make each run at most once, and
 * [closeResponseLog] joins the insert's [Job] so the UPDATE can never overtake the INSERT.
 */
@UnstableInspektorAPI
public class TransactionRecorder internal constructor(
    private val dataSource: InspektorDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    private val notificationManager: NotificationManager?,
    private val notificationTitle: String,
    private val headerSanitizers: List<HeaderSanitizer>,
) {
    private val transactionLog = MutableHttpTransaction()

    public val transaction: com.gyanoba.inspektor.data.HttpTransaction
        get() = transactionLog.toImmutable()

    private val requestLoggedMonitor = Job()
    private val responseLoggedMonitor = Job()

    private val requestLogged = atomic(false)
    private val responseLogged = atomic(false)

    public fun addOriginalRequest(
        headers: Map<String, List<String>>,
        body: String?,
    ) {
        transactionLog.apply {
            this.originalRequestHeaders = headers.sanitize(headerSanitizers).entries
            this.originalRequestBody = body
        }
    }

    public fun addRequestInfo(
        url: String,
        host: String?,
        path: String?,
        scheme: String?,
        method: String?,
        requestHeadersSize: Long?,
        requestContentType: String?,
        requestPayloadSize: Long?,
        requestDate: Instant,
    ) {
        notificationManager?.notify(notificationTitle, "$method $url")
        transactionLog.apply {
            this.url = url
            this.host = host
            this.path = path
            this.scheme = scheme
            this.method = method
            this.requestContentType = requestContentType
            this.requestHeadersSize = requestHeadersSize
            this.requestPayloadSize = requestPayloadSize
            this.requestDate = requestDate
        }
    }

    public fun addRequestHeaders(headers: Map<String, List<String>>) {
        transactionLog.requestHeaders = headers.sanitize(headerSanitizers).entries
    }

    public fun addRequestBody(body: String) {
        transactionLog.requestBody = body
    }

    public fun addRequestException(exception: Throwable) {
        transactionLog.error = exception.toString()
    }

    public fun addOriginalResponse(
        headers: Map<String, List<String>>,
        body: String?,
    ) {
        transactionLog.apply {
            this.originalResponseHeaders = headers.sanitize(headerSanitizers).entries
            this.originalResponseBody = body
        }
    }

    public fun addResponseInfo(
        protocol: String?,
        responseCode: Int?,
        responseContentType: String?,
        responsePayloadSize: Long?,
        responseHeadersSize: Long?,
        responseDate: Instant,
    ) {
        transactionLog.apply {
            this.protocol = protocol
            this.responseCode = responseCode?.toLong()
            this.responseDate = responseDate
            this.responseContentType = responseContentType
            this.responsePayloadSize = responsePayloadSize
            this.responseHeadersSize = responseHeadersSize
            this.tookMs = responseDate.toEpochMilliseconds() - requestDate!!.toEpochMilliseconds()
        }
    }

    public fun addResponseHeaders(headers: Map<String, List<String>>) {
        transactionLog.responseHeaders = headers.sanitize(headerSanitizers).entries
    }

    public fun addResponseBody(body: String) {
        transactionLog.responseBody = body
    }

    public fun addResponseException(exception: Throwable) {
        transactionLog.error = exception.toString()
    }

    /**
     * TLS details for the connection the call went out on. Only integrations that sit close enough
     * to the socket can fill these -- OkHttp can, Ktor cannot.
     */
    public fun addTlsInfo(tlsVersion: String?, cipherSuite: String?) {
        transactionLog.apply {
            this.responseTlsVersion = tlsVersion
            this.responseCipherSuite = cipherSuite
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public fun closeRequestLog(): Job = GlobalScope.launch(ioDispatcher) {
        if (!requestLogged.compareAndSet(false, true)) return@launch
        try {
            transactionLog.id = dataSource.insertHttpTransaction(transactionLog.toImmutable())
        } catch (e: Throwable) {
            logErr(e, TAG) { "Failed to log request: $e" }
        } finally {
            requestLoggedMonitor.complete()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    public fun closeResponseLog(): Job = GlobalScope.launch(ioDispatcher) {
        if (!responseLogged.compareAndSet(false, true)) return@launch
        requestLoggedMonitor.join()
        try {
            dataSource.updateHttpTransaction(transactionLog.toImmutable())
        } catch (e: Throwable) {
            logErr(e, TAG) { "Failed to log response" }
        } finally {
            responseLoggedMonitor.complete()
        }
    }

    /** Suspends until the row exists. Intended for tests, which otherwise race the insert. */
    public suspend fun joinRequestLogged(): Unit = requestLoggedMonitor.join()

    /** Suspends until the row has been updated with the response. Intended for tests. */
    public suspend fun joinResponseLogged(): Unit = responseLoggedMonitor.join()
}
