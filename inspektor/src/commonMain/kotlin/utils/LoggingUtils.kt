package utils

import HttpClientCallLogger
import LogLevel
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.utils.buildHeaders
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


internal suspend fun HttpClientCallLogger.logRequestReturnContent(
    request: HttpRequestBuilder,
    level: LogLevel,
    headerSanitizers: List<HeaderSanitizer>,
): OutgoingContent? {
    val content = request.body as OutgoingContent

    val url = request.url
    val method = request.method.value
    val requestDate = Clock.System.now()
    val requestHeadersSize = request.headers.build().approxByteCount()
    val requestHeaders =
        Json.encodeToString(request.headers.build().sanitizeHeaders(headerSanitizers).entries())

    addRequestHeader(
        url.toString(),
        url.host,
        url.encodedPath,
        url.protocol.name,
        method,
        requestHeaders,
        requestHeadersSize,
        request.contentType()?.typeAndSubType,
        content.contentLength,
        requestDate
    )

    if (!level.body) {
        closeRequestLog()
        return null
    }

    val charset = content.contentType?.charset() ?: Charsets.UTF_8

    val channel = ByteChannel()
    var requestBody: String? = null
    GlobalScope.launch(Dispatchers.Unconfined) {
        requestBody = channel.tryReadText(charset)
    }.invokeOnCompletion {
        requestBody?.let {
            addRequestBody(it)
        }
        closeRequestLog()
    }

    return content.observe(channel)
}


internal fun Headers.sanitizeHeaders(
    headerSanitizers: List<HeaderSanitizer>,
): Headers = buildHeaders {
    forEach { name, values ->
        val sanitizedValues = values.map { value ->
            headerSanitizers.firstOrNull { it.predicate(name) }?.placeholder ?: value
        }
        appendAll(name, sanitizedValues)
    }
}


internal class HeaderSanitizer(
    val placeholder: String = "***",
    val predicate: (String) -> Boolean,
)

/// Copied from ktor logging sample
internal class LoggedContent(
    private val originalContent: OutgoingContent,
    private val channel: ByteReadChannel,
) : OutgoingContent.ReadChannelContent() {

    override val contentType: ContentType? = originalContent.contentType
    override val contentLength: Long? = originalContent.contentLength
    override val status: HttpStatusCode? = originalContent.status
    override val headers: Headers = originalContent.headers

    override fun <T : Any> getProperty(key: AttributeKey<T>): T? = originalContent.getProperty(key)

    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) =
        originalContent.setProperty(key, value)

    override fun readFrom(): ByteReadChannel = channel
}