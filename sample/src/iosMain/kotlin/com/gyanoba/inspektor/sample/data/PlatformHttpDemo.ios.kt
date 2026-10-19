package com.gyanoba.inspektor.sample.data

import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.urlsession.inspektorUrlSession
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.appendData
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.NSObject

actual object PlatformHttpDemo {
    actual val clientName: String = "URLSession"

    actual suspend fun fetchTodo(id: Int): String {
        // A delegate-driven task, not `dataTask(with:completionHandler:)`: URLSession skips every
        // delegate callback for completion-handler tasks, so Inspektor would never see them.
        val collector = BodyCollector()
        val session: NSURLSession = inspektorUrlSession(forwardTo = collector) {
            level = LogLevel.BODY
            sanitizeHeader { header -> header.equals("Authorization", ignoreCase = true) }
        }

        val request = NSMutableURLRequest(
            uRL = NSURL(string = "https://jsonplaceholder.typicode.com/todos/$id")
        )
        request.setValue(
            "Bearer sample-token-that-must-not-be-stored",
            forHTTPHeaderField = "Authorization",
        )
        session.dataTaskWithRequest(request).resume()
        return collector.body.await()
    }
}

/** Accumulates the response and completes when the task finishes. */
private class BodyCollector : NSObject(), NSURLSessionDataDelegateProtocol {
    private val data = NSMutableData()
    val body: CompletableDeferred<String> = CompletableDeferred()

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData,
    ) {
        data.appendData(didReceiveData)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?,
    ) {
        if (didCompleteWithError != null) {
            body.completeExceptionally(Throwable(didCompleteWithError.localizedDescription))
        } else {
            body.complete(
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
            )
        }
        session.finishTasksAndInvalidate()
    }
}
