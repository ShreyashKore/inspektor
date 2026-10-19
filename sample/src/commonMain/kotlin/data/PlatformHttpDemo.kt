package com.gyanoba.inspektor.sample.data

/**
 * A call made with the platform's own HTTP client rather than with Ktor: OkHttp on Android and
 * desktop, `NSURLSession` on iOS.
 *
 * The sample exercises every integration from one place so that `:sample`'s build catches drift
 * between them -- and, because the Android `prod` flavor substitutes the no-op artifacts, it
 * catches drift between each integration and its no-op twin too.
 */
expect object PlatformHttpDemo {
    /** Human-readable name of the client behind this implementation. */
    val clientName: String

    /** Fetches a todo as raw JSON. */
    suspend fun fetchTodo(id: Int): String
}
