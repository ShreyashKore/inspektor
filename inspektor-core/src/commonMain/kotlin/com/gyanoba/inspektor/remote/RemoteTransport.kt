package com.gyanoba.inspektor.remote

/**
 * The first of the loopback TCP ports a remote-inspection server tries, where it listens on TCP
 * (iOS and desktop). It takes the first free one of [REMOTE_PORT_COUNT], so several apps can
 * listen at once; Eagly probes the same range. Android listens on an abstract socket instead.
 */
internal const val REMOTE_FIRST_PORT: Int = 38470

/** How many ports, from [REMOTE_FIRST_PORT], a server may use. */
internal const val REMOTE_PORT_COUNT: Int = 8

/** One client connection: a byte stream in each direction. */
internal interface RemoteConnection {
    /**
     * Reads up to [length] bytes into [buffer] at [offset]. Returns how many were read, or -1 at
     * the end of the stream.
     */
    suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int

    /** Writes all of [bytes]. Throws once the connection is closed. */
    suspend fun write(bytes: ByteArray)

    /** Closes both directions. Safe to call more than once, from any thread. */
    fun close()
}

/** A listening socket that hands out [RemoteConnection]s. */
internal interface RemoteListener {
    /** Where a client finds this listener: `@socket-name` or `127.0.0.1:port`. */
    val address: String

    /** Waits for the next client. Throws once [close] has been called. */
    suspend fun accept(): RemoteConnection

    /** Stops listening, waking a pending [accept]. Safe to call more than once. */
    fun close()
}

/** What the server says about the app in its `hello`. */
internal class RemoteAppInfo(
    val id: String,
    val name: String?,
    val version: String?,
    val pid: Long?,
    val process: String?,
)

/**
 * Opens this platform's listener: an abstract Unix socket named `inspektor_<pid>_kotlin` on
 * Android, a loopback TCP port from [REMOTE_FIRST_PORT] elsewhere. Throws when none is available.
 */
internal expect fun openRemoteListener(): RemoteListener

internal expect fun remoteAppInfo(): RemoteAppInfo

/**
 * Writes [message] where the host can read it: logcat on Android, the unified log on iOS (stdout
 * never reaches it there), stdout on desktop.
 */
internal expect fun logToDevice(message: String)
