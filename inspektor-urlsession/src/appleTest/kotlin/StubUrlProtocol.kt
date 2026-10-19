import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import platform.Foundation.NSCachedURLResponse
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSString
import platform.Foundation.NSURLCacheStoragePolicy
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLProtocolClientProtocol
import platform.Foundation.NSURLProtocolMeta
import platform.Foundation.NSURLRequest
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import kotlin.concurrent.AtomicReference

/** A canned HTTP response. */
@Suppress("UNCHECKED_CAST")
class Stub(
    val code: Int = 200,
    val headers: Map<String, String> = mapOf("Content-Type" to "application/json"),
    val body: String = "",
)

/**
 * Serves [Stub]s instead of reaching the network, so these tests are hermetic.
 *
 * Installed through `NSURLSessionConfiguration.protocolClasses` rather than registered globally, so
 * it affects only the session under test.
 */
class StubUrlProtocol : NSURLProtocol {

    @OverrideInit
    constructor(
        request: NSURLRequest,
        cachedResponse: NSCachedURLResponse?,
        client: NSURLProtocolClientProtocol?,
    ) : super(request, cachedResponse, client)

    override fun startLoading() {
        val request = request()
        val stub = stubResponder.value?.invoke(request) ?: Stub(code = 500, body = "no stub")
        val client = client()

        val response = NSHTTPURLResponse(
            uRL = request.URL!!,
            statusCode = stub.code.toLong(),
            HTTPVersion = "HTTP/1.1",
            headerFields = stub.headers as Map<Any?, *>,
        )
        client?.URLProtocol(
            this,
            didReceiveResponse = response,
            cacheStoragePolicy = NSURLCacheStoragePolicy.NSURLCacheStorageNotAllowed,
        )
        stub.body.toNSData()?.let { client?.URLProtocol(this, didLoadData = it) }
        client?.URLProtocolDidFinishLoading(this)
    }

    override fun stopLoading() = Unit

    companion object : NSURLProtocolMeta() {
        override fun canInitWithRequest(request: NSURLRequest): Boolean = true

        override fun canonicalRequestForRequest(request: NSURLRequest): NSURLRequest = request
    }
}

/**
 * What [StubUrlProtocol] should answer with.
 *
 * Top level rather than in the companion: Kotlin/Native does not allow fields on the companion of
 * an Objective-C subclass, because the companion *is* the Objective-C class object.
 */
val stubResponder: AtomicReference<((NSURLRequest) -> Stub)?> = AtomicReference(null)

fun String.toNSData(): NSData? = (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)
