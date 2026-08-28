package com.gyanoba.inspektor.sample.data

import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.okhttp.InterceptorMode
import com.gyanoba.inspektor.okhttp.installInspektor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

actual object PlatformHttpDemo {
    actual val clientName: String = "OkHttp"

    private val client: OkHttpClient = OkHttpClient.Builder()
        // Application position: one record per call, showing the request as this app wrote it.
        // Switch to InterceptorMode.NETWORK to see redirect hops and on-the-wire encoding instead.
        .installInspektor(InterceptorMode.APPLICATION) {
            level = LogLevel.BODY
            sanitizeHeader { header -> header.equals("Authorization", ignoreCase = true) }
        }
        .build()

    actual suspend fun fetchTodo(id: Int): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://jsonplaceholder.typicode.com/todos/$id")
            .header("Authorization", "Bearer sample-token-that-must-not-be-stored")
            .build()
        client.newCall(request).execute().use { it.body.string() }
    }
}
