package com.gyanoba.inspektor.remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.utils.InspektorLog
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val TAG = "Inspektor remote"

/**
 * Accepts remote-inspection clients and runs a [RemoteSession] for each.
 *
 * The listener is reopened whenever accepting fails: iOS can reclaim a listening socket while the
 * app is suspended, and a server that gave up there would be gone until the next launch.
 */
@OptIn(UnstableInspektorAPI::class)
internal class RemoteServer(
    private val dataSource: () -> InspektorDataSourceImpl,
    private val overrides: () -> OverrideRepository,
    private val storeId: () -> String,
    private val appInfo: () -> RemoteAppInfo,
    private val openListener: () -> RemoteListener = ::openRemoteListener,
    private val announce: (String) -> Unit = ::logToDevice,
    private val retryDelay: Duration = 2.seconds,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    // The handler is the last line of defence: an exception escaping any server or session
    // coroutine would otherwise reach the thread's uncaught-exception handler, and on Android
    // that crashes the app this debug tool is embedded in.
    private val scope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error ->
            InspektorLog.error(error, TAG) { "Remote inspection failed" }
        },
    )
    private val currentListener = atomic<RemoteListener?>(null)
    private val sessionsLock = SynchronizedObject()
    private val sessions = mutableSetOf<RemoteSession>()

    /** Where clients can reach the server, once it listens. */
    val address: String? get() = currentListener.value?.address

    fun start() {
        scope.launch { serve() }
    }

    fun stop() {
        scope.cancel()
        currentListener.getAndSet(null)?.close()
        // Cancelling does not interrupt a thread blocked reading a socket; closing it does.
        synchronized(sessionsLock) { sessions.toList().also { sessions.clear() } }
            .forEach { it.close() }
    }

    private suspend fun serve() {
        val dataSource: InspektorDataSourceImpl
        val overrides: OverrideRepository
        val store: String
        val app: RemoteAppInfo
        try {
            dataSource = dataSource()
            overrides = overrides()
            store = storeId()
            app = appInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // On desktop, for one: the store needs setApplicationId() first.
            announce("Inspektor: remote inspection could not start: ${e.message}")
            return
        }
        var announcedFailure = false
        while (scope.isActive) {
            val listener = try {
                openListener()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!announcedFailure) {
                    announcedFailure = true
                    announce("Inspektor: remote inspection could not listen: ${e.message}")
                }
                delay(retryDelay)
                continue
            }
            currentListener.value = listener
            announce("Inspektor: remote inspection listening on ${listener.address}")
            try {
                while (scope.isActive) {
                    val connection = listener.accept()
                    val session = RemoteSession(
                        connection = connection,
                        dataSource = dataSource,
                        overrides = overrides,
                        hello = { helloMessage(app, store, dataSource.oldestTransactionId()) },
                        parent = scope,
                    )
                    synchronized(sessionsLock) { sessions += session }
                    session.start().invokeOnCompletion {
                        synchronized(sessionsLock) { sessions -= session }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (scope.isActive) {
                    InspektorLog.error(e, TAG) { "Stopped accepting; reopening the listener" }
                }
            } finally {
                currentListener.compareAndSet(listener, null)
                listener.close()
            }
            delay(retryDelay)
        }
    }
}
