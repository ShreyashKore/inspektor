package com.gyanoba.inspektor.okhttp

import com.gyanoba.inspektor.InspektorRequest
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.Buffer
import okio.ForwardingSource
import okio.Sink
import okio.Source
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
    val sink = TruncatingSink(max.toLong())
    try {
        val buffered = sink.buffer()
        writeTo(buffered)
        // `buffer()` hands back a sink that holds writes in its own buffer; without the flush
        // nothing reaches the truncating sink and every capture comes back empty.
        buffered.flush()
    } catch (_: CaptureComplete) {
        // [max] bytes are in; the rest of the body was never produced, which is the point.
    } catch (_: IOException) {
        return null
    } catch (_: IllegalStateException) {
        return null
    }
    val charset = contentType()?.charset() ?: Charsets.UTF_8
    return sink.collected.readString(charset)
}

/** Thrown by [TruncatingSink] to stop `writeTo` once it has everything the log will keep. */
private class CaptureComplete : RuntimeException() {
    // Thrown once per logged upload and always caught; a stack trace would be pure cost.
    override fun fillInStackTrace(): Throwable = this
}

/**
 * Collects the first [limit] bytes written to it, then stops the write.
 *
 * A large upload must not be produced twice -- once for the log, once for the network -- just to
 * log its first 250 KB, so the first write that goes past [limit] throws [CaptureComplete] and
 * [peekText] ends `writeTo` there. Only repeatable bodies get here, so abandoning this pass leaves
 * the one OkHttp makes for the network untouched. A body exactly [limit] bytes long finishes
 * normally.
 */
private class TruncatingSink(private val limit: Long) : Sink {
    val collected: Buffer = Buffer()

    override fun write(source: Buffer, byteCount: Long) {
        val toCopy = minOf(limit - collected.size, byteCount)
        collected.write(source, toCopy)
        if (toCopy < byteCount) throw CaptureComplete()
    }

    override fun flush() = Unit
    override fun timeout(): Timeout = Timeout.NONE
    override fun close() = Unit
}

/**
 * Copies the first [limit] bytes the consumer reads into a buffer, and reports them once.
 *
 * This is how a response body is captured without the interceptor reading it first. Even
 * `peekBody` blocks until [limit] bytes have arrived or the stream ends, so a server-sent-event
 * stream or a slow download would reach the app only after that.
 *
 * [onComplete] runs exactly once, on whichever comes first: the stream is exhausted, [limit] bytes
 * have been captured, a read fails, or the consumer closes the body. A body the app neither reads
 * nor closes is never reported -- but OkHttp requires every body to be closed anyway.
 */
internal class CapturingSource(
    delegate: Source,
    private val limit: Long,
    private val onComplete: (captured: Buffer, failure: IOException?) -> Unit,
) : ForwardingSource(delegate) {
    private val captured = Buffer()
    private var completed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = try {
            super.read(sink, byteCount)
        } catch (e: IOException) {
            complete(e)
            throw e
        }
        if (read == -1L) complete(null) else capture(sink, read)
        return read
    }

    override fun close() {
        try {
            super.close()
        } finally {
            complete(null)
        }
    }

    /** Copies the [read] bytes just appended to [sink], up to [limit] in total. */
    @Synchronized
    private fun capture(sink: Buffer, read: Long) {
        if (completed) return
        val toCopy = minOf(read, limit - captured.size)
        sink.copyTo(captured, offset = sink.size - read, byteCount = toCopy)
        if (captured.size >= limit) complete(null)
    }

    @Synchronized
    private fun complete(failure: IOException?) {
        if (completed) return
        completed = true
        onComplete(captured, failure)
    }
}
