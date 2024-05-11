import co.touchlab.kermit.Logger
import com.gyanoba.inspektor.data.entites.HttpTransaction
import data.db.createDatabase
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.Sender
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.util.date.GMTDate
import io.ktor.util.toMap
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal val logger = Logger

internal class Inspektor private constructor() {
    private val db = createDatabase()

    fun insertHttpTransaction(httpTransaction: HttpTransaction) {
        logger.d("$httpTransaction")
        db.httpTransactionQueries.insert(httpTransaction)
    }

    fun getAllLatestHttpTransactions() = db.httpTransactionQueries.getAllLatest().executeAsList()

    companion object {
        val Instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { Inspektor() }
    }
}


public val inspektor: suspend Sender.(HttpRequestBuilder) -> HttpClientCall = { requestBuilder ->
    val startTime = Clock.System.now()
    val call = execute(requestBuilder)
    val endTime = Clock.System.now()

    val duration = endTime - startTime

    val requestBody = try {
        call.request.content.toString()
    } catch (e: Exception) {
        logger.e(e) { "Failed to read request body" }
        null
    }

    val responseBody = try {
        call.response.bodyAsText()
    } catch (e: Exception) {
        logger.e(e) { "Failed to read response body" }
        null
    }

    val inspektor = Inspektor.Instance

    inspektor.insertHttpTransaction(
        HttpTransaction(
            id = -1,
            method = call.request.method.value,
            statusCode = call.response.status.value.toLong(),
            requestDate = startTime,
            responseDate = call.response.responseTime.toInstant(),
            tookMs = duration.inWholeMilliseconds,
            protocol = call.request.url.protocol.name,
            scheme = call.request.url.protocol.name,
            url = call.request.url.toString(),
            host = call.request.url.host,
            path = call.request.url.encodedPath,
            requestPayloadSize = call.request.contentLength(),
            requestContentType = call.request.contentType()?.contentType,
            requestHeaders = jsonX.encodeToString(call.request.headers.toMap()),
            requestHeadersSize = call.request.headers.approxByteCount(),
            requestBody = requestBody,
            isRequestBodyEncoded = requestBody == null,
            responseCode = call.response.status.value.toLong(),
            responseMessage = responseBody,
            error = null,
            responsePayloadSize = call.response.contentLength(),
            responseContentType = call.response.contentType()?.contentType,
            responseHeaders = jsonX.encodeToString(call.response.headers.toMap()),
            responseHeadersSize = call.response.headers.toMap().size.toLong(),
            responseBody = responseBody,
            isResponseBodyEncoded = responseBody == null,
            responseTlsVersion = null,
            responseCipherSuite = null,
        )
    )
    call
}

internal val jsonX = Json {
    ignoreUnknownKeys = true
}

internal fun GMTDate.toInstant() = Instant.fromEpochMilliseconds(timestamp)


/**
 * Returns the number of bytes required to encode these headers using HTTP/1.1. This is also the
 * approximate size of HTTP/2 headers before they are compressed with HACK. This value is
 * intended to be used as a metric: smaller headers are more efficient to encode and transmit.
 */
internal fun Headers.approxByteCount(): Long {
    // Each header name has 2 bytes of overhead for ': ' and every header value has 2 bytes of
    // overhead for '\r\n'.
    val entries = entries().toList()
    var result = (entries.size * 2 * 2).toLong()

    for ((name, values) in entries) {
        result += name.length.toLong()
        for (i in values.indices) {
            result += values[i].length.toLong()
            // Add 1 byte for ','
            if (i != values.lastIndex) result += 1
        }
    }

    return result
}