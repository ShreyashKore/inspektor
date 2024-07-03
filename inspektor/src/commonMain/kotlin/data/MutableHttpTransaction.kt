package data

import com.gyanoba.inspektor.data.entites.HttpTransaction
import kotlinx.datetime.Instant

public data class MutableHttpTransaction(
    public var id: Long = 0L,
    public var method: String? = null,
    public var statusCode: Long? = null,
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
    public var requestHeaders: String? = null,
    public var requestHeadersSize: Long? = null,
    public var requestBody: String? = null,
    public var isRequestBodyEncoded: Boolean? = null,
    public var responseCode: Long? = null,
    public var responseMessage: String? = null,
    public var error: String? = null,
    public var responsePayloadSize: Long? = null,
    public var responseContentType: String? = null,
    public var responseHeaders: String? = null,
    public var responseHeadersSize: Long? = null,
    public var responseBody: String? = null,
    public var isResponseBodyEncoded: Boolean? = null,
)

internal fun MutableHttpTransaction.toImmutable() = HttpTransaction(
    id,
    method!!,
    statusCode,
    requestDate!!,
    responseDate,
    tookMs,
    protocol,
    url,
    host,
    path,
    scheme,
    responseTlsVersion,
    responseCipherSuite,
    requestPayloadSize,
    requestContentType,
    requestHeaders,
    requestHeadersSize,
    requestBody,
    isRequestBodyEncoded,
    responseCode,
    responseMessage,
    error,
    responsePayloadSize,
    responseContentType,
    responseHeaders,
    responseHeadersSize,
    responseBody,
    isResponseBodyEncoded,
)

//internal fun buildHttpTransaction(builder: MutableHttpTransaction.() -> Unit): HttpTransaction =
//    MutableHttpTransaction().apply(builder).toImmutable()
