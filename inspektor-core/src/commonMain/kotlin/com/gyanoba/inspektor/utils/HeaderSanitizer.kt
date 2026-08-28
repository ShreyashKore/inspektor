package com.gyanoba.inspektor.utils

import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * Replaces the value of any header whose name matches [predicate] with [placeholder].
 *
 * Deliberately expressed over header *names* only, so it is client-library neutral: Ktor
 * `Headers`, OkHttp `Headers` and `NSURLRequest.allHTTPHeaderFields` all reduce to the same
 * `Map<String, List<String>>`.
 */
@UnstableInspektorAPI
public class HeaderSanitizer(
    public val placeholder: String = "***",
    public val predicate: (String) -> Boolean,
)

/**
 * Applies [headerSanitizers] to every value in this map.
 *
 * Called from [com.gyanoba.inspektor.TransactionRecorder], i.e. on the store's side of the
 * integration boundary -- so a new integration cannot forget to sanitize.
 */
@UnstableInspektorAPI
public fun Map<String, List<String>>.sanitize(
    headerSanitizers: List<HeaderSanitizer>,
): Map<String, List<String>> {
    if (headerSanitizers.isEmpty()) return this
    return mapValues { (name, values) ->
        val placeholder = headerSanitizers.firstOrNull { it.predicate(name) }?.placeholder
            ?: return@mapValues values
        values.map { placeholder }
    }
}
