package com.gyanoba.inspektor.har

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.HttpTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 *  Using Har as name space for dumping all Har related classes
 *  Not all fields are used from the data classes but kept for future.
 *  Some fields from specifications such as `comments` are omitted.
 *
 *
 *  [Specification](http://www.softwareishard.com/blog/har-12-spec/)
 * */
@UnstableInspektorAPI
@Serializable
public data class Har(
    public val log: Log,
) {
    public companion object {
        public const val HAR_VERSION: String = "1.2"
    }

    @Serializable
    public data class Log(
        public val version: String = HAR_VERSION,
        public val creator: Creator,
        public val entries: List<Entry>,
    )

    @Serializable
    public data class Creator(
        public val name: String,
        public val version: String = HAR_VERSION,
    )

    @Serializable
    public data class Entry(
        public val startedDateTime: String,
        public val time: Long,
        public val request: Request,
        public val response: Response,
        public val cache: Cache,
        public val timings: Timings
    )

    @Serializable
    public data class Request(
        public val method: String?,
        public val url: String?,
        public val httpVersion: String?,
        public val cookies: List<String> = emptyList(),
        public val headers: List<Header>,
        public val queryString: List<QueryParameter> = emptyList(),
        public val postData: PostData? = null,
        public val headersSize: Long?,
        public val bodySize: Long?,
    )

    @Serializable
    public data class Response(
        public val status: Long?,
        public val statusText: String?,
        public val httpVersion: String?,
        public val cookies: List<String> = emptyList(),
        public val headers: List<Header>,
        public val content: Content,
        public val redirectURL: String? = null,
        public val headersSize: Long?,
        public val bodySize: Long?,
    )

    @Serializable
    public data class Content(
        public val size: Long?,
        public val mimeType: String?,
        public val text: String? = null,
        public val encoding: String? = null,
    )

    @Serializable
    public data class Header(
        public val name: String,
        public val value: String,
    )

    @Serializable
    public data class QueryParameter(
        public val name: String,
        public val value: String,
    )

    @Serializable
    public data class PostData(
        public val mimeType: String?,
        public val text: String?,
        public val params: List<Param> = emptyList(),
    ) {
        @Serializable
        public data class Param(
            val name: String, val value: String
        )
    }

    @Serializable
    public data class Cache(
        public val afterRequest: SecondaryRequest? = null,
        public val beforeRequest: SecondaryRequest? = null,
    ) {
        @Serializable
        public data class SecondaryRequest(
            val expires: String? = null,
            val lastAccess: String,
            val eTag: String,
            val hitCount: Int,
        )
    }

    @Serializable
    public data class Timings(
        public val blocked: Long? = null,
        public val dns: Long? = null,
        public val ssl: Long? = null,
        public val connect: Long? = null,
        public val send: Long = 0,
        public val wait: Long,
        public val receive: Long = 0,
    )
}


@UnstableInspektorAPI
public fun HttpTransaction.toHarEntry(): Har.Entry? {
    val requestDate = this.requestDate ?: return null

    return Har.Entry(
        startedDateTime = requestDate.toString(), time = this.tookMs ?: -1,
        request = Har.Request(
            method = this.method,
            url = this.url,
            httpVersion = this.protocol,
            headers = this.requestHeaders?.flatMap { entry ->
                entry.value.map {
                    Har.Header(name = entry.key, value = it)
                }
            }.orEmpty(),
            queryString = this.url?.let(::parseQueryParameters).orEmpty(),
            bodySize = this.requestPayloadSize,
            headersSize = this.requestHeadersSize
        ),
        response = Har.Response(
            status = this.responseCode,
            statusText = this.responseMessage,
            httpVersion = this.protocol,
            headers = this.responseHeaders?.flatMap { entry ->
                entry.value.map { Har.Header(name = entry.key, value = it) }
            }.orEmpty(),
            bodySize = this.responsePayloadSize,
            headersSize = this.responseHeadersSize,
            content = Har.Content(
                size = this.responsePayloadSize,
                mimeType = this.responseContentType,
                text = this.responseBody,
            )
        ),
        timings = Har.Timings(
            wait = this.tookMs ?: 0,
        ),
        cache = Har.Cache(),
    )
}

/**
 * Converts a list of [HttpTransaction] to a HAR log string.
 *
 * @param creatorName The name of the creator of the HAR log.
 * @return A string representation of the HAR log in JSON format.
 */
@UnstableInspektorAPI
public fun List<HttpTransaction>.toHarLogString(creatorName: String): String {
    val log = Har.Log(
        creator = Har.Creator(name = creatorName),
        entries = this.mapNotNull { it.toHarEntry() },
    )
    return json.encodeToString(Har(log))
}

@UnstableInspektorAPI
public val json: Json = Json { encodeDefaults = true }

/**
 * Splits the query string off [url] into HAR query parameters.
 *
 * Hand-rolled rather than reusing `io.ktor.http.Url`, so that HAR export -- and therefore the whole
 * core module -- carries no Ktor dependency.
 */
private fun parseQueryParameters(url: String): List<Har.QueryParameter> {
    val query = url.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return emptyList()
    return query.split('&').mapNotNull { pair ->
        if (pair.isEmpty()) return@mapNotNull null
        val name = pair.substringBefore('=')
        val value = pair.substringAfter('=', missingDelimiterValue = "")
        Har.QueryParameter(percentDecode(name), percentDecode(value))
    }
}

private fun percentDecode(value: String): String {
    if ('%' !in value && '+' !in value) return value
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' && i + 2 < value.length -> {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex == null) {
                    bytes += c.code.toByte()
                    i++
                } else {
                    bytes += hex.toByte()
                    i += 3
                }
            }

            c == '+' -> {
                bytes += ' '.code.toByte()
                i++
            }

            else -> {
                // Non-ASCII characters cannot appear un-encoded in a valid query string, but be
                // lenient rather than dropping them.
                c.toString().encodeToByteArray().forEach { bytes += it }
                i++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}