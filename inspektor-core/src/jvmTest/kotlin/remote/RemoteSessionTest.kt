package remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.remote.RemoteAppInfo
import com.gyanoba.inspektor.remote.RemoteSession
import com.gyanoba.inspektor.remote.helloMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(UnstableInspektorAPI::class)
class RemoteSessionTest {
    private val dataSource = testDataSource()
    private val overrides = testOverrides()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app = RemoteAppInfo(
        id = "com.example.app",
        name = "Example",
        version = "1.4.0 (51)",
        pid = 12345,
        process = "com.example.app",
    )

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun connect(): Pair<TestClient, PipeConnection> {
        val (clientSide, serverSide) = connectionPair()
        RemoteSession(
            connection = serverSide,
            dataSource = dataSource,
            overrides = overrides,
            hello = { helloMessage(app, "store-1", dataSource.oldestTransactionId()) },
            parent = scope,
        ).start()
        return TestClient(clientSide) to clientSide
    }

    private suspend fun record(vararg urls: String): List<Long> = urls.map {
        dataSource.insertHttpTransaction(transaction(url = it))
    }

    @Test
    fun `hello comes first and names the app and the store`() = runBlocking<Unit> {
        record("https://a.example.com/1", "https://a.example.com/2")
        val (client, _) = connect()

        val hello = client.hello()

        assertEquals("1", hello["protocol"].text)
        assertEquals("kotlin", hello.obj("sdk")["runtime"].text)
        assertEquals("true", hello.obj("sdk")["overrides"].text)
        assertEquals("com.example.app", hello.obj("app")["id"].text)
        assertEquals("12345", hello.obj("app")["pid"].text)
        assertEquals("store-1", hello.obj("store")["id"].text)
        assertEquals("1", hello.obj("store")["oldestId"].text)
    }

    @Test
    fun `subscribe backfills after afterId, then streams every write`() = runBlocking<Unit> {
        record("https://a.example.com/1", "https://a.example.com/2", "https://a.example.com/3")
        val (client, _) = connect()
        client.hello()

        val reply = client.request("subscribe", buildJsonObject { put("afterId", "1") })
        assertEquals(JsonObject(emptyMap()), reply["result"])

        assertEquals("2", client.nextEvent()["data"]!!.jsonObject["id"].text)
        assertEquals("3", client.nextEvent()["data"]!!.jsonObject["id"].text)

        val id = dataSource.insertHttpTransaction(transaction(responseCode = null))
        val started = client.nextEvent()
        assertEquals("transaction.started", started["event"].text)
        assertEquals(id.toString(), started.obj("data")["id"].text)
        assertNull(started.obj("data")["response"])

        dataSource.updateHttpTransaction(transaction().copy(id = id))
        val updated = client.nextEvent()
        assertEquals("transaction.updated", updated["event"].text)
        assertEquals("200", updated.obj("data").obj("response")["code"].text)
    }

    @Test
    fun `a transaction carries its request, response and headers as pairs`() = runBlocking<Unit> {
        dataSource.insertHttpTransaction(
            transaction(responseBody = "short", responsePayloadSize = 9000),
        )
        val (client, _) = connect()
        client.hello()
        client.request("subscribe")

        val tx = client.nextEvent().obj("data")

        assertEquals("POST", tx["method"].text)
        assertEquals("https://api.example.com/v1/items", tx["url"].text)
        assertEquals("h2", tx["protocol"].text)
        assertEquals("2026-10-18T09:12:03.412Z", tx["requestDate"].text)
        assertEquals("231", tx["tookMs"].text)
        assertEquals(
            buildJsonArray {
                add(buildJsonArray { add(json("\"content-type\"")); add(json("\"application/json\"")) })
                add(buildJsonArray { add(json("\"accept\"")); add(json("\"application/json\"")) })
                add(buildJsonArray { add(json("\"accept\"")); add(json("\"text/plain\"")) })
            },
            tx.obj("request")["headers"],
        )
        assertEquals("available", tx.obj("request")["body"].text)
        assertEquals("truncated", tx.obj("response")["body"].text)
        assertEquals("TLSv1.3", tx.obj("response")["tlsVersion"].text)
        assertEquals(2, tx.obj("response")["headers"]!!.jsonArray.size)
        assertNull(tx.obj("request")["bodyData"])
    }

    @Test
    fun `small bodies travel inline when the client asks`() = runBlocking<Unit> {
        dataSource.insertHttpTransaction(transaction())
        val (client, _) = connect()
        client.hello()
        client.request("subscribe", buildJsonObject { put("inlineBodyLimit", 1024) })

        val response = client.nextEvent().obj("data").obj("response")

        assertEquals("""{"id":7}""", response["bodyData"].text)
        assertEquals("utf8", response["encoding"].text)
    }

