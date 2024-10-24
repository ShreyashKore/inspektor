import com.gyanoba.inspektor.ClientCallLogger
import com.gyanoba.inspektor.HttpClientCallLogger
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
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.request
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.test.runTest
import utils.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals


class KtorMultipartTest: TestBase() {

    @Test
    fun testMultipartRequest() {
        db.httpTransactionQueries.deleteAll()
        val client = createMockClient { request ->
            when (request.url.encodedPath) {
                "/multipart" -> respondOk("OK")
                "/text" -> respondOk("OK")
                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }

        runTest {
            val response = client.post("http://localhost/multipart") {
                setBody(MultiPartFormDataContent(
                    formData {
                        append("key1", "value1")
                        append("key2", "value2")
                        append("key3", byteArrayOf(9, 8, 7, 6))
                    }
                ))
            }
            val request = response.request
            request.attributes[ClientCallLogger].joinRequestLogged()
            val transaction = db.httpTransactionQueries.getLast().executeAsOne()
            assertEquals(request.method.value, transaction.method)
        }
    }


    @Test
    fun testBinaryRequest() {
        db.httpTransactionQueries.deleteAll()
        val client = createMockClient { request ->
            when (request.url.encodedPath) {
                "/binary" -> respondOk("OK")
                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }

        runTest {
            val response = client.post("http://localhost/binary") {
                setBody(ByteArrayContent(byteArrayOf(9, 8, 7, 6)))
            }
            val request = response.request
            println(db.httpTransactionQueries.getAll().executeAsList())
            val transaction = db.httpTransactionQueries.getLast().executeAsOne()
            assertEquals(request.method.value, transaction.method)
        }
    }
}