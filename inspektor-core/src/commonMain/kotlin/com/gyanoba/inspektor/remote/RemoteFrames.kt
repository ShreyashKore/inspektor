package com.gyanoba.inspektor.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The largest frame either side accepts. Bodies larger than this are read in ranges.
 */
internal const val MAX_FRAME_BYTES: Int = 16 * 1024 * 1024

/** A client broke the protocol. The connection is closed without a reply. */
internal class RemoteProtocolException(message: String) : Exception(message)

internal val RemoteJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Encodes [message] as one frame: a 4-byte big-endian length, then that many bytes of UTF-8 JSON.
 */
internal fun encodeFrame(message: JsonObject): ByteArray {
    val payload = RemoteJson.encodeToString(JsonObject.serializer(), message).encodeToByteArray()
    require(payload.size <= MAX_FRAME_BYTES) {
        "A message encoded to ${payload.size} bytes; the frame limit is $MAX_FRAME_BYTES."
    }
    val frame = ByteArray(4 + payload.size)
    frame[0] = (payload.size ushr 24).toByte()
    frame[1] = (payload.size ushr 16).toByte()
    frame[2] = (payload.size ushr 8).toByte()
    frame[3] = payload.size.toByte()
    payload.copyInto(frame, destinationOffset = 4)
    return frame
}

/**
 * Reads frames off a connection.
 *
 * A frame longer than [MAX_FRAME_BYTES], or one that is not UTF-8 JSON naming an object, throws
 * [RemoteProtocolException] and the session closes the connection without replying. That rule is
 * what keeps a web page from using the socket: the only bytes a browser can put on a loopback
 * socket are an HTTP request, and its first four (`GET `, `POST`) read as a length over a
 * gigabyte.
 */
internal class FrameReader(private val connection: RemoteConnection) {
    private val header = ByteArray(4)

    /** The next frame, or null when the client closed the connection between frames. */
    suspend fun next(): JsonObject? {
        if (!readFully(header, endOfStreamAllowed = true)) return null
        val length = (header[0].toLong() and 0xFF shl 24) or
            (header[1].toLong() and 0xFF shl 16) or
            (header[2].toLong() and 0xFF shl 8) or
            (header[3].toLong() and 0xFF)
        if (length > MAX_FRAME_BYTES) {
            throw RemoteProtocolException(
                "A frame announced $length bytes; the limit is $MAX_FRAME_BYTES.",
            )
        }
        val payload = ByteArray(length.toInt())
        readFully(payload, endOfStreamAllowed = false)

        val text = try {
            payload.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw RemoteProtocolException("A frame was not UTF-8.")
        }
        val element = try {
            RemoteJson.parseToJsonElement(text)
        } catch (_: SerializationException) {
            throw RemoteProtocolException("A frame was not JSON.")
        }
        return element as? JsonObject ?: throw RemoteProtocolException(
            "A frame was not a JSON object.",
        )
    }

    /**
     * Fills [buffer]. Returns false when the stream ended before its first byte and
     * [endOfStreamAllowed]; an end anywhere else is a truncated frame.
     */
    private suspend fun readFully(buffer: ByteArray, endOfStreamAllowed: Boolean): Boolean {
        var filled = 0
        while (filled < buffer.size) {
            val read = connection.read(buffer, filled, buffer.size - filled)
            if (read < 0) {
                if (filled == 0 && endOfStreamAllowed) return false
                throw RemoteProtocolException("The connection closed inside a frame.")
            }
            filled += read
        }
        return true
    }
}
