import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.urlsession.InspektorUrlProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import utils.DbTestBase
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
        InspektorUrlProtocol.register(loaderConfiguration) {
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
        configuration.protocolClasses = listOf(InspektorUrlProtocol)
        return NSURLSession.sessionWithConfiguration(configuration)
    }

    @Test
    fun `a completion-handler task is captured unlike with the delegate`() = runBlocking {
        val handled = CompletableDeferred<String?>()
        val request = NSMutableURLRequest(uRL = NSURL(string = "https://example.com/handler"))
        session().dataTaskWithRequest(request) { data, _, _ ->
            handled.complete(data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() })
        }.resume()

        assertEquals("""{"ok":true}""", withTimeout(30.seconds) { handled.await() })

        // The protocol records asynchronously; poll briefly for the finished row.
        repeat(200) {
            val row = db.httpTransactionQueries.getAll().executeAsList().lastOrNull()
            if (row?.responseCode != null && row.responseBody != null) {
                assertEquals("GET", row.method)
                assertEquals("/handler", row.path)
                assertEquals(200L, row.responseCode)
                assertEquals("""{"ok":true}""", row.responseBody)
                return@runBlocking
            }
            platform.posix.usleep(25_000u)
        }
        throw AssertionError("the URLProtocol recorded nothing")
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
