package utils

import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderSanitizerTest {

    @Test
    fun `test single header sanitizer`() {
        val headers = headersOf(
            "Authorization" to listOf("Bearer token"),
            "Content-Type" to listOf("application/json")
        )
        val sanitizer = HeaderSanitizer(placeholder = "***") { it == "Authorization" }
        val sanitizedHeaders = headers.sanitizeHeaders(listOf(sanitizer))

        assertEquals("***", sanitizedHeaders["Authorization"])
        assertEquals("application/json", sanitizedHeaders["Content-Type"])
    }

    @Test
    fun `test multiple header sanitizers`() {
        val headers = headersOf(
            "Authorization" to listOf("Bearer token"),
            "Content-Type" to listOf("application/json"),
            "Cookie" to listOf("sessionId=abc123")
        )
        val sanitizers = listOf(
            HeaderSanitizer(placeholder = "***") { it == "Authorization" },
            HeaderSanitizer(placeholder = "###") { it == "Cookie" }
        )
        val sanitizedHeaders = headers.sanitizeHeaders(sanitizers)

        assertEquals("***", sanitizedHeaders["Authorization"])
        assertEquals("application/json", sanitizedHeaders["Content-Type"])
        assertEquals("###", sanitizedHeaders["Cookie"])
    }

    @Test
    fun `test no header sanitizer`() {
        val headers = headersOf(
            "Authorization" to listOf("Bearer token"),
            "Content-Type" to listOf("application/json")
        )
        val sanitizedHeaders = headers.sanitizeHeaders(emptyList())

        assertEquals("Bearer token", sanitizedHeaders["Authorization"])
        assertEquals("application/json", sanitizedHeaders["Content-Type"])
    }
}