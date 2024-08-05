import data.InspektorDataSource
import data.MutableHttpTransaction
import data.toImmutable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import utils.log

internal class HttpClientCallLogger(private val dataSource: InspektorDataSource) {
    private val transactionLog = MutableHttpTransaction()
    private val requestLoggedMonitor = Job()
    private val responseHeaderMonitor = Job()

    private val requestLogged = atomic(false)
    private val responseLogged = atomic(false)

    fun addRequestHeader(
        url: String,
        host: String?,
        path: String?,
        scheme: String?,
        method: String?,
        requestHeaders: String?,
        requestContentType: String?,
        requestDate: Instant,
    ) {
        transactionLog.apply {
            this.url = url
            this.host = host
            this.path = path
            this.scheme = scheme
            this.method = method
            this.requestContentType = requestContentType
            this.responseHeaders = requestHeaders
            this.requestDate = requestDate
        }
    }

    fun addRequestBody(body: String) {
        transactionLog.requestBody = body
    }

    fun addRequestException(exception: Throwable) {
        transactionLog.error = exception.toString()
    }

    fun addResponseHeader(
        protocol: String?,
        responseCode: Int?,
        responseHeaders: String?,
        responseContentType: String?,
        responsePayloadSize: Long?,
        responseHeadersSize: Long?,
        responseDate: Instant,
    ) {
        transactionLog.apply {
            this.protocol = protocol
            this.responseCode = responseCode?.toLong()
            this.responseHeaders = responseHeaders
            this.responseDate = responseDate
            this.responseContentType = responseContentType
            this.responsePayloadSize = responsePayloadSize
            this.responseHeadersSize = responseHeadersSize
            this.tookMs = responseDate.toEpochMilliseconds() - requestDate!!.toEpochMilliseconds()
        }
        responseHeaderMonitor.complete()
    }

    suspend fun addResponseBody(body: String) {
        responseHeaderMonitor.join()
        transactionLog.responseBody = body
    }

    suspend fun addResponseException(exception: Throwable) {
        requestLoggedMonitor.join()
        transactionLog.error = exception.toString()
    }


    fun closeRequestLog() = GlobalScope.launch(Dispatchers.Unconfined) {
        if (!requestLogged.compareAndSet(false, true)) return@launch
        try {
            transactionLog.id = dataSource.insertHttpTransaction(transactionLog.toImmutable())
            log { "Inserted transaction with id ${transactionLog.id}" }
        } finally {
            log { "Closing request log for transaction ${transactionLog.id}" }
            requestLoggedMonitor.complete()
        }
    }

    suspend fun closeResponseLog() {
        if (!responseLogged.compareAndSet(false, true)) return
        requestLoggedMonitor.join()
        dataSource.insertHttpTransaction(transactionLog.toImmutable())
        log { "Updated transaction with id ${transactionLog.id}" }
        log { "${transactionLog.id} BODY $transactionLog" }
    }
}
