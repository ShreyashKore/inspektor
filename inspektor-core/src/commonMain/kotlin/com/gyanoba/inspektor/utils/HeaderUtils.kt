package com.gyanoba.inspektor.utils

import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * The number of bytes needed to encode [headers] as HTTP/1.1 -- also roughly the size of HTTP/2
 * headers before HPACK compression.
 *
 * Lives in core so every integration reports the same number instead of each rolling its own.
 *
 * Deliberately not an extension on `Map<String, List<String>>`: that would put it in completion on
 * every such map a consumer owns, for a function only Inspektor's integrations call.
 */
@UnstableInspektorAPI
public fun approxHeaderByteCount(headers: Map<String, List<String>>): Long {
    // Each header name has 2 bytes of overhead for ': ' and every header value has 2 bytes of
    // overhead for '\r\n'.
    var result = (headers.size * 2 * 2).toLong()

    for ((name, values) in headers) {
        result += name.length.toLong()
        for (i in values.indices) {
            result += values[i].length.toLong()
            // Add 1 byte for ','
            if (i != values.lastIndex) result += 1
        }
    }

    return result
}
