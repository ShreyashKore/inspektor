import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.urlsession.InspektorUrlProtocol
import com.gyanoba.inspektor.urlsession.inspektorUrlSession
import com.gyanoba.inspektor.urlsession.inspektorUrlSessionDelegate
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import platform.Foundation.NSCachedURLResponse
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLCacheStoragePolicy
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLProtocolClientProtocol
import platform.Foundation.NSURLProtocolMeta
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.dataTaskWithURL
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSEC_PER_SEC
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import platform.darwin.sel_registerName
import kotlin.concurrent.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

/** Answers every request with an empty 200, so nothing here reaches the network. */
private class EmptyOkProtocol : NSURLProtocol {

    @OverrideInit
    constructor(
        request: NSURLRequest,
        cachedResponse: NSCachedURLResponse?,
        client: NSURLProtocolClientProtocol?,
    ) : super(request, cachedResponse, client)

    override fun startLoading() {
        val response = NSHTTPURLResponse(
            uRL = request.URL!!,
            statusCode = 200,
            HTTPVersion = "HTTP/1.1",
            headerFields = null,
        )
        client?.URLProtocol(
            this,
            didReceiveResponse = response,
            cacheStoragePolicy = NSURLCacheStoragePolicy.NSURLCacheStorageNotAllowed,
        )
        client?.URLProtocolDidFinishLoading(this)
    }

    override fun stopLoading() = Unit

    companion object : NSURLProtocolMeta() {
        override fun canInitWithRequest(request: NSURLRequest): Boolean = true

        override fun canonicalRequestForRequest(request: NSURLRequest): NSURLRequest = request
    }
}

/** The app's own delegate, implementing two of the data-delegate methods. */
private class AppDelegate : NSObject(), NSURLSessionDataDelegateProtocol {
    val responses = AtomicInt(0)
    val completed = dispatch_semaphore_create(0)

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        responses.incrementAndGet()
        completionHandler(NSURLSessionResponseAllow)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        dispatch_semaphore_signal(completed)
    }
}

/**
 * These tests are the contract of the no-op artifact: nothing here may observe a request. If any of
 * them starts failing, the artifact is no longer a safe production replacement for
 * `inspektor-urlsession`.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class NoOpUrlSessionTest {

    @Test
    fun `with nothing to forward to the delegate implements no callback at all`() {
        val delegate = inspektorUrlSessionDelegate() as NSObject

        listOf(
            "URLSession:dataTask:didReceiveResponse:completionHandler:",
            "URLSession:dataTask:didReceiveData:",
            "URLSession:task:didCompleteWithError:",
        ).forEach { selector ->
            assertFalse(
                delegate.respondsToSelector(sel_registerName(selector)),
                "the no-op delegate must not implement $selector",
            )
        }
    }

    /**
     * Swapping the real artifact for this one must not change what the app's own delegate sees.
     * Before forwarding, it got no callback at all, and every delegate-driven request hung.
     */
    @Test
    fun `the app's own delegate receives exactly the callbacks it implements`() {
        val app = AppDelegate()
        val delegate = inspektorUrlSessionDelegate(forwardTo = app)

        assertFalse(
            delegate.respondsToSelector(sel_registerName("URLSession:dataTask:didReceiveData:")),
            "a method the app does not implement must not be claimed",
        )

        val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
        configuration.protocolClasses = listOf(EmptyOkProtocol)
        val session = NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = delegate,
            delegateQueue = null,
        )
        session.dataTaskWithURL(NSURL(string = "https://example.com/forward")!!).resume()

        val timeout = dispatch_time(DISPATCH_TIME_NOW, 30 * NSEC_PER_SEC.toLong())
        assertEquals(0L, dispatch_semaphore_wait(app.completed, timeout), "didCompleteWithError")
        assertEquals(1, app.responses.value, "didReceiveResponse")
        session.finishTasksAndInvalidate()
    }

    @Test
    fun `a session built by the no-op has no delegate of ours`() {
        val session = inspektorUrlSession {
            level = LogLevel.BODY
            maxContentLength = 10
            retentionDuration = 7.days
            showNotifications = false
            filter { true }
            sanitizeHeader { true }
        }

        assertNull(session.delegate, "no delegate should be installed when none was forwarded")
        session.finishTasksAndInvalidate()
    }

    @Test
    fun `the URLProtocol never claims a request`() {
        InspektorUrlProtocol.register()
        val request = NSURLRequest(uRL = NSURL(string = "https://example.com/x")!!)

        assertFalse(InspektorUrlProtocol.canInitWithRequest(request))
        InspektorUrlProtocol.unregister()
    }
}
