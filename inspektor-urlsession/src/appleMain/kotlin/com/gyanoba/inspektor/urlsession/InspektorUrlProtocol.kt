package com.gyanoba.inspektor.urlsession

import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import platform.Foundation.NSCachedURLResponse
import platform.Foundation.NSError
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLCacheStoragePolicy
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLProtocolClientProtocol
import platform.Foundation.NSURLProtocolMeta
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.dataTaskWithRequest
import kotlin.concurrent.AtomicReference

private const val HANDLED_KEY = "com.gyanoba.inspektor.handled"

/**
 * Zero-configuration URLSession capture, at a price.
 *
 * Registering this protocol records every `http`/`https` request the URL loading system makes,
 * without the app changing how it builds sessions. Prefer [inspektorUrlSession] unless that is
 * impossible: a `URLProtocol` has hard limits that a delegate does not.
 *
 * - It must be registered **before** any request is made; earlier requests are invisible.
 * - It never sees `AVPlayer` traffic, background sessions or WebSockets, which bypass the URL
 *   loading system.
 * - Registration is global and mutable, and it changes caching semantics -- responses it loads are
 *   stored with `NSURLCacheStorageNotAllowed`.
 * - It re-issues each request through its own session and hands the response back in one piece, so
 *   a streamed download is buffered in memory rather than delivered incrementally.
 *
 * ```kotlin
 * InspektorUrlProtocol.register {
 *     level = LogLevel.BODY
 * }
 * ```
 */
public class InspektorUrlProtocol : NSURLProtocol {

    @OverrideInit
    public constructor(
        request: NSURLRequest,
        cachedResponse: NSCachedURLResponse?,
        client: NSURLProtocolClientProtocol?,
    ) : super(request, cachedResponse, client)

    private var task: NSURLSessionDataTask? = null

    override fun startLoading() {
        val capture = activeCapture.value
        val session = loaderSession.value
        val original = request()

        val forwarded = original.mutableCopy() as NSMutableURLRequest
        // Without this the re-issued request would be picked up by this same protocol again.
        NSURLProtocol.setProperty(true, HANDLED_KEY, forwarded)

        val pending = capture?.begin(original)

        task = session?.dataTaskWithRequest(forwarded) { data, response, error ->
            val client = client()
            if (error != null) {
                pending?.let { capture?.onComplete(it, error) }
                client?.URLProtocol(this, didFailWithError = error)
                return@dataTaskWithRequest
            }

            if (response != null) {
                pending?.let { capture?.onResponse(it, response) }
                client?.URLProtocol(
                    this,
                    didReceiveResponse = response,
                    cacheStoragePolicy = NSURLCacheStoragePolicy.NSURLCacheStorageNotAllowed,
                )
            }
            if (data != null) {
                pending?.appendResponseData(data, capture?.maxContentLength ?: 0)
                client?.URLProtocol(this, didLoadData = data)
            }
            pending?.let { capture?.onComplete(it, null) }
            client?.URLProtocolDidFinishLoading(this)
        }
        task?.resume()
    }

    override fun stopLoading() {
        task?.cancel()
        task = null
    }

    public companion object : NSURLProtocolMeta() {
        override fun canInitWithRequest(request: NSURLRequest): Boolean {
            if (activeCapture.value == null) return false
            // Already ours -- let the re-issued request through to the real loader.
            if (NSURLProtocol.propertyForKey(HANDLED_KEY, request) != null) return false
            val scheme = request.URL?.scheme?.lowercase()
            return scheme == "http" || scheme == "https"
        }

        override fun canonicalRequestForRequest(request: NSURLRequest): NSURLRequest = request

        /**
         * Registers the protocol globally. Call before the app makes its first request.
         *
         * Registering twice replaces the configuration rather than adding a second protocol.
         *
         * @param loaderConfiguration configuration for the session each request is re-issued
         * through. It must not list this protocol among its `protocolClasses`, or every request
         * would recurse. Defaults to an ephemeral configuration, which keeps the re-issued call out
         * of the app's cookie and cache stores.
         */
        public fun register(
            loaderConfiguration: NSURLSessionConfiguration =
                NSURLSessionConfiguration.ephemeralSessionConfiguration,
            configure: InspektorUrlSessionConfig.() -> Unit = {},
        ) {
            activeCapture.value = UrlSessionCapture(InspektorUrlSessionConfig().apply(configure))
            loaderSession.value = NSURLSession.sessionWithConfiguration(loaderConfiguration)
            NSURLProtocol.registerClass(this)
        }

        /** Unregisters the protocol. In-flight requests are unaffected. */
        public fun unregister() {
            NSURLProtocol.unregisterClass(this)
            activeCapture.value = null
            loaderSession.value = null
        }
    }
}

/**
 * Set by [InspektorUrlProtocol.register]. Null means "not registered", which is also what makes
 * `canInitWithRequest` return false after [InspektorUrlProtocol.unregister].
 *
 * Top level for the same reason as [loaderSession].
 */
private val activeCapture = AtomicReference<UrlSessionCapture?>(null)

/**
 * The session the protocol re-issues requests through, set by [InspektorUrlProtocol.register].
 *
 * Top level rather than in the companion: Kotlin/Native does not allow fields on the companion of
 * an Objective-C subclass, because the companion *is* the Objective-C class object.
 */
private val loaderSession = AtomicReference<NSURLSession?>(null)
