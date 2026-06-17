package com.gyanoba.inspektor.utils


internal fun log(tag: String, message: () -> Any?) =
    println("$tag ::: ${message().toString()}")

internal fun logErr(error: Throwable?, tag: String, message: (() -> Any?)? = null) =
    println("$tag ::: ❌ ${message?.invoke()?.toString().orEmpty()}\t${error?.message}")


