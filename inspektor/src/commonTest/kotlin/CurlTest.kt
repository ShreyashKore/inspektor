import com.gyanoba.inspektor.data.MutableHttpTransaction
import com.gyanoba.inspektor.data.toImmutable
import com.gyanoba.inspektor.utils.toCurlString
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CurlTest {

    @Test
    fun `test curl command with basic GET request`() {
        val transaction = MutableHttpTransaction(
            id = -1,
            method = "GET",
            url = "https://example.com",
            requestHeaders = null,
            requestBody = null,
        ).toImmutable()

        val curlCommand = transaction.toCurlString()
        assertTrue(curlCommand.contains("--request GET"))
        assertTrue(curlCommand.contains("--url 'https://example.com'"))
    }

    @Test
    fun `test curl command with headers`() {
        val headers = mapOf(
            "Authorization" to listOf("Bearer authToken"),
            "User-Agent" to listOf("Firefox/1.0")
        )

        val transaction = MutableHttpTransaction(
            id = -1,
            method = "POST",
            url = "https://example.com/api",
            requestHeaders = headers.entries,
            requestBody = null,
            isRequestBodyEncoded = false,
            requestContentType = "application/json",
        ).toImmutable()

        val curlCommand = transaction.toCurlString()
        assertTrue(curlCommand.contains("--header \"Authorization: Bearer authToken\""))
        assertTrue(curlCommand.contains("--header \"User-Agent: Firefox/1.0\""))
        assertTrue(curlCommand.contains("--header \"Content-Type: application/json\""))
    }

    @Test
    fun `test curl command with request body`() {
        val transaction = MutableHttpTransaction(
            id = -1,
            method = "POST",
            url = "https://example.com/submit",
            requestBody = "{\"key\":\"value\"}",
            isRequestBodyEncoded = false,
        ).toImmutable()

        val curlCommand = transaction.toCurlString()
        assertTrue(curlCommand.contains("--data-raw '{\"key\":\"value\"}'"))
    }

    @Test
    fun `test encoded request body gives curl command without body`() {
        val transaction = MutableHttpTransaction(
            id = -1,
            method = "POST",
            url = "https://example.com/upload",
            requestBody = "encodedData",
            isRequestBodyEncoded = true,
        ).toImmutable()

        val curlCommand = transaction.toCurlString()
        assertFalse(curlCommand.contains("--data-raw 'encodedData'"))
    }
}
