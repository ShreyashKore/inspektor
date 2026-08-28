import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.urlsession.InspektorUrlProtocol
import com.gyanoba.inspektor.urlsession.inspektorUrlSession
import com.gyanoba.inspektor.urlsession.inspektorUrlSessionDelegate
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.darwin.NSObject
import platform.darwin.sel_registerName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

/**
 * These tests are the contract of the no-op artifact: nothing here may observe a request. If any of
 * them starts failing, the artifact is no longer a safe production replacement for
 * `inspektor-urlsession`.
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class NoOpUrlSessionTest {

    @Test
    fun `the delegate implements no callback at all`() {
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
