package remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.remote.FrameReader
import com.gyanoba.inspektor.remote.RemoteAppInfo
import com.gyanoba.inspektor.remote.RemoteServer
import com.gyanoba.inspektor.remote.StreamConnection
import com.gyanoba.inspektor.remote.openLoopbackListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableInspektorAPI::class)
class RemoteServerTest {
    private val announced = CopyOnWriteArrayList<String>()
    private val dataSource = testDataSource()
    private val server = RemoteServer(
        dataSource = { dataSource },
        overrides = { testOverrides() },
        storeId = { "store-1" },
        appInfo = { RemoteAppInfo("com.example.app", "Example", null, 1, null) },
        // Port 0: the OS picks a free one, so a test never collides with a running app.
        openListener = { openLoopbackListener(0..0) },
        announce = { announced += it },
        retryDelay = 50.milliseconds,
    )

    @AfterTest
    fun tearDown() {
        server.stop()
    }

    private suspend fun awaitAddress(): String = withTimeout(5.seconds) {
        while (server.address == null) delay(10)
        server.address!!
    }

    private fun connect(address: String): StreamConnection {
        val socket = Socket(InetAddress.getLoopbackAddress(), address.substringAfter(':').toInt())
        return StreamConnection(socket, socket.getInputStream(), socket.getOutputStream())
    }

    @Test
    fun `listens on loopback, announces where, and greets a client`() = runBlocking<Unit> {
        server.start()
        val address = awaitAddress()

        val hello = withContext(Dispatchers.IO) { FrameReader(connect(address)).next()!! }

        assertTrue(address.startsWith("127.0.0.1:"))
        assertEquals(listOf("Inspektor: remote inspection listening on $address"), announced)
        assertEquals("hello", hello["type"].text)
        assertEquals("com.example.app", hello.obj("app")["id"].text)
    }

    @Test
    fun `stopping closes every client connection`() = runBlocking<Unit> {
        server.start()
        val connection = connect(awaitAddress())
        val reader = FrameReader(connection)
        withContext(Dispatchers.IO) { reader.next() }

        server.stop()

        val next = withContext(Dispatchers.IO) {
            withTimeout(5.seconds) { runCatching { reader.next() }.getOrNull() }
        }
        assertNull(next)
    }

    @Test
    fun `a store that cannot open is announced, not thrown`() = runBlocking<Unit> {
        val failing = RemoteServer(
            dataSource = { error("setApplicationId() was not called") },
            overrides = { testOverrides() },
            storeId = { "store-1" },
            appInfo = { RemoteAppInfo("com.example.app", null, null, null, null) },
            openListener = { openLoopbackListener(0..0) },
            announce = { announced += it },
        )
        failing.start()
        withTimeout(5.seconds) { while (announced.isEmpty()) delay(10) }

        assertEquals(
            listOf("Inspektor: remote inspection could not start: setApplicationId() was not called"),
            announced,
        )
        assertNull(failing.address)
        failing.stop()
    }

    @Test
    fun `a taken port is skipped, and no free port is an error`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { taken ->
            assertFailsWith<IOException> {
                openLoopbackListener(taken.localPort..taken.localPort)
            }
        }
    }
}
