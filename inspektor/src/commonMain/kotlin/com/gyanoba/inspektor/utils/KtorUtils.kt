package com.gyanoba.inspektor.utils

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining

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


internal suspend inline fun ByteReadChannel.tryReadText(
    charset: Charset,
    max: Int = Int.MAX_VALUE,
): String? = try {
    readRemaining().readText(charset = charset, max = max)
} catch (cause: Throwable) {
    null
}

internal val ContentType.typeAndSubType get() = "$contentType/$contentSubtype"
