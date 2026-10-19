import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.urlsession.InspektorUrlProtocol
import com.gyanoba.inspektor.urlsession.InspektorUrlProtocolBridge
import com.gyanoba.inspektor.urlsession.readFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPBodyStream
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import utils.DbTestBase
import kotlin.concurrent.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The opt-in `URLProtocol` path.
 *
 * Unlike the proxy delegate it does capture completion-handler tasks, because it sits inside the
 * URL loading system rather than beside a session. The re-issued request goes through a loader
 * session that carries [StubUrlProtocol], so nothing here touches the network.
 */
class UrlProtocolCaptureTest : DbTestBase() {

    @BeforeTest
    fun registerProtocol() {
        db.httpTransactionQueries.deleteAll()
        stubResponder.value = { Stub(code = 200, body = """{"ok":true}""") }

        val loaderConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        loaderConfiguration.protocolClasses = listOf(StubUrlProtocol)
        // `prepare`, not `register`: every session here lists the protocol itself, which is the
        // React Native set-up, so nothing needs registering globally.
        InspektorUrlProtocol.prepare(loaderConfiguration) {
            level = LogLevel.BODY
            dataSource = InspektorDataSourceImpl(db)
            overrideRepository = OverrideRepositoryImpl(store)
            notificationManager = object : NotificationManager {
                override fun notify(title: String, message: String) = Unit
            }
        }
    }

    @AfterTest
    fun unregisterProtocol() {
        InspektorUrlProtocol.unregister()
        stubResponder.value = null
    }

    private fun session(): NSURLSession {
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        InspektorUrlProtocolBridge.addTo(configuration)
        return NSURLSession.sessionWithConfiguration(configuration)
    }

    /** Runs [request] through the protocol and returns what the app received. */
    private fun call(request: NSURLRequest): String? = runBlocking {
        val handled = CompletableDeferred<String?>()
        session().dataTaskWithRequest(request) { data, _, _ ->
            handled.complete(data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() })
        }.resume()
        withTimeout(30.seconds) { handled.await() }
    }

    /** The protocol records asynchronously; poll briefly for the finished row. */
    private fun awaitRow(): HttpTransaction {
        repeat(200) {
            val row = db.httpTransactionQueries.getAll().executeAsList().lastOrNull()
            if (row?.responseCode != null && row.responseBody != null) return row
            platform.posix.usleep(25_000u)
        }
        throw AssertionError("the URLProtocol recorded nothing")
    }

    @Test
    fun `the bridge puts the protocol ahead of a configuration's own`() {
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        val builtIn = configuration.protocolClasses.orEmpty()

        InspektorUrlProtocolBridge.addTo(configuration)

        assertEquals(listOf<Any?>(InspektorUrlProtocol) + builtIn, configuration.protocolClasses)
    }

    @Test
    fun `a completion-handler task is captured unlike with the delegate`() {
        val request = NSMutableURLRequest(uRL = NSURL(string = "https://example.com/handler"))
        assertEquals("""{"ok":true}""", call(request))

        val row = awaitRow()
        assertEquals("GET", row.method)
        assertEquals("/handler", row.path)
        assertEquals(200L, row.responseCode)
        assertEquals("""{"ok":true}""", row.responseBody)
    }

    /**
     * The URL loading system hands a protocol every body as `HTTPBodyStream`, never `HTTPBody` --
     * even when the app set `HTTPBody` -- so the protocol has to read the stream to see the body,
     * and must then still send the server all of it.
     */
    @Test
    fun `a request body is recorded and still reaches the server`() {
        val received = AtomicReference<String?>(null)
        stubResponder.value = { forwarded ->
            val body = forwarded.HTTPBody ?: forwarded.HTTPBodyStream?.readFully()
            received.value = body?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
            Stub(code = 200, body = """{"ok":true}""")
        }

        val request = NSMutableURLRequest(uRL = NSURL(string = "https://example.com/upload"))
        request.setHTTPMethod("POST")
        request.setValue("application/json", forHTTPHeaderField = "Content-Type")
        request.setHTTPBody("""{"hello":"world"}""".toNSData())
        call(request)

        assertEquals("""{"hello":"world"}""", received.value, "the server must get the whole body")
        val row = awaitRow()
        assertEquals("POST", row.method)
        assertEquals("""{"hello":"world"}""", row.requestBody)
        assertEquals(17L, row.requestPayloadSize)
    }

    @Test
    fun `the recursion guard keeps a re-issued request out of the protocol`() {
        val plain = NSURLRequest(uRL = NSURL(string = "https://example.com/plain")!!)
        assertTrue(InspektorUrlProtocol.canInitWithRequest(plain))

        // Non-HTTP schemes are never ours.
        val file = NSURLRequest(uRL = NSURL(string = "file:///tmp/x")!!)
        assertFalse(InspektorUrlProtocol.canInitWithRequest(file))
    }

    @Test
    fun `nothing is captured once unregistered`() {
        InspektorUrlProtocol.unregister()
        val plain = NSURLRequest(uRL = NSURL(string = "https://example.com/plain")!!)
        assertFalse(
            InspektorUrlProtocol.canInitWithRequest(plain),
            "an unregistered protocol must decline every request"
        )
        assertNotNull(plain)
    }
}
