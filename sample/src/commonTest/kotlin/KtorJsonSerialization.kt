import io.ktor.http.headersOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test

class KtorJsonSerialization {
    val json = Json

    @Test
    fun testHeadersEncoding() {

        val headers = headersOf(
            "Content-Type" to listOf("application/json"),
            "Accept" to listOf("application/json")
        )
        val headersString = json.encodeToString(headers.entries())
        println(headersString)
    }

    @Test
    fun testHeadersDecoding() {

        val headers =
            "[{\"Content-Type\":[\"application/json\"]},{\"Accept\":[\"application/json\"]}]"
        val headersString = json.decodeFromString<Set<Map.Entry<String, List<String>>>>(headers)
        println(headersString)
    }

}