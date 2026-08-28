package com.gyanoba.inspektor.utils

import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * The number of bytes needed to encode these headers as HTTP/1.1 -- also roughly the size of
 * HTTP/2 headers before HPACK compression.
 *
 * Lives in core so every integration reports the same number instead of each rolling its own.
 */
@UnstableInspektorAPI
public fun Map<String, List<String>>.approxByteCount(): Long {
    // Each header name has 2 bytes of overhead for ': ' and every header value has 2 bytes of
    // overhead for '\r\n'.
    var result = (size * 2 * 2).toLong()

    for ((name, values) in this) {
        result += name.length.toLong()
        for (i in values.indices) {
            result += values[i].length.toLong()
            // Add 1 byte for ','
            if (i != values.lastIndex) result += 1
        }
    }

    return result
}
