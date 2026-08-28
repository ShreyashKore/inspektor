import com.gyanoba.inspektor.ClientCallLogger
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.data.HostMatcher
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.Matcher
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.UrlMatcher
import com.gyanoba.inspektor.data.UrlRegexMatcher
import io.github.xxfast.kstore.extensions.plus
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import utils.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class OverridingTest : TestBase() {

    private val matchers = listOf(
        UrlMatcher("http://localhost/text"),
        UrlRegexMatcher("http://localhost/.*"),
        HostMatcher("localhost"),
        PathMatcher("/text")
    )

    private val overrideActions = listOf(
        OverrideAction(
            type = OverrideAction.Type.FixedRequest,
            requestHeaders = mutableMapOf("Custom" to listOf("Overridden Request-Header")),
            requestBody = "Overridden-Request"
        ),
        OverrideAction(
            type = OverrideAction.Type.FixedResponse,
            responseHeaders = mutableMapOf("Custom" to listOf("Overridden Response-Header")),
            responseBody = "Overridden-Response"
        ),
        OverrideAction(
            type = OverrideAction.Type.FixedRequestResponse,
            requestHeaders = mutableMapOf("Custom" to listOf("Overridden Request-Header")),
            requestBody = "Overridden-Request",
            responseHeaders = mutableMapOf("Custom" to listOf("Overridden Response-Header")),
            responseBody = "Overridden-Response"
        ),
        OverrideAction(
            type = OverrideAction.Type.None
        )
    )

    @Test
    fun testSingleMatchers() = runTest {
        matchers.forEach { matcher ->
            overrideActions.forEach { action ->
                val transaction = performTest(listOf(matcher), action)
                transaction.assert(listOf(matcher), action)
            }
        }
    }

    @Test
    fun testMultipleMatchers() = runTest {
        val combinations = listOf(
            listOf(matchers[0], matchers[2]),
            listOf(matchers[1], matchers[3]),
            listOf(matchers[0], matchers[1], matchers[2]),
            listOf(matchers[1], matchers[2], matchers[3])
        )

        combinations.forEach { matcherList ->
            overrideActions.forEach { action ->
                val transaction = performTest(matcherList, action)
                transaction.assert(matcherList, action)
            }
        }
    }

    private suspend fun performTest(matcherList: List<Matcher>, action: OverrideAction): HttpTransaction {
        db.httpTransactionQueries.deleteAll()
        store.reset()
        store.plus(
            Override(
                id = 0,
                type = HttpRequest(HttpMethod.Post),
                matchers = matcherList,
                action = action
            )
        )

        val client = createMockClient(logLevel = LogLevel.BODY) {
            respond(
                "Response",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Content-Type" to listOf("text/plain"),
                    "Custom" to listOf("Response-Header")
                )
            )
        }

        val response = client.post("http://localhost/text") {
            headers.append("Content-Type", "text/plain")
            headers.append("Custom", "Request-Header")
            setBody("Request")
        }
        response.call.attributes[ClientCallLogger].joinResponseLogged()

        return db.httpTransactionQueries.getAll().executeAsOne()
    }

    private fun HttpTransaction.assert(
        matcherList: List<Matcher>,
        action: OverrideAction,
    ) {
        val paramDescription = "\n" +
                "\t- $matcherList\n" +
                "\t- ${action.type} $action"
        if (action.type == OverrideAction.Type.FixedRequest || action.type == OverrideAction.Type.FixedRequestResponse) {
            assertEquals(
                "Overridden Request-Header",
                requestHeaders?.firstOrNull { it.key == "Custom" }?.value?.first(),
                "Request headers are not overridden correctly for$paramDescription"
            )
            assertEquals(
                "Overridden-Request",
                requestBody,
                "Request body is not overridden correctly for$paramDescription"
            )
        } else {
            assertEquals(
                "Request-Header",
                requestHeaders?.firstOrNull { it.key == "Custom" }?.value?.first(),
                "Request headers should not be overridden for$paramDescription"
            )
            assertEquals(
                "Request",
                requestBody,
                "Request body should not be overridden for$paramDescription"
            )
        }

        if (action.type == OverrideAction.Type.FixedResponse || action.type == OverrideAction.Type.FixedRequestResponse) {
            assertEquals(
                "Overridden Response-Header",
                responseHeaders?.firstOrNull { it.key == "Custom" }?.value?.first(),
                "Response headers are not overridden correctly"
            )
            assertEquals(
                "Overridden-Response",
                responseBody,
                "Response body is not overridden correctly"
            )
        } else {
            assertEquals(
                "Response-Header",
                responseHeaders?.firstOrNull { it.key == "Custom" }?.value?.first(),
                "Response headers should not be overridden $action $matcherList $responseBody"
            )
            assertEquals(
                "Response",
                responseBody,
                "Response body should not be overridden"
            )
        }
    }

    @Test
    fun givenOverrideIsDisabled_WhenMatchingCallIsMade_ItShouldNotBeOverridden() = runTest {
        db.httpTransactionQueries.deleteAll()
        store.reset()
        store.plus(
            Override(
                id = 0,
                type = HttpRequest(HttpMethod.Post),
                matchers = listOf(UrlMatcher("http://localhost/text")),
                action = OverrideAction(
                    type = OverrideAction.Type.FixedRequestResponse,
                    requestHeaders = mutableMapOf("Custom" to listOf("Overridden Request-Header")),
                    requestBody = "Overridden-Request",
                    responseHeaders = mutableMapOf("Custom" to listOf("Overridden Response-Header")),
                    responseBody = "Overridden-Response"
                ),
                enabled = false
            )
        )

        val client = createMockClient(logLevel = LogLevel.BODY) {
            respond(
                "Response",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    "Content-Type" to listOf("text/plain"),
                    "Custom" to listOf("Response-Header")
                )
            )
        }

        val response = client.post("http://localhost/text") {
            headers.append("Content-Type", "text/plain")
            headers.append("Custom", "Request-Header")
            setBody("Request")
        }
        response.call.attributes[ClientCallLogger].joinResponseLogged()

        val transaction = db.httpTransactionQueries.getAll().executeAsOne()
        assertEquals("Request-Header", transaction.requestHeaders?.firstOrNull { it.key == "Custom" }?.value?.first())
        assertEquals("Request", transaction.requestBody)
        assertEquals("Response-Header", transaction.responseHeaders?.firstOrNull { it.key == "Custom" }?.value?.first())
        assertEquals("Response", transaction.responseBody)
    }
}