package utils


internal fun log(message: () -> Any?) =
    println("HttpTransaction ${message().toString()}")

internal fun logErr(error: Throwable?, message: () -> Any?) =
    println("HttpTransaction Err ${message().toString()}")


