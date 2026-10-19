package com.gyanoba.inspektor

/** How much of each call is captured. */
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
