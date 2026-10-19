package com.gyanoba.inspektor.remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.APPLICATION_ID
import com.gyanoba.inspektor.platform.getAppName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

internal actual fun openRemoteListener(): RemoteListener =
    openLoopbackListener(REMOTE_FIRST_PORT until REMOTE_FIRST_PORT + REMOTE_PORT_COUNT)

/** Listens on the first free loopback port in [ports]; `0..0` lets the OS pick one. */
internal fun openLoopbackListener(ports: IntRange): RemoteListener {
    var lastError: IOException? = null
    for (port in ports) {
        try {
            return SocketListener(ServerSocket(port, 4, InetAddress.getLoopbackAddress()))
        } catch (e: IOException) {
            lastError = e
        }
    }
    throw IOException("Every port in $ports is taken.", lastError)
}

private class SocketListener(private val server: ServerSocket) : RemoteListener {
    override val address: String = "127.0.0.1:${server.localPort}"

    // A blocked accept() is not cancellable; close() unblocks it with a SocketException.
    override suspend fun accept(): RemoteConnection = withContext(Dispatchers.IO) {
        val socket = server.accept()
        socket.tcpNoDelay = true
        StreamConnection(socket, socket.getInputStream(), socket.getOutputStream())
    }

    override fun close() {
        server.close()
    }
}

/** A [RemoteConnection] over a pair of blocking streams, read and written on [Dispatchers.IO]. */
internal class StreamConnection(
    private val socket: Socket,
    private val input: InputStream,
    private val output: OutputStream,
) : RemoteConnection {
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withContext(Dispatchers.IO) { input.read(buffer, offset, length) }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        output.write(bytes)
        output.flush()
    }

    override fun close() {
        socket.close()
    }
}

@OptIn(UnstableInspektorAPI::class)
internal actual fun remoteAppInfo(): RemoteAppInfo {
    // "12345@hostname" on every JVM that matters.
    val runtimeName = ManagementFactory.getRuntimeMXBean().name
    return RemoteAppInfo(
        id = APPLICATION_ID ?: "desktop",
        name = runCatching { getAppName() }.getOrNull(),
        version = null,
        pid = runtimeName.substringBefore('@').toLongOrNull(),
        process = System.getProperty("sun.java.command")?.substringBefore(' '),
    )
}

internal actual fun logToDevice(message: String) {
    println(message)
}
