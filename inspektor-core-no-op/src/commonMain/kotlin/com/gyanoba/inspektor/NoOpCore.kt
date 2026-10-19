package com.gyanoba.inspektor

/**
 * No-op counterpart of `com.gyanoba.inspektor.LogLevel`.
 *
 * Kept so that consumer code configuring `level` still compiles against the no-op artifacts. The
 * value is never read.
 */
public enum class LogLevel(
    public val info: Boolean = false,
    public val headers: Boolean = false,
    public val body: Boolean = false,
) {
    NONE,
    INFO(info = true),
    HEADERS(info = true, headers = true),
    BODY(info = true, headers = true, body = true)
}

/**
 * No-op counterpart of `com.gyanoba.inspektor.InspektorRequest`.
 *
 * Kept so that consumer code writing a neutral `filter { ... }` still compiles. No instance is ever
 * constructed by this artifact.
 */
public class InspektorRequest(
    public val method: String,
    public val url: String,
    public val host: String,
    public val path: String,
    public val headers: Map<String, List<String>> = emptyMap(),
)

@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message = "This API is unstable and may be removed in the future.",
    level = RequiresOptIn.Level.ERROR,
)
public annotation class UnstableInspektorAPI
