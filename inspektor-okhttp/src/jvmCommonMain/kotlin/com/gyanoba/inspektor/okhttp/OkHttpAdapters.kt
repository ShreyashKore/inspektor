package com.gyanoba.inspektor.okhttp

import com.gyanoba.inspektor.InspektorRequest
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.buffer
import java.io.IOException

/** OkHttp headers reduced to the shape core stores, sanitizes and sizes. */
internal fun Headers.toHeaderMap(): Map<String, List<String>> =
    names().associateWith { values(it) }

/** The neutral view of a request that filters and override matchers work against. */
internal fun Request.toInspektorRequest(): InspektorRequest = InspektorRequest(
    method = method,
    url = url.toString(),
    host = url.host,
    path = url.encodedPath,
    headers = headers.toHeaderMap(),
)

internal val MediaType.typeAndSubType: String get() = "$type/$subtype"

/**
 * Reads at most [max] bytes of this request body without consuming it.
 *
 * `writeTo` is safe to call twice for ordinary bodies, which is what makes non-destructive capture
 * possible at all. It is *not* safe for duplex or one-shot bodies -- reading those would corrupt
 * the consumer's actual request -- so those are skipped rather than captured.
 */
internal fun RequestBody.peekText(max: Int): String? {
    if (isDuplex() || isOneShot()) return null
    return try {
        val sink = TruncatingSink(max.toLong())
        val buffered = sink.buffer()
        writeTo(buffered)
        // `buffer()` hands back a sink that holds writes in its own buffer; without the flush
        // nothing reaches the truncating sink and every capture comes back empty.
        buffered.flush()
        val charset = contentType()?.charset() ?: Charsets.UTF_8
        sink.collected.readString(charset)
    } catch (_: IOException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
}

/**
 * Collects the first [limit] bytes written to it and discards the rest.
 *
 * A large upload must not be held in memory in full just to log its first 250 KB. `writeTo` has to
 * be allowed to run to completion regardless, so the overflow is skipped rather than refused.
 */
private class TruncatingSink(private val limit: Long) : Sink {
    val collected: Buffer = Buffer()
    private var written = 0L

    override fun write(source: Buffer, byteCount: Long) {
        val remaining = limit - written
        if (remaining <= 0) {
            source.skip(byteCount)
            return
        }
        val toCopy = minOf(remaining, byteCount)
        collected.write(source, toCopy)
        written += toCopy
        if (toCopy < byteCount) source.skip(byteCount - toCopy)
    }

    override fun flush() = Unit
    override fun timeout(): Timeout = Timeout.NONE
    override fun close() = Unit
}
