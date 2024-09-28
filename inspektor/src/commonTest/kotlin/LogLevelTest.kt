import com.gyanoba.inspektor.ClientCallLogger
import com.gyanoba.inspektor.DisableLogging
import com.gyanoba.inspektor.Inspektor
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.DriverFactory
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.InspektorDatabase
import com.gyanoba.inspektor.data.adapters.instantAdapter
import com.gyanoba.inspektor.data.adapters.setMapEntryAdapter
import com.gyanoba.inspektor.data.setApplicationId
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class LogLevelTest {
    private val db = createTestDb()

    init {
        @OptIn(UnstableInspektorAPI::class)
        setApplicationId("com.test.inspektor")
    }

    @Test
    fun `when logLevel is NONE then nothing is recorded`() = runTest {
        db.httpTransactionQueries.deleteAll()
        val client = createMockClient(logLevel = LogLevel.NONE) {
            respondOk("OK")
        }

        val response = client.post("http://localhost/text") { setBody("Request") }
        val disableLogging = response.call.attributes.getOrNull(DisableLogging)
        val clientCallLogger = response.call.attributes.getOrNull(ClientCallLogger)
        assertNull(disableLogging)
        assertNull(clientCallLogger)
        val count = db.httpTransactionQueries.getAllCount().executeAsOne()
        assertEquals(0, count)
    }

    @Test
    fun `when logLevel is INFO then only info fields are recorded`() = runTest {
        db.httpTransactionQueries.deleteAll()
        val client = createMockClient(logLevel = LogLevel.INFO) {
            respond(
                "OK",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/plain"))
            )
        }

        val response = client.post("http://localhost/text") {
            headers.append("Content-Type", "text/plain")
            setBody("Request")
        }
        response.call.attributes[ClientCallLogger].joinResponseLogged()
        val transaction = db.httpTransactionQueries.getAll().executeAsOne()

        mapOf(
            "request date" to transaction.requestDate,
            "response date" to transaction.responseDate,
        ).forEach {
            assertNotNull(it.value, "${it.key} should not be null")
        }

        mapOf(
            "request headers" to transaction.requestHeaders,
            "request body" to transaction.requestBody,
            "response headers" to transaction.responseHeaders,
            "response body" to transaction.responseBody,
        ).forEach {
            assertNull(it.value, "${it.key} should be null")
        }
    }


    @Test
    fun `when logLevel is HEADERS then only info and headers fields are recorded`() = runTest {
        db.httpTransactionQueries.deleteAll()
        val client = createMockClient(logLevel = LogLevel.HEADERS) {
            respond(
                "OK",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/plain"))
            )
        }

        val response = client.post("http://localhost/text") {
            headers.append("Content-Type", "text/plain")
            setBody("Request")
        }
        response.call.attributes[ClientCallLogger].joinResponseLogged()
        val transaction = db.httpTransactionQueries.getAll().executeAsOne()

        mapOf(
            "request date" to transaction.requestDate,
            "response date" to transaction.responseDate,
        ).forEach {
            assertNotNull(it.value, "${it.key} should not be null")
        }

        mapOf(
            "request headers" to transaction.requestHeaders,
            "response headers" to transaction.responseHeaders,
        ).forEach {
            assertNotNull(it.value, "${it.key} should not be null")
        }

        mapOf(
            "request body" to transaction.requestBody,
            "response body" to transaction.responseBody,
        ).forEach {
            assertNull(it.value, "${it.key} should be null")
        }
    }


    @Test
    fun `when logLevel is BODY then all fields are recorded`() = runTest {
        db.httpTransactionQueries.deleteAll()
        val client = createMockClient(logLevel = LogLevel.BODY) {
            respond(
                "OK",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/plain"))
            )
        }

        val response = client.post("http://localhost/text") {
            headers.append("Content-Type", "text/plain")
            setBody("Request")
        }
        response.call.attributes[ClientCallLogger].joinResponseLogged()

        val transaction = db.httpTransactionQueries.getAll().executeAsOne()

        mapOf(
            "request date" to transaction.requestDate,
            "response date" to transaction.responseDate,
            "request headers" to transaction.requestHeaders,
            "response headers" to transaction.responseHeaders,
            "request body" to transaction.requestBody,
            "response body" to transaction.responseBody,
        ).forEach {
            assertNotNull(it.value, "${it.key} should not be null")
        }
    }


    private fun createTestDb(): InspektorDatabase {
        val driver = DriverFactory.createTempDbDriver()
        return InspektorDatabase(
            driver, HttpTransaction.Adapter(
                requestDateAdapter = instantAdapter,
                responseDateAdapter = instantAdapter,
                requestHeadersAdapter = setMapEntryAdapter,
                responseHeadersAdapter = setMapEntryAdapter
            )
        )
    }

    private fun createMockClient(
        logLevel: LogLevel,
        block: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        return HttpClient(MockEngine { block(it) }) {
            install(Inspektor) {
                level = logLevel
                this.dataSource = InspektorDataSourceImpl(db)
            }
        }
    }


}