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
 * **Off until [start] is called, and [start] serves only a development build.** Whoever reaches
 * the socket can read every recorded request and change responses through overrides, so it must
 * never run in a build that reaches real users. Production should ship the no-op artifacts, which
 * contain no server at all; in case a build ships the real library anyway, [start] refuses to run
 * unless the build is one of these:
 *
 * - **Android:** a debuggable app. A build that is not debuggable, such as a UAT build, serves only
 *   if its manifest has
 *   `<meta-data android:name="com.gyanoba.inspektor.remote.ALLOW" android:value="true"/>`.
 * - **iOS:** an app on the simulator, or one signed for development (`get-task-allow`). An ad hoc
 *   or TestFlight build serves only if its Info.plist sets `InspektorRemoteAllow` to `YES`.
 * - **Desktop:** there is no debug build to recognise, so only a JVM launched with
 *   `-Dinspektor.remote.allow=true`.
 *
 * The opt-in lives in the build's own manifest, Info.plist or launch arguments, never in code, so
 * a call to [start] in shared code cannot turn the server on in every build at once.
 *
 * The socket is local to the device: an abstract Unix socket on Android, a `127.0.0.1` port on iOS
 * and desktop. Nothing on the network can reach it, and it does not speak HTTP, so a web page
 * cannot drive it either. Other apps on the device are kept out too: Android accepts only adb, root
 * and the app itself; on iOS and desktop a client must first present a token that is new on every
 * start and written only to the device log.
 *
 * When the server starts listening it writes one line to the device log naming its socket or port,
 * and its token where it has one; when [start] refuses, that line says why.
 */
@UnstableInspektorAPI
public object InspektorRemote {
    private val lock = SynchronizedObject()
    private var server: RemoteServer? = null

    /** Whether the server runs: [start] was called in a build that allows it, and [stop] was not. */
    public val isRunning: Boolean get() = synchronized(lock) { server != null }

    /**
     * Starts the remote-inspection server, in the background, if this build allows it (see
     * [InspektorRemote]); otherwise writes why not to the device log and does nothing. Safe to
     * call more than once and from any thread; it returns at once.
     */
    public fun start() {
        synchronized(lock) {
            if (server != null) return
            remoteStartRefusal()?.let { reason ->
                logToDevice("Inspektor: remote inspection is off: $reason")
                return
            }
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
