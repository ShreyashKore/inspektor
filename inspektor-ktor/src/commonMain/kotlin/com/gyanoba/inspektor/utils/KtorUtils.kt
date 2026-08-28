package com.gyanoba.inspektor.utils

import com.gyanoba.inspektor.InspektorRequest
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.encodedPath
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining

/** Delegates to core, so Ktor and every other integration report the same header size. */
internal fun Headers.approxByteCount(): Long = toHeaderMap().approxByteCount()


internal suspend inline fun ByteReadChannel.tryReadText(
    charset: Charset,
    max: Int = Int.MAX_VALUE,
): String? = try {
    readRemaining().readText(charset = charset, max = max)
} catch (cause: Throwable) {
    null
}

internal val ContentType.typeAndSubType get() = "$contentType/$contentSubtype"

/**
 * Ktor `Headers` reduced to the shape core stores and sanitizes.
 *
 * This is the whole of the Ktor-to-core header adapter: sanitization itself lives in
 * [com.gyanoba.inspektor.TransactionRecorder], so it cannot be forgotten here or in any other
 * integration.
 */
internal fun Headers.toHeaderMap(): Map<String, List<String>> =
    entries().associate { it.key to it.value }

/** The neutral view of an outgoing request that filters and override matchers work against. */
internal fun HttpRequestBuilder.toInspektorRequest(): InspektorRequest = InspektorRequest(
    method = method.value,
    url = url.toString(),
    host = url.host,
    path = url.encodedPath,
    headers = headers.build().toHeaderMap(),
)

/** The neutral view of an already-sent request. */
internal fun HttpRequest.toInspektorRequest(): InspektorRequest = InspektorRequest(
    method = method.value,
    url = url.toString(),
    host = url.host,
    path = url.encodedPath,
    headers = headers.toHeaderMap(),
)