    @Test
    fun `body get returns text, a byte range as base64, and errors for what is missing`() =
        runBlocking<Unit> {
            val (id) = record("https://a.example.com/1")
            val (client, _) = connect()
            client.hello()

            val whole = client.request("body.get", buildJsonObject {
                put("id", id.toString())
                put("part", "response")
            }).obj("result")
            assertEquals("utf8", whole["encoding"].text)
            assertEquals("""{"id":7}""", whole["data"].text)
            assertEquals("false", whole["truncated"].text)

            val range = client.request("body.get", buildJsonObject {
                put("id", id.toString())
                put("part", "request")
                put("offset", 2)
                put("length", 4)
            }).obj("result")
            assertEquals("base64", range["encoding"].text)
            assertEquals("name", String(Base64.getDecoder().decode(range["data"].text)))

            val missingTransaction = client.request("body.get", buildJsonObject {
                put("id", "999")
                put("part", "response")
            })
            assertEquals("not_found", missingTransaction.obj("error")["code"].text)

            val missingBody = client.request("body.get", buildJsonObject {
                put("id", id.toString())
                put("part", "originalResponse")
            })
            assertEquals("not_found", missingBody.obj("error")["code"].text)

            val badPart = client.request("body.get", buildJsonObject {
                put("id", id.toString())
                put("part", "trailers")
            })
            assertEquals("invalid_params", badPart.obj("error")["code"].text)
        }

    @Test
    fun `transactions list pages by id`() = runBlocking<Unit> {
        record("https://a.example.com/1", "https://a.example.com/2", "https://a.example.com/3")
        val (client, _) = connect()
        client.hello()

        val first = client.request("transactions.list", buildJsonObject { put("limit", 2) })
            .obj("result")
        assertEquals(listOf("1", "2"), first["transactions"]!!.ids())
        assertEquals("true", first["hasMore"].text)

        val rest = client.request(
            "transactions.list",
            buildJsonObject {
                put("afterId", "2")
                put("limit", 2)
            },
        ).obj("result")
        assertEquals(listOf("3"), rest["transactions"]!!.ids())
        assertEquals("false", rest["hasMore"].text)

        val picked = client.request(
            "transactions.get",
            buildJsonObject { put("ids", buildJsonArray { add(json("\"3\"")); add(json("\"77\"")) }) },
        ).obj("result")
        assertEquals(listOf("3"), picked["transactions"]!!.ids())
    }

    @Test
    fun `clearing empties the store and tells subscribers`() = runBlocking<Unit> {
        record("https://a.example.com/1")
        val (client, _) = connect()
        client.hello()
        client.request("subscribe")
        client.nextEvent()

        client.request("transactions.clear")

        assertEquals("transactions.cleared", client.nextEvent()["event"].text)
        assertEquals(0, dataSource.transactionCount())
    }

    @Test
    fun `overrides can be listed, added and deleted, and changes are pushed`() = runBlocking<Unit> {
        val (client, _) = connect()
        client.hello()
        client.request("subscribe")

        val stored = client.request(
            "overrides.put",
            json(
                """
                {"id": 0, "name": "Fail the items call",
                 "type": {"type": "http", "method": "Get"},
                 "matchers": [{"type": "pathMatcher", "path": "/v1/items"}],
                 "action": {"type": "FixedResponse", "statusCode": 500, "responseBody": "boom"}}
                """.trimIndent(),
            ),
        ).obj("result")
        assertEquals("1", stored["id"].text)
        assertEquals("Fail the items call", stored["name"].text)

        val listed = client.request("overrides.list")["result"] as JsonArray
        assertEquals(1, listed.size)
        assertEquals(
            "500",
            listed.first().jsonObject.obj("action")["statusCode"].text,
        )
        // The first push is the list as it stood at subscribe time; wait for the one with ours.
        var changed = client.nextEvent("overrides.changed")["data"] as JsonArray
        while (changed.isEmpty()) {
            changed = client.nextEvent("overrides.changed")["data"] as JsonArray
        }
        assertEquals("Fail the items call", changed.first().jsonObject["name"].text)

        client.request("overrides.delete", buildJsonObject { put("id", "1") })
        assertEquals(0, (client.request("overrides.list")["result"] as JsonArray).size)

        val missing = client.request("overrides.delete", buildJsonObject { put("id", "1") })
        assertEquals("not_found", missing.obj("error")["code"].text)

        val garbage = client.request("overrides.put", buildJsonObject { put("nonsense", true) })
        assertEquals("invalid_params", garbage.obj("error")["code"].text)
    }

    @Test
    fun `an unknown method is an error, and the session carries on`() = runBlocking<Unit> {
        val (client, _) = connect()
        client.hello()

        val reply = client.request("debugger.attach")
        assertEquals("unknown_method", reply.obj("error")["code"].text)
        assertEquals(JsonObject(emptyMap()), client.request("ping")["result"])
    }

    @Test
    fun `a frame over the limit closes the connection without a reply`() = runBlocking<Unit> {
        val (client, connection) = connect()
        client.hello()

        connection.writeRaw(byteArrayOf(0x7F, -1, -1, -1))

        assertNull(client.nextFrame())
    }

    @Test
    fun `a browser's HTTP request closes the connection without a reply`() = runBlocking<Unit> {
        val (client, connection) = connect()
        client.hello()

        connection.writeRaw("GET / HTTP/1.1\r\nHost: 127.0.0.1:38470\r\n\r\n".encodeToByteArray())

        assertNull(client.nextFrame())
    }

    @Test
    fun `rows without a URL are not sent`() = runBlocking<Unit> {
        dataSource.insertHttpTransaction(transaction(url = ""))
        record("https://a.example.com/2")
        val (client, _) = connect()
        client.hello()

        val listed = client.request("transactions.list").obj("result")

        assertEquals(listOf("2"), listed["transactions"]!!.ids())
        assertFalse(listed["hasMore"].text.toBoolean())
    }

    private fun kotlinx.serialization.json.JsonElement.ids(): List<String?> =
        jsonArray.map { it.jsonObject["id"].text }
}
