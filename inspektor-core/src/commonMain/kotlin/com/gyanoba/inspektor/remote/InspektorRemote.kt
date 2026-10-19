package com.gyanoba.inspektor.remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.platform.getAppDataDir
import com.gyanoba.inspektor.utils.InspektorLog
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Remote inspection: lets [Eagly](https://github.com/ShreyashKore/eagly), a desktop device viewer,
 * show this app's recorded traffic live and edit its overrides, over a socket local to the device.
 * The protocol is in docs/REMOTE_PROTOCOL.md.
 *
 * **Off until [start] is called.** There is no authentication: while it runs, anything on the
 * device that reaches the socket -- Eagly through adb or usbmux, but also another app -- can read
 * every recorded request and change responses through overrides. That is the trade React Native's
 * Metro makes, and it is bounded the same way:
 *
 * - call [start] only in builds that never reach real users (debug, UAT, dev flavours), and ship
 *   the no-op artifacts in production, which contain no server at all;
 * - the socket is local to the device: an abstract Unix socket on Android, a `127.0.0.1` port on
 *   iOS and desktop. Nothing on the network can reach it, and it does not speak HTTP, so a web page
 *   cannot drive it either.
 *
 * When the server starts listening it writes one line to the device log naming its socket or port.
 */
@UnstableInspektorAPI
public object InspektorRemote {
    private val lock = SynchronizedObject()
    private var server: RemoteServer? = null

    /** Whether [start] has been called and [stop] has not. */
    public val isRunning: Boolean get() = synchronized(lock) { server != null }

    /**
     * Starts the remote-inspection server, in the background. Safe to call more than once and
     * from any thread; it returns at once.
     */
    public fun start() {
        synchronized(lock) {
            if (server != null) return
            // Providers, not instances: opening the database and the override store reads files,
            // which belongs on the server's background thread, not the caller's.
            server = RemoteServer(
                dataSource = { InspektorDataSourceImpl.Instance },
                overrides = { OverrideRepositoryImpl.Instance },
                storeId = ::loadOrCreateStoreId,
                appInfo = ::remoteAppInfo,
            ).also { it.start() }
        }
    }

    /** Stops the server and closes every client connection. */
    public fun stop() {
        synchronized(lock) {
            server?.stop()
            server = null
        }
    }
}

/**
 * The id that tells a client whether a remembered position in the store still applies. Kept in a
 * file beside the database, so it lives and dies with the app's data; a fresh one per process when
 * the file cannot be used.
 */
@OptIn(ExperimentalUuidApi::class, UnstableInspektorAPI::class)
internal fun loadOrCreateStoreId(): String = try {
    val dir = Path(getAppDataDir(), "inspektor-remote")
    val file = Path(dir, "store-id")
    SystemFileSystem.createDirectories(dir)
    val existing = if (SystemFileSystem.exists(file)) {
        SystemFileSystem.source(file).buffered().use { it.readString() }.trim()
    } else {
        ""
    }
    existing.ifEmpty {
        Uuid.random().toString().also { id ->
            SystemFileSystem.sink(file).buffered().use { it.writeString(id) }
        }
    }
} catch (e: Throwable) {
    InspektorLog.error(e, "Inspektor remote") { "Could not keep a store id; using one per process" }
    Uuid.random().toString()
}
