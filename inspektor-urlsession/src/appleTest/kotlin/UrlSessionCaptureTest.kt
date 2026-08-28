import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.platform.NotificationManager
import com.gyanoba.inspektor.urlsession.InspektorUrlSessionConfig
import com.gyanoba.inspektor.urlsession.InspektorUrlSessionDelegate
import com.gyanoba.inspektor.urlsession.inspektorUrlSessionDelegate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import utils.DbTestBase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Signals task completion, and doubles as the "forward to the app's own delegate" case. */
private class CompletionSignal : NSObject(), NSURLSessionDataDelegateProtocol {
    val done: CompletableDeferred<NSError?> = CompletableDeferred()

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        done.complete(didCompleteWithError)
    }
}

/**
 * Drives the proxy delegate through a real `NSURLSession`, with [StubUrlProtocol] standing in for
 * the network so nothing here depends on connectivity.
 *
 * `runBlocking`, not `runTest`: URLSession callbacks arrive on a real background queue, and
 * `runTest`'s virtual clock would fire every `withTimeout` before any of them landed.
 */
class UrlSessionCaptureTest : DbTestBase() {

    @BeforeTest
    fun resetStub() {
        db.httpTransactionQueries.deleteAll()
        stubResponder.value = { Stub(code = 200, body = """{"ok":true}""") }
    }

    @AfterTest
    fun clearStub() {
        stubResponder.value = null
    }

    private fun delegate(
        signal: CompletionSignal,
        configure: InspektorUrlSessionConfig.() -> Unit = {},
    ): InspektorUrlSessionDelegate = inspektorUrlSessionDelegate(forwardTo = signal) {
        level = LogLevel.BODY
        dataSource = InspektorDataSourceImpl(db)
        overrideRepository = OverrideRepositoryImpl(store)
        notificationManager = object : NotificationManager {
            override fun notify(title: String, message: String) = Unit
        }
        configure()
    }

    private fun session(delegate: InspektorUrlSessionDelegate): NSURLSession {
        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.protocolClasses = listOf(StubUrlProtocol)
        return NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = delegate,
            delegateQueue = null,
        )
    }

    private fun request(url: String, body: String? = null): NSMutableURLRequest {
        val request = NSMutableURLRequest(uRL = NSURL(string = url))
        request.setValue("Bearer super-secret", forHTTPHeaderField = "Authorization")
        if (body != null) {
            request.setHTTPMethod("POST")
            request.setHTTPBody(body.toNSData())
        }
        return request
    }

    /**
     * Runs one call and waits for it to finish.
     *
     * The task is created *without* a completion handler on purpose: URLSession does not call the
     * data-delivery delegate methods for completion-handler tasks, which is the delegate approach's
     * one real limitation and is asserted separately below.
     */
    private fun call(
        url: String,
        body: String? = null,
        configure: InspektorUrlSessionConfig.() -> Unit = {},
    ): InspektorUrlSessionDelegate = runBlocking {
        val signal = CompletionSignal()
        val recording = delegate(signal, configure)
        val task = session(recording).dataTaskWithRequest(request(url, body))
        task.resume()
        withTimeout(30.seconds) { signal.done.await() }
        // The recorder writes off the delegate queue; wait for the row rather than sleeping.
        recording.lastRecorder?.joinResponseLogged()
        recording
    }

    private fun transaction(): HttpTransaction? =
        db.httpTransactionQueries.getAll().executeAsList().lastOrNull()

    @Test
    fun `a call is recorded end to end`() {
        call("https://example.com/probe", body = """{"hello":"world"}""")

        val transaction = assertNotNull(transaction())
        assertEquals("POST", transaction.method)
        assertEquals("example.com", transaction.host)
        assertEquals("/probe", transaction.path)
        assertEquals("https", transaction.scheme)
        assertEquals("""{"hello":"world"}""", transaction.requestBody)
        assertEquals(200L, transaction.responseCode)
        assertEquals("""{"ok":true}""", transaction.responseBody)
        assertEquals("application/json", transaction.responseContentType)
        assertNotNull(transaction.tookMs)
        assertNotNull(transaction.requestHeadersSize)
    }

    @Test
    fun `the app's own delegate still receives its callbacks`() {
        val signal = CompletionSignal()
        runBlocking {
            val task = session(delegate(signal))
                .dataTaskWithRequest(request("https://example.com/forward"))
            task.resume()
            assertNull(withTimeout(30.seconds) { signal.done.await() })
        }
    }

    @Test
    fun `sanitized headers never reach the store`() {
        call("https://example.com/secret") {
            sanitizeHeader { it.equals("Authorization", ignoreCase = true) }
        }

        val headers = assertNotNull(transaction()).requestHeaders.orEmpty()
        val authorization = headers.firstOrNull { it.key.equals("Authorization", true) }
        assertNotNull(authorization, "the Authorization header should still be recorded")
        assertEquals(listOf("***"), authorization.value)
    }

    @Test
    fun `a filtered-out call is not recorded`() {
        val recording = call("https://example.com/drop") { filter { it.path.startsWith("/keep") } }

        assertNull(recording.lastRecorder, "a filtered call must not allocate a recorder")
        assertTrue(db.httpTransactionQueries.getAll().executeAsList().isEmpty())
    }

    @Test
    fun `a response body larger than maxContentLength is truncated`() {
        stubResponder.value = { Stub(code = 200, body = "y".repeat(5_000)) }
        call("https://example.com/big") { maxContentLength = 100 }

        assertEquals("y".repeat(100), assertNotNull(transaction()).responseBody)
    }

    @Test
    fun `an error status is recorded like any other response`() {
        stubResponder.value = { Stub(code = 503, body = "unavailable") }
        call("https://example.com/down")

        val transaction = assertNotNull(transaction())
        assertEquals(503L, transaction.responseCode)
        assertEquals("unavailable", transaction.responseBody)
    }

    /**
     * Pins down what a completion-handler task records. URLSession skips the data-delivery
     * delegate methods for these, so the response body cannot be captured -- but the request and
     * the status line still are, via `didCompleteWithError` and `task.response`.
     */
    /**
     * Pins down the delegate's one real limitation, so a future change cannot quietly claim
     * otherwise: URLSession calls *no* delegate method for a task created with a completion
     * handler -- not even `didCompleteWithError` -- so such calls are invisible to this path.
     *
     * `InspektorUrlProtocol` does capture them, which is what it is for.
     */
    @Test
    fun `a completion-handler task is invisible to the delegate`() {
        val signal = CompletionSignal()
        val recording = delegate(signal)
        runBlocking {
            val handled = CompletableDeferred<Unit>()
            val task = session(recording)
                .dataTaskWithRequest(request("https://example.com/handler")) { _, _, _ ->
                    handled.complete(Unit)
                }
            task.resume()
            withTimeout(30.seconds) { handled.await() }
            // Give any late delegate callback a chance before concluding there was none.
            platform.posix.usleep(300_000u)
        }

        assertTrue(!signal.done.isCompleted, "URLSession skips the delegate for these tasks")
        assertNull(recording.lastRecorder)
        assertTrue(db.httpTransactionQueries.getAll().executeAsList().isEmpty())
    }
}
