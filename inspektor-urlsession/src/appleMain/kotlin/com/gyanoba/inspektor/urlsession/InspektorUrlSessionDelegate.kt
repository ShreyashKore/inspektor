package com.gyanoba.inspektor.urlsession

import com.gyanoba.inspektor.TransactionRecorder
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import platform.darwin.sel_registerName

/**
 * Records every call made through an `NSURLSession` that is constructed with this delegate.
 *
 * This is the recommended way to capture URLSession traffic. It sees exactly what the session
 * sees -- no global registration, no effect on caching, and it works for any library layered on
 * URLSession (Alamofire, Moya, Get). The cost is a setup step: the session has to be built with
 * it.
 *
 * ```kotlin
 * val session = inspektorUrlSession {
 *     level = LogLevel.BODY
 *     sanitizeHeader { it.equals("Authorization", ignoreCase = true) }
 * }
 * ```
 *
 * If the app already has its own delegate, pass it as [forwardTo] and it receives every callback it
 * implements: the three data callbacks this records are forwarded after recording, and the rest --
 * auth challenges, redirects, upload progress, metrics -- go to it untouched.
 *
 * ### Limits
 *
 * - **Completion-handler tasks are invisible.** URLSession calls no delegate method at all for a
 *   task created with `dataTask(with:completionHandler:)` -- not even `didCompleteWithError` -- so
 *   those calls cannot be recorded through a delegate. Use a delegate-driven task, or
 *   [InspektorUrlProtocol], which sits inside the URL loading system and does see them.
 * - An upload backed by `HTTPBodyStream` cannot be read without consuming it, so those request
 *   bodies are skipped rather than corrupting the consumer's request.
 * - URLSession does not expose the negotiated HTTP version, so `protocol` stays empty.
 */
public class InspektorUrlSessionDelegate internal constructor(
    private val capture: UrlSessionCapture,
    private val forwardTo: NSURLSessionDataDelegateProtocol?,
) : NSObject(), NSURLSessionDataDelegateProtocol {

    private val inFlight = CaptureRegistry()

    /**
     * The recorder for the most recently completed task.
     *
     * A test hook: the recorder writes asynchronously, and `joinResponseLogged()` on this is how
     * the module's tests wait for the row instead of sleeping and hoping.
     */
    internal var lastRecorder: TransactionRecorder? = null
        private set

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit,
    ) {
        record(dataTask)?.let { capture.onResponse(it, didReceiveResponse) }

        val forwarded = forwardTo
        if (forwarded != null && forwarded.responds(DID_RECEIVE_RESPONSE)) {
            forwarded.URLSession(session, dataTask, didReceiveResponse, completionHandler)
        } else {
            completionHandler(NSURLSessionResponseAllow)
        }
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        record(dataTask)?.appendResponseData(didReceiveData, capture.maxContentLength)
        forwardTo?.takeIf { it.responds(DID_RECEIVE_DATA) }
            ?.URLSession(session, dataTask, didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        // `inFlight` is empty when no data callback ever arrived: either the call failed before a
        // response (a DNS error), or -- much more commonly -- the task was created with a
        // completion handler, for which URLSession skips the data-delivery delegate methods
        // entirely. Start the recording here in that case, so those calls are still captured;
        // only their response body is missing, since nothing ever handed us the bytes.
        val pending = inFlight.remove(task)
            ?: task.originalRequest()?.let { capture.begin(it) }

        if (pending != null) {
            lastRecorder = pending.recorder
            if (didCompleteWithError == null) {
                (task.response as? NSHTTPURLResponse)?.let { capture.onResponse(pending, it) }
            }
            capture.onComplete(pending, didCompleteWithError)
        }
        forwardTo?.takeIf { it.responds(DID_COMPLETE) }
            ?.URLSession(session, task, didCompleteWithError)
    }

    // ---- forwarding ------------------------------------------------------------------------
    //
    // Only the three callbacks above carry anything worth recording, so only they are implemented
    // here. Every other delegate method goes straight to [forwardTo] through Objective-C message
    // forwarding: `respondsToSelector:` reports whatever [forwardTo] implements, and
    // `forwardingTargetForSelector:` hands the message to it.
    //
    // Implementing those methods here and forwarding by hand would not be equivalent: a method
    // that exists changes URLSession's behaviour even when all it does is defer. URLSession sends a
    // server-trust challenge to the session-level handler only when the task-level one is
    // missing, for instance, so a forwarding task-level handler would route around an app's
    // certificate pinning.

    override fun respondsToSelector(aSelector: COpaquePointer?): Boolean =
        super.respondsToSelector(aSelector) || forwardTo?.respondsToSelector(aSelector) == true

    override fun forwardingTargetForSelector(aSelector: COpaquePointer?): Any? {
        val forwarded = forwardTo
        return if (forwarded != null && forwarded.respondsToSelector(aSelector)) {
            forwarded
        } else {
            super.forwardingTargetForSelector(aSelector)
        }
    }

    /** Starts recording this task the first time it is seen, and returns the in-flight capture. */
    private fun record(task: NSURLSessionTask): UrlSessionCapture.Capture? {
        inFlight.get(task)?.let { return it }
        val request = task.originalRequest() ?: return null
        val started = capture.begin(request) ?: return null
        inFlight.put(task, started)
        return started
    }
}

private const val DID_RECEIVE_RESPONSE = "URLSession:dataTask:didReceiveResponse:completionHandler:"
private const val DID_RECEIVE_DATA = "URLSession:dataTask:didReceiveData:"
private const val DID_COMPLETE = "URLSession:task:didCompleteWithError:"

/**
 * Every method of `NSURLSessionDataDelegate` is optional in Objective-C, so a real delegate
 * usually implements only some of them. Kotlin sees them all as interface members, and calling one
 * the delegate does not actually implement is an `unrecognized selector` crash -- so ask first.
 */
@OptIn(ExperimentalForeignApi::class)
private fun Any.responds(selector: String): Boolean =
    (this as? NSObject)?.respondsToSelector(sel_registerName(selector)) == true

/**
 * Builds an `NSURLSession` that records through Inspektor.
 *
 * @param configuration the session configuration; defaults to `NSURLSessionConfiguration.default`.
 * @param delegateQueue the queue delegate callbacks arrive on; null means a serial background queue.
 * @param forwardTo an existing delegate; it receives every callback it implements.
 */
public fun inspektorUrlSession(
    configuration: NSURLSessionConfiguration = NSURLSessionConfiguration.defaultSessionConfiguration,
    delegateQueue: NSOperationQueue? = null,
    forwardTo: NSURLSessionDataDelegateProtocol? = null,
    configure: InspektorUrlSessionConfig.() -> Unit = {},
): NSURLSession = NSURLSession.sessionWithConfiguration(
    configuration = configuration,
    delegate = inspektorUrlSessionDelegate(forwardTo, configure),
    delegateQueue = delegateQueue,
)

/**
 * The recording delegate on its own, for callers that build their `NSURLSession` themselves.
 *
 * Note that URLSession keeps a strong reference to its delegate until the session is invalidated,
 * so there is nothing to retain here.
 */
public fun inspektorUrlSessionDelegate(
    forwardTo: NSURLSessionDataDelegateProtocol? = null,
    configure: InspektorUrlSessionConfig.() -> Unit = {},
): InspektorUrlSessionDelegate = InspektorUrlSessionDelegate(
    capture = UrlSessionCapture(InspektorUrlSessionConfig().apply(configure)),
    forwardTo = forwardTo,
)
