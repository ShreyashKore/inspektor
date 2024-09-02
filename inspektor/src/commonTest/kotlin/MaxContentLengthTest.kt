import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import utils.NoOpDataSource
import kotlin.test.Test
import kotlin.test.assertTrue

class MaxContentLengthTest {

    @Test
    fun `test maxContentLength is respected by request`() = runBlocking {
        val maxContentLength = 100
        val largeRequestBody = "A".repeat(200)

        val client = HttpClient(MockEngine) {
            install(Inspektor) {
                level = LogLevel.BODY
                this.maxContentLength = maxContentLength
                this.dataSource = NoOpDataSource
            }
            engine {
                addHandler { request ->
                    assertTrue(request.body.toString().length <= maxContentLength)
                    respond("OK")
                }
            }
        }

        val response = client.post("http://localhost/test") {
            setBody(largeRequestBody)
        }

        val logger = response.call.attributes[ClientCallLogger]
        val requestBodySaved = logger.transaction.requestBody!!
        assertTrue(requestBodySaved.length <= maxContentLength)
    }

    @Test
    fun `test maxContentLength is respected by response`() = runBlocking {
        val maxContentLength = 100
        val largeResponseBody = "A".repeat(200)

        val client = HttpClient(MockEngine) {
            install(Inspektor) {
                level = LogLevel.BODY
                this.maxContentLength = maxContentLength
                this.dataSource = NoOpDataSource
            }
            engine {
                addHandler { request ->
                    respond(
                        content = largeResponseBody,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf("text/plain"))
                    )
                }
            }
        }

        val response = client.get("http://localhost/test")
        val logger = response.call.attributes[ClientCallLogger]
        logger.joinResponseLogged()
        val responseBodySaved = logger.transaction.responseBody!!
        assertTrue(responseBodySaved.length <= maxContentLength)
    }
}