@file:OptIn(ExperimentalForeignApi::class)

package com.gyanoba.inspektor.remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.platform.getAppName
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.ECONNABORTED
import platform.posix.EINTR
import platform.posix.POLLIN
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_REUSEADDR
import platform.posix.bind
import platform.posix.errno
import platform.posix.getpid
import platform.posix.listen
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.setsockopt
import platform.posix.shutdown
import platform.posix.sockaddr_in
import platform.posix.socket

/** 127.0.0.1. */
private const val LOOPBACK: UInt = 0x7F000001u

/**
 * A loopback TCP port from [REMOTE_FIRST_PORT]. Eagly reaches it over usbmux, which connects to the
 * device's own loopback; nothing off the device can.
 */
internal actual fun openRemoteListener(): RemoteListener {
    val last = REMOTE_FIRST_PORT + REMOTE_PORT_COUNT - 1
    for (port in REMOTE_FIRST_PORT..last) {
        val fd = bindLoopback(port)
        if (fd >= 0) return PosixListener(fd, port)
    }
    throw IllegalStateException("Every port from $REMOTE_FIRST_PORT to $last is taken.")
}

/** A listening socket on 127.0.0.1:[port], or -1 when the port is taken. */
private fun bindLoopback(port: Int): Int = memScoped {
    val fd = socket(AF_INET, SOCK_STREAM, 0)
    if (fd < 0) return -1
    val one = alloc<IntVar>()
    one.value = 1
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())

    val address = alloc<sockaddr_in>()
    memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
    address.sin_len = sizeOf<sockaddr_in>().convert()
    address.sin_family = AF_INET.convert()
    address.sin_port = networkOrder(port.toUShort())
    address.sin_addr.s_addr = networkOrder(LOOPBACK)
    val bound = bind(fd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0
    if (!bound || listen(fd, 4) != 0) {
        platform.posix.close(fd)
        return -1
    }
    fd
}

// Every iOS device and simulator is little-endian, so network (big-endian) order is a byte swap.
private fun networkOrder(value: UShort): UShort =
    (((value.toInt() and 0xFF) shl 8) or ((value.toInt() shr 8) and 0xFF)).toUShort()

private fun networkOrder(value: UInt): UInt =
    ((value and 0xFFu) shl 24) or ((value and 0xFF00u) shl 8) or
        ((value shr 8) and 0xFF00u) or ((value shr 24) and 0xFFu)

private class PosixListener(private val fd: Int, port: Int) : RemoteListener {
    private val closed = atomic(false)

    override val address: String = "127.0.0.1:$port"

    override suspend fun accept(): RemoteConnection =
        withContext(Dispatchers.IO) { awaitClient() }

    // Polled rather than blocked on, so close() takes effect within one interval: closing a
    // descriptor that another thread is blocked in accept() on does not reliably wake it.
    private fun CoroutineScope.awaitClient(): RemoteConnection {
        while (true) {
            check(!closed.value) { "The listener is closed." }
            val ready = memScoped {
                val entry = alloc<pollfd>()
                entry.fd = fd
                entry.events = POLLIN.convert()
                poll(entry.ptr, 1u, 500)
            }
            if (ready > 0) {
                val client = platform.posix.accept(fd, null, null)
                if (client >= 0) return PosixConnection(client)
                val error = errno
                check(error == EINTR || error == EAGAIN || error == ECONNABORTED) {
                    "accept() failed with errno $error."
                }
            } else if (ready < 0) {
                val error = errno
                check(error == EINTR) { "poll() failed with errno $error." }
            }
            ensureActive()
        }
    }

    override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) platform.posix.close(fd)
    }
}

internal class PosixConnection(private val fd: Int) : RemoteConnection {
    private val closed = atomic(false)

    init {
        // Writing to a socket the peer closed raises SIGPIPE, which kills the app by default.
        memScoped {
            val one = alloc<IntVar>()
            one.value = 1
            setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<IntVar>().convert())
        }
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withContext(Dispatchers.IO) { readBlocking(buffer, offset, length) }

    override suspend fun write(bytes: ByteArray) =
        withContext(Dispatchers.IO) { writeBlocking(bytes) }

    private fun readBlocking(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (true) {
            val count = buffer.usePinned {
                platform.posix.read(fd, it.addressOf(offset), length.convert())
            }
            if (count > 0) return count.toInt()
            if (count < 0 && errno == EINTR) continue
            // 0 is the peer closing; anything else is a reset or our own close().
            return -1
        }
    }

    private fun writeBlocking(bytes: ByteArray) {
        var written = 0
        while (written < bytes.size) {
            val count = bytes.usePinned {
                platform.posix.write(fd, it.addressOf(written), (bytes.size - written).convert())
            }
            if (count < 0) {
                val error = errno
                if (error == EINTR) continue
                throw IllegalStateException("write() failed with errno $error.")
            }
            written += count.toInt()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(expect = false, update = true)) return
        // shutdown() wakes a thread blocked in read(); close() alone does not.
        shutdown(fd, SHUT_RDWR)
        platform.posix.close(fd)
    }
}

@OptIn(UnstableInspektorAPI::class)
internal actual fun remoteAppInfo(): RemoteAppInfo {
    val bundle = NSBundle.mainBundle
    val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    val build = bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String
    return RemoteAppInfo(
        id = bundle.bundleIdentifier ?: "ios-app",
        name = getAppName(),
        version = listOfNotNull(version, build?.let { "($it)" }).joinToString(" ").ifEmpty { null },
        pid = getpid().toLong(),
        process = NSProcessInfo.processInfo.processName,
    )
}

internal actual fun logToDevice(message: String) {
    // stdout does not reach the unified log on a device; NSLog does, as a public message.
    // The message is the format, escaped, never an argument: Kotlin/Native passes a String vararg
    // as a C string, so `NSLog("%@", message)` hands `%@` a char pointer and crashes the app.
    NSLog(message.replace("%", "%%"))
}
