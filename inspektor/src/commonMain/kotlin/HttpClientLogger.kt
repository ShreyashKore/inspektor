import data.InspektorDataSource
import data.MutableHttpTransaction
import data.toImmutable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import utils.logErr

private const val TAG = "Inspektor HttpClientCallLogger"

internal class HttpClientCallLogger(
    private val dataSource: InspektorDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    private val notificationManager: NotificationManager,
) {
    private val transactionLog = MutableHttpTransaction()
    private val requestLoggedMonitor = Job()

    private val requestLogged = atomic(false)
    private val responseLogged = atomic(false)

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
        notificationManager.notify("Request", "Request to $url")
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

    fun closeResponseLog() = GlobalScope.launch(ioDispatcher) {
        if (!responseLogged.compareAndSet(false, true)) return@launch
        requestLoggedMonitor.join()
        try {
            dataSource.updateHttpTransaction(transactionLog.toImmutable())
        } catch (e: Throwable) {
            logErr(e, TAG) { "Failed to log response" }
        }
    }
}
