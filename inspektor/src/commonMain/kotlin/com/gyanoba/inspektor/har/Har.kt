package com.gyanoba.inspektor.har

import com.gyanoba.inspektor.data.HttpTransaction
import io.ktor.http.Url
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
@Serializable
internal data class Har(
    val log: Log,
) {
    companion object {
        const val HAR_VERSION = "1.2"
    }

    @Serializable
    internal data class Log(
        val version: String = HAR_VERSION,
        val creator: Creator,
        val entries: List<Entry>,
    )

    @Serializable
    internal data class Creator(
        val name: String,
        val version: String = HAR_VERSION,
    )

    @Serializable
    internal data class Entry(
        val startedDateTime: String,
        val time: Long,
        val request: Request,
        val response: Response,
        val cache: Cache,
        val timings: Timings
    )

    @Serializable
    internal data class Request(
        val method: String?,
        val url: String?,
        val httpVersion: String?,
        val cookies: List<String> = emptyList(),
        val headers: List<Header>,
        val queryString: List<QueryParameter> = emptyList(),
        val postData: PostData? = null,
        val headersSize: Long?,
        val bodySize: Long?,
    )

    @Serializable
    internal data class Response(
        val status: Long?,
        val statusText: String?,
        val httpVersion: String?,
        val cookies: List<String> = emptyList(),
        val headers: List<Header>,
        val content: Content,
        val redirectURL: String? = null,
        val headersSize: Long?,
        val bodySize: Long?,
    )

    @Serializable
    internal data class Content(
        val size: Long?,
        val mimeType: String?,
        val text: String? = null,
        val encoding: String? = null,
    )

    @Serializable
    internal data class Header(
        val name: String,
        val value: String,
    )

    @Serializable
    internal data class QueryParameter(
        val name: String,
        val value: String,
    )

    @Serializable
    internal data class PostData(
        val mimeType: String?,
        val text: String?,
        val params: List<Param> = emptyList(),
    ) {
        @Serializable
        internal data class Param(
            val name: String, val value: String
        )
    }

    @Serializable
    internal data class Cache(
        val afterRequest: SecondaryRequest? = null,
        val beforeRequest: SecondaryRequest? = null,
    ) {
        @Serializable
        internal data class SecondaryRequest(
            val expires: String? = null,
            val lastAccess: String,
            val eTag: String,
            val hitCount: Int,
        )
    }

    @Serializable
    internal data class Timings(
        val blocked: Long? = null,
        val dns: Long? = null,
        val ssl: Long? = null,
        val connect: Long? = null,
        val send: Long = 0,
        val wait: Long,
        val receive: Long = 0,
    )
}


internal fun HttpTransaction.toHarEntry(): Har.Entry? {
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
            queryString = this.url?.let {
                Url(it).parameters.entries().flatMap { entry ->
                    entry.value.map { value ->
                        Har.QueryParameter(entry.key, value)
                    }
                }
            }.orEmpty(),
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
 * @param creatorVersion The version of the creator of the HAR log.
 * @return A string representation of the HAR log in JSON format.
 */
internal fun List<HttpTransaction>.toHarLogString(creatorName: String): String {
    val log = Har.Log(
        creator = Har.Creator(name = creatorName),
        entries = this.mapNotNull { it.toHarEntry() },
    )
    return json.encodeToString(Har(log))
}

internal val json = Json { encodeDefaults = true }