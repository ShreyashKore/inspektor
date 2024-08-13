package utils

import io.ktor.client.utils.buildHeaders
import io.ktor.http.Headers


internal fun Headers.sanitizeHeaders(
    headerSanitizers: List<HeaderSanitizer>,
): Headers = buildHeaders {
    forEach { name, values ->
        val sanitizedValues = values.map { value ->
            headerSanitizers.firstOrNull { it.predicate(name) }?.placeholder ?: value
        }
        appendAll(name, sanitizedValues)
    }
}


internal class HeaderSanitizer(
    val placeholder: String = "***",
    val predicate: (String) -> Boolean,
)