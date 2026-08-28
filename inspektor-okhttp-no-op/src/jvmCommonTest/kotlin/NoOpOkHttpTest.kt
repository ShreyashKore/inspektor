import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.okhttp.InterceptorMode
import com.gyanoba.inspektor.okhttp.installInspektor
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * These tests are the contract of the no-op artifact: `installInspektor` must be completely inert.
 * If any of them starts failing, the artifact is no longer a safe production replacement for
 * `inspektor-okhttp`.
 */
class NoOpOkHttpTest {

    private lateinit var server: MockWebServer

    @BeforeTest
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun stopServer() {
        server.close()
    }

    @Test
    fun `no interceptor is registered`() {
        val client = OkHttpClient.Builder().installInspektor().build()

        assertTrue(client.interceptors.isEmpty(), "the no-op must add no application interceptor")
        assertTrue(
            client.networkInterceptors.isEmpty(),
            "the no-op must add no network interceptor"
        )
    }

    @Test
    fun `no interceptor is registered in network mode either`() {
        val client = OkHttpClient.Builder().installInspektor(InterceptorMode.NETWORK).build()

        assertTrue(client.interceptors.isEmpty())
        assertTrue(client.networkInterceptors.isEmpty())
    }

    @Test
    fun `request and response pass through untouched`() {
        server.enqueue(MockResponse.Builder().code(200).body("pong").build())
        val client = OkHttpClient.Builder().installInspektor().build()

        val response = client.newCall(
            Request.Builder()
                .url(server.url("/echo"))
                .header("Authorization", "Bearer secret")
                .post("ping".toRequestBody("text/plain".toMediaType()))
                .build()
        ).execute()

        assertEquals("pong", response.body.string())
        val recorded = server.takeRequest()!!
        assertEquals("ping", recorded.body!!.utf8())
        assertEquals("Bearer secret", recorded.headers["Authorization"])
    }

    @Test
    fun `configuration predicates are never invoked`() {
        server.enqueue(MockResponse.Builder().code(200).body("pong").build())
        var filterCalled = false
        var sanitizerCalled = false

        val client = OkHttpClient.Builder()
            .installInspektor {
                level = LogLevel.BODY
                maxContentLength = 10
                retentionDuration = 7.days
                showNotifications = false
                filter { filterCalled = true; true }
                sanitizeHeader { sanitizerCalled = true; true }
            }
            .build()

        client.newCall(Request.Builder().url(server.url("/any")).build()).execute().close()

        assertTrue(!filterCalled, "filter predicate must never run")
        assertTrue(!sanitizerCalled, "sanitizer predicate must never run")
    }
}
