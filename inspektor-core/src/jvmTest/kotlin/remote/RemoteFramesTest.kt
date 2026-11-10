package remote

import com.gyanoba.inspektor.remote.FrameReader
import com.gyanoba.inspektor.remote.MAX_FRAME_BYTES
import com.gyanoba.inspektor.remote.RemoteProtocolException
import com.gyanoba.inspektor.remote.encodeFrame
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RemoteFramesTest {
    private fun lengthPrefix(length: Long) = byteArrayOf(
        (length ushr 24).toByte(),
        (length ushr 16).toByte(),
        (length ushr 8).toByte(),
        length.toByte(),
    )

    @Test
    fun `a frame is a big-endian length and UTF-8 JSON`() {
        val frame = encodeFrame(buildJsonObject { put("name", "é") })
        val payload = """{"name":"é"}""".encodeToByteArray()
        assertEquals(payload.size.toLong(), frame.copyOfRange(0, 4).fold(0L) { acc, b -> acc shl 8 or (b.toLong() and 0xFF) })
        assertEquals(payload.toList(), frame.copyOfRange(4, frame.size).toList())
    }

    @Test
    fun `frames survive any chunking`() = runBlocking<Unit> {
        val (client, server) = connectionPair()
        val bytes = encodeFrame(buildJsonObject { put("n", 1) }) +
            encodeFrame(buildJsonObject { put("n", 2) })
        for (byte in bytes) client.writeRaw(byteArrayOf(byte))
        client.close()

        val reader = FrameReader(server)
        assertEquals("1", reader.next()!!["n"].text)
        assertEquals("2", reader.next()!!["n"].text)
        assertNull(reader.next())
    }

    @Test
    fun `a frame over the limit is refused before it is read`() = runBlocking<Unit> {
        val (client, server) = connectionPair()
        client.writeRaw(lengthPrefix(MAX_FRAME_BYTES + 1L))
        assertFailsWith<RemoteProtocolException> { FrameReader(server).next() }
    }

    // The rule that keeps a web page out: all a browser can put on a loopback socket is an HTTP
    // request, whose first four bytes read as a length over a gigabyte.
    @Test
    fun `a browser's HTTP request is refused on its first four bytes`() = runBlocking<Unit> {
        for (method in listOf("GET ", "POST", "PUT ", "OPTI")) {
            val (client, server) = connectionPair()
            client.writeRaw("$method/ HTTP/1.1\r\nHost: 127.0.0.1:38470\r\n\r\n".encodeToByteArray())
            assertFailsWith<RemoteProtocolException>(method) { FrameReader(server).next() }
        }
    }

    @Test
    fun `a payload that is not a JSON object is refused`() = runBlocking<Unit> {
        for (payload in listOf("hello", "[1,2]", "")) {
            val (client, server) = connectionPair()
            val bytes = payload.encodeToByteArray()
            client.writeRaw(lengthPrefix(bytes.size.toLong()) + bytes)
            assertFailsWith<RemoteProtocolException>(payload) { FrameReader(server).next() }
        }
    }

    @Test
    fun `a payload that is not UTF-8 is refused`() = runBlocking<Unit> {
        val (client, server) = connectionPair()
        client.writeRaw(lengthPrefix(2) + byteArrayOf(0xC3.toByte(), 0x28))
        assertFailsWith<RemoteProtocolException> { FrameReader(server).next() }
    }

    @Test
    fun `a connection that ends inside a frame is an error, between frames it is not`() =
        runBlocking<Unit> {
            val (client, server) = connectionPair()
            val frame = encodeFrame(buildJsonObject { put("n", 1) })
            client.writeRaw(frame.copyOfRange(0, frame.size - 2))
            client.close()
            assertFailsWith<RemoteProtocolException> { FrameReader(server).next() }
        }
}
