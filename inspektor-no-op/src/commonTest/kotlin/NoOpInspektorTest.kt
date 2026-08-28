import com.gyanoba.inspektor.Inspektor
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.setApplicationId
import com.gyanoba.inspektor.openInspektor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

/**
 * These tests are the contract of the no-op artifact: installing [Inspektor] from it must be
 * completely inert. If any of them starts failing, the artifact is no longer a safe production
 * replacement for the real library.
 */
class NoOpInspektorTest {

    private fun mockClient(configure: HttpClient.() -> Unit = {}): Pair<HttpClient, MockEngine> {
        val engine = MockEngine { _ ->
            respond(
                content = RESPONSE_BODY,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }
        return HttpClient(engine) { install(Inspektor) }.apply(configure) to engine
    }

    @Test
    fun `request passes through untouched`() = runTest {
        val (client, engine) = mockClient()

        client.post("https://example.com/echo") {
            contentType(ContentType.Text.Plain)
            header(HttpHeaders.Authorization, "Bearer secret")
            setBody(REQUEST_BODY)
        }

        val sent = engine.requestHistory.single()
        assertEquals(REQUEST_BODY, sent.body.toByteArray().decodeToString())
        assertEquals("Bearer secret", sent.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `response body is readable and unmodified`() = runTest {
        val (client, _) = mockClient()

        val response = client.post("https://example.com/echo") { setBody(REQUEST_BODY) }

        assertEquals(RESPONSE_BODY, response.bodyAsText())
    }

    @Test
    fun `response body can be read more than once`() = runTest {
        // The real plugin installs a ResponseObserver that tees the body. The no-op installs
        // nothing, so this asserts we did not accidentally consume or buffer the channel.
        val (client, _) = mockClient()

        val response = client.post("https://example.com/echo") { setBody(REQUEST_BODY) }

        assertEquals(RESPONSE_BODY, response.bodyAsText())
        assertEquals(RESPONSE_BODY, response.bodyAsText())
    }

    @OptIn(UnstableInspektorAPI::class)
    @Test
    fun `every configuration option is accepted and ignored`() = runTest {
        var filterCalled = false
        var sanitizerCalled = false

        val engine = MockEngine { respond(RESPONSE_BODY) }
        val client = HttpClient(engine) {
            install(Inspektor) {
                level = LogLevel.BODY
                maxContentLength = 1
                showNotifications = true
                retentionDuration = 1.days
                filter { filterCalled = true; true }
                sanitizeHeader { sanitizerCalled = true; true }
            }
        }

        client.post("https://example.com/echo") { setBody(REQUEST_BODY) }

        // Nothing is inspected, so nothing the caller registered is ever invoked.
        assertEquals(false, filterCalled)
        assertEquals(false, sanitizerCalled)
    }

    @OptIn(UnstableInspektorAPI::class)
    @Test
    fun `entry points do not throw`() {
        openInspektor()
        setApplicationId("com.example.myapp")
    }

    private companion object {
        const val REQUEST_BODY = "request-body"
        const val RESPONSE_BODY = "response-body"
    }
}
