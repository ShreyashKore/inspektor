package data

import com.gyanoba.inspektor.data.HttpTransaction
import kotlinx.datetime.Instant

public data class MutableHttpTransaction(
    public var id: Long = 0L,
    public var method: String? = null,
    public var requestDate: Instant? = null,
    public var responseDate: Instant? = null,
    public var tookMs: Long? = null,
    public var protocol: String? = null,
    public var url: String? = null,
    public var host: String? = null,
    public var path: String? = null,
    public var scheme: String? = null,
    public var responseTlsVersion: String? = null,
    public var responseCipherSuite: String? = null,
    public var requestPayloadSize: Long? = null,
    public var requestContentType: String? = null,
    public var requestHeaders: Set<Map.Entry<String, List<String>>>? = null,
    public var requestHeadersSize: Long? = null,
    public var requestBody: String? = null,
    public var isRequestBodyEncoded: Boolean? = null,
    public var responseCode: Long? = null,
    public var responseMessage: String? = null,
    public var error: String? = null,
    public var responsePayloadSize: Long? = null,
    public var responseContentType: String? = null,
    public var responseHeaders: Set<Map.Entry<String, List<String>>>? = null,
    public var responseHeadersSize: Long? = null,
    public var responseBody: String? = null,
    public var isResponseBodyEncoded: Boolean? = null,
)

internal fun MutableHttpTransaction.toImmutable() = HttpTransaction(
    id = id,
    method = method,
    requestDate = requestDate,
    responseDate = responseDate,
    tookMs = tookMs,
    protocol = protocol,
    url = url,
    host = host,
    path = path,
    scheme = scheme,
    responseTlsVersion = responseTlsVersion,
    responseCipherSuite = responseCipherSuite,
    requestPayloadSize = requestPayloadSize,
    requestContentType = requestContentType,
    requestHeaders = requestHeaders,
    requestHeadersSize = requestHeadersSize,
    requestBody = requestBody,
    isRequestBodyEncoded = isRequestBodyEncoded,
    responseCode = responseCode,
    responseMessage = responseMessage,
    error = error,
    responsePayloadSize = responsePayloadSize,
    responseContentType = responseContentType,
    responseHeaders = responseHeaders,
    responseHeadersSize = responseHeadersSize,
    responseBody = responseBody,
    isResponseBodyEncoded = isResponseBodyEncoded,
)

//internal fun buildHttpTransaction(builder: MutableHttpTransaction.() -> Unit): HttpTransaction =
//    MutableHttpTransaction().apply(builder).toImmutable()
