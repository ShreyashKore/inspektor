package utils

import com.gyanoba.inspektor.utils.HeaderSanitizer
import com.gyanoba.inspektor.utils.sanitize
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderSanitizerTest {

    private val headers = mapOf(
        "Authorization" to listOf("Bearer token"),
        "Content-Type" to listOf("application/json"),
        "Cookie" to listOf("sessionId=abc123"),
    )

    @Test
    fun `test single header sanitizer`() {
        val sanitizer = HeaderSanitizer(placeholder = "***") { it == "Authorization" }
        val sanitizedHeaders = headers.sanitize(listOf(sanitizer))

        assertEquals(listOf("***"), sanitizedHeaders["Authorization"])
        assertEquals(listOf("application/json"), sanitizedHeaders["Content-Type"])
    }

    @Test
    fun `test multiple header sanitizers`() {
        val sanitizers = listOf(
            HeaderSanitizer(placeholder = "***") { it == "Authorization" },
            HeaderSanitizer(placeholder = "###") { it == "Cookie" }
        )
        val sanitizedHeaders = headers.sanitize(sanitizers)

        assertEquals(listOf("***"), sanitizedHeaders["Authorization"])
        assertEquals(listOf("application/json"), sanitizedHeaders["Content-Type"])
        assertEquals(listOf("###"), sanitizedHeaders["Cookie"])
    }

    @Test
    fun `test no header sanitizer`() {
        val sanitizedHeaders = headers.sanitize(emptyList())

        assertEquals(listOf("Bearer token"), sanitizedHeaders["Authorization"])
        assertEquals(listOf("application/json"), sanitizedHeaders["Content-Type"])
    }

    @Test
    fun `every value of a matched header is replaced`() {
        val multi = mapOf("Set-Cookie" to listOf("a=1", "b=2"))
        val sanitized = multi.sanitize(listOf(HeaderSanitizer("#") { it == "Set-Cookie" }))

        assertEquals(listOf("#", "#"), sanitized["Set-Cookie"])
    }
}
