package com.gyanoba.inspektor.utils

import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * Inspektor's own diagnostics.
 *
 * Scoped in an object rather than exposed as top-level `log` / `logErr` functions: those names are
 * far too generic to put in a consumer's namespace, and this is only reachable across modules
 * because Inspektor's own UI and integrations are separate artifacts.
 */
@UnstableInspektorAPI
public object InspektorLog {
    public fun info(tag: String, message: () -> Any?): Unit =
        println("$tag ::: ${message().toString()}")

    public fun error(error: Throwable?, tag: String, message: (() -> Any?)? = null): Unit =
        println("$tag ::: ❌ ${message?.invoke()?.toString().orEmpty()}\t${error?.message}")
}
