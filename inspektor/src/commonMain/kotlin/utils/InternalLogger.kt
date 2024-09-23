package utils


internal fun log(tag: String, message: () -> Any?) =
    println("$tag ::: ${message().toString()}")

internal fun logErr(error: Throwable?, tag: String, message: (() -> Any?)? = null) =
    println("$tag ::: ❌ ${message?.toString().orEmpty()}\t${error?.message}")


