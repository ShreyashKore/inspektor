package remote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.InspektorDatabase
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.createInspektorDatabase
import com.gyanoba.inspektor.remote.RemoteConnection
import com.gyanoba.inspektor.remote.RemoteJson
import com.gyanoba.inspektor.remote.encodeFrame
import io.github.xxfast.kstore.Codec
import io.github.xxfast.kstore.storeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A fresh in-memory store. */
@OptIn(UnstableInspektorAPI::class)
internal fun testDataSource(): InspektorDataSourceImpl = runBlocking {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    InspektorDatabase.Schema.create(driver).await()
    InspektorDataSourceImpl(createInspektorDatabase(driver))
}

@OptIn(UnstableInspektorAPI::class)
private class MemoryCodec : Codec<List<Override>> {
    private var stored: List<Override>? = null
    override suspend fun decode(): List<Override>? = stored
    override suspend fun encode(value: List<Override>?) {
        stored = value
    }
}

@OptIn(UnstableInspektorAPI::class)
internal fun testOverrides(): OverrideRepositoryImpl =
    OverrideRepositoryImpl(storeOf(codec = MemoryCodec()))

/** A recorded exchange as the integrations write one. */
internal fun transaction(
    url: String = "https://api.example.com/v1/items",
    responseCode: Long? = 200,
    requestBody: String? = """{"name":"pen"}""",
    responseBody: String? = """{"id":7}""",
    responsePayloadSize: Long? = responseBody?.encodeToByteArray()?.size?.toLong(),
): HttpTransaction = HttpTransaction(
    id = 0,
    method = "POST",
    requestDate = Instant.parse("2026-10-18T09:12:03.412Z"),
    responseDate = responseCode?.let { Instant.parse("2026-10-18T09:12:03.643Z") },
    tookMs = responseCode?.let { 231 },
    protocol = "h2",
    url = url,
    host = "api.example.com",
    path = "/v1/items",
    scheme = "https",
    responseTlsVersion = responseCode?.let { "TLSv1.3" },
    responseCipherSuite = null,
    requestPayloadSize = requestBody?.encodeToByteArray()?.size?.toLong(),
    requestContentType = "application/json",
    requestHeaders = mapOf(
        "content-type" to listOf("application/json"),
        "accept" to listOf("application/json", "text/plain"),
    ).entries,
    requestHeadersSize = null,
    requestBody = requestBody,
    isRequestBodyEncoded = false,
    responseCode = responseCode,
    responseMessage = responseCode?.let { "OK" },
    error = null,
    responsePayloadSize = responsePayloadSize,
    responseContentType = responseCode?.let { "application/json" },
    responseHeaders = responseCode?.let { mapOf("set-cookie" to listOf("a=1", "b=2")).entries },
    responseHeadersSize = null,
    responseBody = responseBody,
    isResponseBodyEncoded = false,
    originalRequestHeaders = null,
    originalRequestBody = null,
    originalResponseCode = null,
    originalResponseHeaders = null,
    originalResponseBody = null,
)

/**
 * Two in-memory [RemoteConnection]s wired to each other: what one writes, the other reads.
 * Closing either ends both directions, like a socket.
 */
internal fun connectionPair(): Pair<PipeConnection, PipeConnection> {
    val aToB = Channel<ByteArray>(Channel.UNLIMITED)
    val bToA = Channel<ByteArray>(Channel.UNLIMITED)
    return PipeConnection(incoming = bToA, outgoing = aToB) to
        PipeConnection(incoming = aToB, outgoing = bToA)
}

internal class PipeConnection(
    private val incoming: Channel<ByteArray>,
    private val outgoing: Channel<ByteArray>,
) : RemoteConnection {
    private var chunk: ByteArray? = null
    private var position = 0

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var current = chunk
        if (current == null || position == current.size) {
            current = incoming.receiveCatching().getOrNull() ?: return -1
            chunk = current
            position = 0
        }
        val count = minOf(length, current.size - position)
        current.copyInto(buffer, offset, position, position + count)
        position += count
        return count
    }

    override suspend fun write(bytes: ByteArray) {
        outgoing.send(bytes.copyOf())
    }

    override fun close() {
        outgoing.close()
        incoming.close()
    }

    /** Raw bytes, for frames the encoder would refuse to produce. */
    suspend fun writeRaw(bytes: ByteArray): Unit = write(bytes)
}

/**
 * The Eagly side of a session: sends requests, and sorts what comes back into replies and events.
 */
internal class TestClient(private val connection: PipeConnection) {
    private val reader = com.gyanoba.inspektor.remote.FrameReader(connection)
    private val events = ArrayDeque<JsonObject>()
    private val replies = mutableMapOf<Long, JsonObject>()
    private var nextId = 1L

    /** The next frame of any kind. Null when the server closed the connection. */
    suspend fun nextFrame(): JsonObject? = withRealTimeout { reader.next() }

    suspend fun hello(): JsonObject = nextFrame()!!.also { check(it["type"].text == "hello") }

    suspend fun request(method: String, params: JsonElement? = null): JsonObject {
        val id = nextId++
        connection.write(
            encodeFrame(
                buildJsonObject {
                    put("id", id)
                    put("method", method)
                    params?.let { put("params", it) }
                },
            ),
        )
        while (true) {
            replies.remove(id)?.let { return it }
            val frame = nextFrame() ?: error("The server closed the connection.")
            sortFrame(frame)
        }
    }

    /** The next event, skipping `overrides.changed` unless [name] asks for it. */
    suspend fun nextEvent(name: String? = null): JsonObject {
        while (true) {
            val queued = events.removeFirstOrNull()
            if (queued != null) {
                val event = queued["event"].text
                if (name == null && event == "overrides.changed") continue
                if (name == null || event == name) return queued
                continue
            }
            val frame = nextFrame() ?: error("The server closed the connection.")
            sortFrame(frame)
        }
    }

    private fun sortFrame(frame: JsonObject) {
        if (frame["type"].text == "event") {
            events.addLast(frame)
        } else {
            replies[frame["id"]!!.jsonPrimitive.long] = frame
        }
    }

    private suspend fun <T> withRealTimeout(block: suspend () -> T): T =
        withContext(Dispatchers.Default) { withTimeout(5.seconds) { block() } }
}

internal val JsonElement?.text: String? get() = (this as? kotlinx.serialization.json.JsonPrimitive)?.content

internal fun JsonObject.obj(key: String): JsonObject = this[key]!!.jsonObject

internal fun json(text: String): JsonElement = RemoteJson.parseToJsonElement(text)
