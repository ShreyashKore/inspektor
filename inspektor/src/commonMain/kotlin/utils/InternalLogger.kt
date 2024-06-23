package utils

private val kermit = co.touchlab.kermit.Logger

internal fun log(message: () -> Any?) =
    kermit.d(tag = "HttpTransaction") { message().toString() }

internal fun logErr(error: Throwable?, message: () -> Any?) =
    kermit.e(error, "HttpTransaction") { message().toString() }


