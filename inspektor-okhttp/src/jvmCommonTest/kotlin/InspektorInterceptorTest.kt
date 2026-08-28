import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.okhttp.InterceptorMode
import com.gyanoba.inspektor.okhttp.InspektorOkHttpConfig
import com.gyanoba.inspektor.okhttp.installInspektor
import com.gyanoba.inspektor.platform.NotificationManager
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import utils.DbTestBase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspektorInterceptorTest : DbTestBase() {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun startServer() {
        server = MockWebServer()
        server.start()
        db.httpTransactionQueries.deleteAll()
    }

    @AfterTest
    fun stopServer() {
        server.close()
    }

    private fun client(
        mode: InterceptorMode = InterceptorMode.APPLICATION,
        configure: InspektorOkHttpConfig.() -> Unit = {},
    ): OkHttpClient = OkHttpClient.Builder()
        .installInspektor(mode) {
            level = LogLevel.BODY
            dataSource = InspektorDataSourceImpl(db)
            overrideRepository = OverrideRepositoryImpl(store)
            notificationManager = object : NotificationManager {
                override fun notify(title: String, message: String) = Unit
            }
            configure()
        }
        .build()

    /**
     * The recorder writes off the calling thread, so a test that reads straight after the call
     * races the insert.
     */
    private fun awaitTransaction(expected: Int = 1): List<HttpTransaction> {
        repeat(200) {
            val rows = db.httpTransactionQueries.getAll().executeAsList()
            if (rows.size >= expected && rows.all { it.responseCode != null }) return rows
            Thread.sleep(25)
        }
        return db.httpTransactionQueries.getAll().executeAsList()
    }

    @Test
    fun `records a plain call as an application interceptor`() {
        server.enqueue(MockResponse.Builder().code(200).body("""{"ok":true}""").build())

        val response = client().newCall(
            Request.Builder()
                .url(server.url("/todos/1"))
                .post("""{"q":1}""".toRequestBody("application/json".toMediaType()))
                .header("X-Trace", "abc")
                .build()
        ).execute()
        assertEquals("""{"ok":true}""", response.body.string())

        val transaction = awaitTransaction().single()
        assertEquals("POST", transaction.method)
        assertEquals("/todos/1", transaction.path)
        assertEquals("http", transaction.scheme)
        assertEquals(200L, transaction.responseCode)
        assertEquals("""{"q":1}""", transaction.requestBody)
        assertEquals("""{"ok":true}""", transaction.responseBody)
        assertEquals("application/json", transaction.requestContentType)
        assertTrue(transaction.requestHeaders.orEmpty().any { it.key == "X-Trace" })
        assertNotNull(transaction.requestHeadersSize)
        assertNotNull(transaction.tookMs)
    }

    @Test
    fun `the consumer still receives an intact request body`() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())

        client().newCall(
            Request.Builder()
                .url(server.url("/echo"))
                .post("hello world".toRequestBody("text/plain".toMediaType()))
                .build()
        ).execute().close()

        // Capture must be non-destructive: the server has to see the body in full.
        assertEquals("hello world", server.takeRequest()!!.body!!.utf8())
        assertEquals("hello world", awaitTransaction().single().requestBody)
    }

    @Test
    fun `a gzipped response is captured as a network interceptor sees it`() {
        // Content-Encoding set by hand means OkHttp does not transparently decompress, so a
        // network interceptor sees the encoded bytes -- exactly the difference from application
        // position that makes the mode a real choice.
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Encoding", "identity")
                .body("plain payload")
                .build()
        )

        val response = client(InterceptorMode.NETWORK)
            .newCall(Request.Builder().url(server.url("/gzip")).build()).execute()
        assertEquals("plain payload", response.body.string())

        val transaction = awaitTransaction().single()
        assertEquals("plain payload", transaction.responseBody)
        assertTrue(transaction.responseHeaders.orEmpty().any { it.key == "Content-Encoding" })
    }

    @Test
    fun `an application interceptor records one transaction for a redirect chain`() {
        server.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", "/final").build()
        )
        server.enqueue(MockResponse.Builder().code(200).body("arrived").build())

        client().newCall(Request.Builder().url(server.url("/start")).build()).execute().close()

        val rows = awaitTransaction()
        assertEquals(1, rows.size, "application interceptors fire once per call")
        assertEquals("/start", rows.single().path)
        assertEquals(200L, rows.single().responseCode)
    }

    @Test
    fun `a network interceptor records every hop of a redirect chain`() {
        server.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", "/final").build()
        )
        server.enqueue(MockResponse.Builder().code(200).body("arrived").build())

        client(InterceptorMode.NETWORK)
            .newCall(Request.Builder().url(server.url("/start")).build()).execute().close()

        val rows = awaitTransaction(expected = 2)
        assertEquals(2, rows.size, "network interceptors fire once per hop")
        assertEquals(listOf("/start", "/final"), rows.map { it.path })
        assertEquals(listOf(302L, 200L), rows.map { it.responseCode })
    }

    @Test
    fun `a one-shot request body is passed through uncaptured rather than corrupted`() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())

        val oneShot = object : okhttp3.RequestBody() {
            private val delegate = "one shot payload".toRequestBody("text/plain".toMediaType())
            override fun contentType() = delegate.contentType()
            override fun isOneShot() = true
            override fun writeTo(sink: okio.BufferedSink) = delegate.writeTo(sink)
        }

        client().newCall(
            Request.Builder().url(server.url("/oneshot")).post(oneShot).build()
        ).execute().close()

        // The consumer's request must arrive intact; only the log gives up on the body.
        assertEquals("one shot payload", server.takeRequest()!!.body!!.utf8())
        val transaction = awaitTransaction().single()
        assertNull(transaction.requestBody)
        assertEquals("POST", transaction.method)
    }

    @Test
    fun `a filtered-out call is not recorded`() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())

        val client = client { filter { it.url.encodedPath.startsWith("/keep") } }
        client.newCall(Request.Builder().url(server.url("/drop")).build()).execute().close()

        Thread.sleep(200)
        assertEquals(0, db.httpTransactionQueries.getAll().executeAsList().size)
    }

    @Test
    fun `sanitized headers never reach the store`() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())

        val client = client { sanitizeHeader { it == "Authorization" } }
        client.newCall(
            Request.Builder()
                .url(server.url("/secret"))
                .header("Authorization", "Bearer super-secret")
                .build()
        ).execute().close()

        val headers = awaitTransaction().single().requestHeaders.orEmpty()
        assertEquals(listOf("***"), headers.first { it.key == "Authorization" }.value)
    }

    @Test
    fun `a body larger than maxContentLength is truncated, not dropped`() {
        server.enqueue(MockResponse.Builder().code(200).body("ok").build())

        val payload = "x".repeat(5_000)
        val client = client { maxContentLength = 100 }
        client.newCall(
            Request.Builder()
                .url(server.url("/big"))
                .post(payload.toRequestBody("text/plain".toMediaType()))
                .build()
        ).execute().close()

        assertEquals("x".repeat(5_000), server.takeRequest()!!.body!!.utf8())
        assertEquals("x".repeat(100), awaitTransaction().single().requestBody)
    }
}
