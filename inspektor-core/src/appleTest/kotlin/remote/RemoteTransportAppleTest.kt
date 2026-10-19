@file:OptIn(ExperimentalForeignApi::class)

package remote

import com.gyanoba.inspektor.remote.FrameReader
import com.gyanoba.inspektor.remote.PosixConnection
import com.gyanoba.inspektor.remote.REMOTE_FIRST_PORT
import com.gyanoba.inspektor.remote.REMOTE_PORT_COUNT
import com.gyanoba.inspektor.remote.encodeFrame
import com.gyanoba.inspektor.remote.logToDevice
import com.gyanoba.inspektor.remote.openRemoteListener
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.connect
import platform.posix.memset
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Runs the POSIX listener for real, on the simulator: bind, accept, both directions, close. */
class RemoteTransportAppleTest {
    private fun connectLoopback(port: Int): PosixConnection = memScoped {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        val address = alloc<sockaddr_in>()
        memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
        address.sin_len = sizeOf<sockaddr_in>().convert()
        address.sin_family = AF_INET.convert()
        address.sin_port = (((port and 0xFF) shl 8) or ((port shr 8) and 0xFF)).toUShort()
        address.sin_addr.s_addr = 0x0100007Fu // 127.0.0.1, network order on a little-endian host
        val result = connect(fd, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
        check(result == 0) { "connect() failed" }
        PosixConnection(fd)
    }

    @Test
    fun listenerExchangesFramesBothWaysAndSeesTheClientLeave() = runBlocking {
        withTimeout(10.seconds) {
            val listener = openRemoteListener()
            val port = listener.address.substringAfter(':').toInt()
            assertTrue(port in REMOTE_FIRST_PORT until REMOTE_FIRST_PORT + REMOTE_PORT_COUNT)

            val accepted = async(Dispatchers.IO) { listener.accept() }
            val client = connectLoopback(port)
            val server = accepted.await()

            client.write(encodeFrame(buildJsonObject { put("from", "client") }))
            assertEquals(JsonPrimitive("client"), FrameReader(server).next()!!["from"])

            server.write(encodeFrame(buildJsonObject { put("from", "server") }))
            assertEquals(JsonPrimitive("server"), FrameReader(client).next()!!["from"])

            client.close()
            assertNull(FrameReader(server).next())

            server.close()
            listener.close()
        }
    }

    @Test
    fun aSecondListenerTakesTheNextPort() = runBlocking {
        val first = openRemoteListener()
        val second = openRemoteListener()
        try {
            assertTrue(first.address != second.address)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `the device log line is written as text whatever it contains`() {
        // A Kotlin String passed through a C vararg arrives as a C string, so a `%@` in NSLog's
        // format would be handed a char pointer and crash the app.
        logToDevice("Inspektor remote inspection listening on 127.0.0.1:38470")
        logToDevice("100% of %@ and %s stay text")
    }
}
