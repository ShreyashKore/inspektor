package com.gyanoba.inspektor.utils


@com.gyanoba.inspektor.UnstableInspektorAPI
public fun log(tag: String, message: () -> Any?): Unit =
    println("$tag ::: ${message().toString()}")

@com.gyanoba.inspektor.UnstableInspektorAPI
public fun logErr(error: Throwable?, tag: String, message: (() -> Any?)? = null): Unit =
    println("$tag ::: ❌ ${message?.invoke()?.toString().orEmpty()}\t${error?.message}")


