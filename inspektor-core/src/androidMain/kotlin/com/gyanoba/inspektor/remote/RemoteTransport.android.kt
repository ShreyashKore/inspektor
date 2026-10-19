package com.gyanoba.inspektor.remote

import android.app.Application
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.platform.getAppName
import com.gyanoba.inspektor.utils.ContextInitializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * An abstract Unix socket named `inspektor_<pid>_kotlin`. Eagly finds it in `/proc/net/unix` and
 * reaches it with `adb forward tcp:0 localabstract:<name>`; the pid keeps the processes of one app
 * apart, and the runtime suffix keeps a Dart server in the same process apart from this one.
 */
internal actual fun openRemoteListener(): RemoteListener {
    val name = "inspektor_${Process.myPid()}_kotlin"
    return LocalSocketListener(LocalServerSocket(name), name)
}

private class LocalSocketListener(
    private val server: LocalServerSocket,
    private val name: String,
) : RemoteListener {
    @Volatile
    private var closed = false

    override val address: String = "@$name"

    override suspend fun accept(): RemoteConnection = withContext(Dispatchers.IO) {
        val socket = server.accept()
        if (closed) {
            socket.close()
            throw IOException("The listener is closed.")
        }
        LocalSocketConnection(socket)
    }

    override fun close() {
        if (closed) return
        closed = true
        // Closing a LocalServerSocket does not wake a thread blocked in accept(), so connect once
        // to make it return; it sees `closed` and gives up.
        runCatching { LocalSocket().use { it.connect(LocalSocketAddress(name)) } }
        runCatching { server.close() }
    }
}

private class LocalSocketConnection(private val socket: LocalSocket) : RemoteConnection {
    private val input = socket.inputStream
    private val output = socket.outputStream

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        withContext(Dispatchers.IO) { input.read(buffer, offset, length) }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        output.write(bytes)
        output.flush()
    }

    override fun close() {
        // shutdown first: close() alone does not wake a thread blocked in read().
        runCatching { socket.shutdownInput() }
        runCatching { socket.shutdownOutput() }
        runCatching { socket.close() }
    }
}

@OptIn(UnstableInspektorAPI::class)
internal actual fun remoteAppInfo(): RemoteAppInfo {
    val context = ContextInitializer.appContext
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    return RemoteAppInfo(
        id = context.packageName,
        name = runCatching { getAppName() }.getOrNull(),
        version = packageInfo?.let { info ->
            // An app built without a versionName (or code) reports null (or 0) for it.
            val code = PackageInfoCompat.getLongVersionCode(info).takeIf { it > 0 }
            listOfNotNull(info.versionName, code?.let { "($it)" }).joinToString(" ").ifEmpty { null }
        },
        pid = Process.myPid().toLong(),
        process = processName(),
    )
}

private fun processName(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        runCatching {
            File("/proc/self/cmdline").readText().substringBefore('\u0000').ifEmpty { null }
        }.getOrNull()
    }

internal actual fun logToDevice(message: String) {
    Log.i("Inspektor", message)
}
