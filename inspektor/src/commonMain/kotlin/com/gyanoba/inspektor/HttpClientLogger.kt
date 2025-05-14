package com.gyanoba.inspektor

import androidx.annotation.VisibleForTesting
import com.gyanoba.inspektor.data.InspektorDataSource
import com.gyanoba.inspektor.data.MutableHttpTransaction
import com.gyanoba.inspektor.data.toImmutable
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.utils.logErr
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

private const val TAG = "Inspektor HttpClientCallLogger"

internal class HttpClientCallLogger(
    private val dataSource: InspektorDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    private val notificationManager: NotificationManager?,
) {
    private val transactionLog = MutableHttpTransaction()
    @VisibleForTesting
    val transaction get() = transactionLog.toImmutable()
    private val requestLoggedMonitor = Job()
    private val responseLoggedMonitor = Job()

    private val requestLogged = atomic(false)
    private val responseLogged = atomic(false)

    fun addOriginalRequest(
        headers: Set<Map.Entry<String, List<String>>>,
        body: String?,
    ) {
        transactionLog.apply {
            this.originalRequestHeaders = headers
            this.originalRequestBody = body
        }
    }

    fun addRequestInfo(
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
        notificationManager?.notify("Recording Ktor Activity", "$method $url")
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

    fun addRequestHeaders(headers: Set<Map.Entry<String, List<String>>>) {
        transactionLog.requestHeaders = headers
    }

    fun addRequestBody(body: String) {
        transactionLog.requestBody = body
    }

    fun addRequestException(exception: Throwable) {
        transactionLog.error = exception.toString()
    }

    fun addOriginalResponse(
        headers: Set<Map.Entry<String, List<String>>>,
        body: String?,
    ) {
        transactionLog.apply {
            this.originalResponseHeaders = headers
            this.originalResponseBody = body
        }
    }

    fun addResponseInfo(
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

    fun addResponseHeaders(headers: Set<Map.Entry<String, List<String>>>) {
        transactionLog.responseHeaders = headers
    }

    fun addResponseBody(body: String) {
        transactionLog.responseBody = body
    }

    fun addResponseException(exception: Throwable) {
        transactionLog.error = exception.toString()
    }


    @OptIn(DelicateCoroutinesApi::class)
    fun closeRequestLog() = GlobalScope.launch(ioDispatcher) {
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
    fun closeResponseLog() = GlobalScope.launch(ioDispatcher) {
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

    @VisibleForTesting
    suspend fun joinRequestLogged() = requestLoggedMonitor.join()

    @VisibleForTesting
    suspend fun joinResponseLogged() = responseLoggedMonitor.join()
}
