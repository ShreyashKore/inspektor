package com.gyanoba.inspektor.remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.Override
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The protocol version this server speaks (docs/REMOTE_PROTOCOL.md). */
internal const val REMOTE_PROTOCOL_VERSION: Int = 1

/** Which body of a transaction a `body.get` asks for. */
internal enum class BodyPart(val wireName: String) {
    Request("request"),
    Response("response"),
    OriginalRequest("originalRequest"),
    OriginalResponse("originalResponse");

    companion object {
        fun fromWire(name: String?): BodyPart? = entries.firstOrNull { it.wireName == name }
    }
}

/** The `hello` a server sends first on every connection. Public identity only, no traffic. */
internal fun helloMessage(app: RemoteAppInfo, storeId: String, oldestId: Long?): JsonObject =
    buildJsonObject {
        put("type", "hello")
        put("protocol", REMOTE_PROTOCOL_VERSION)
        putJsonObject("sdk") {
            put("name", "inspektor")
            put("runtime", "kotlin")
            // Which integrations a process uses is not recorded yet; Eagly reads `adapter` per
            // transaction once it is.
            putJsonArray("adapters") {}
            put("overrides", true)
        }
        putJsonObject("app") {
            put("id", app.id)
            app.name?.let { put("name", it) }
            app.version?.let { put("version", it) }
            app.pid?.let { put("pid", it) }
            app.process?.let { put("process", it) }
        }
        putJsonObject("store") {
            put("id", storeId)
            put("persistent", true)
            oldestId?.let { put("oldestId", it.toString()) }
        }
    }

internal fun eventMessage(event: String, data: JsonElement? = null): JsonObject =
    buildJsonObject {
        put("type", "event")
        put("event", event)
        data?.let { put("data", it) }
    }

internal fun resultMessage(id: Long, result: JsonElement): JsonObject = buildJsonObject {
    put("id", id)
    put("result", result)
}

internal fun errorMessage(id: Long, code: String, message: String): JsonObject =
    buildJsonObject {
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

/**
 * The transaction object of the protocol, or null for a row that has no URL yet (a client could
 * not show it, and the protocol requires one).
 *
 * Bodies up to [inlineBodyLimit] bytes travel inline as `bodyData`; every other body stays on the
 * device until a `body.get` asks for it.
 */
@OptIn(UnstableInspektorAPI::class)
internal fun HttpTransaction.toRemoteJson(inlineBodyLimit: Int = 0): JsonObject? {
    val url = url?.takeIf { it.isNotEmpty() } ?: return null
    return buildJsonObject {
        put("id", id.toString())
        put("method", method ?: "GET")
        put("url", url)
        protocol?.let { put("protocol", it) }
        requestDate?.let { put("requestDate", it.toString()) }
        responseDate?.let { put("responseDate", it.toString()) }
        tookMs?.let { put("tookMs", it) }
        putJsonObject("request") {
            putHeaders(requestHeaders)
            requestContentType?.let { put("contentType", it) }
            requestPayloadSize?.let { put("size", it) }
            putBody(requestBody, requestPayloadSize, inlineBodyLimit)
        }
        responseCode?.let { code ->
            putJsonObject("response") {
                put("code", code)
                responseMessage?.let { put("message", it) }
                putHeaders(responseHeaders)
                responseContentType?.let { put("contentType", it) }
                responsePayloadSize?.let { put("size", it) }
                putBody(responseBody, responsePayloadSize, inlineBodyLimit)
                responseTlsVersion?.let { put("tlsVersion", it) }
                responseCipherSuite?.let { put("cipherSuite", it) }
            }
        }
        error?.let { put("error", it) }
        overrideJson()?.let { put("override", it) }
    }
}

/** What an override replaced, or null for an exchange no override touched. */
private fun HttpTransaction.overrideJson(): JsonObject? {
    val requestChanged = originalRequestHeaders != null || originalRequestBody != null
    val responseChanged = originalResponseCode != null || originalResponseHeaders != null ||
        originalResponseBody != null
    if (!requestChanged && !responseChanged) return null
    return buildJsonObject {
        putJsonObject("original") {
            if (requestChanged) {
                putJsonObject("request") {
                    if (originalRequestHeaders != null) putHeaders(originalRequestHeaders)
                    if (originalRequestBody != null) {
                        put("body", bodyState(originalRequestBody, null))
                    }
                }
            }
            if (responseChanged) {
                putJsonObject("response") {
                    originalResponseCode?.let { put("code", it) }
                    if (originalResponseHeaders != null) putHeaders(originalResponseHeaders)
                    if (originalResponseBody != null) {
                        put("body", bodyState(originalResponseBody, null))
                    }
                }
            }
        }
    }
}

/** Headers travel as `[[name, value], …]`: one pair per value, in the order they were recorded. */
private fun JsonObjectBuilder.putHeaders(headers: Set<Map.Entry<String, List<String>>>?) {
    putJsonArray("headers") {
        headers?.forEach { (name, values) ->
            values.forEach { value ->
                addJsonArray {
                    add(name)
                    add(value)
                }
            }
        }
    }
}

private fun JsonObjectBuilder.putBody(body: String?, payloadSize: Long?, inlineBodyLimit: Int) {
    val state = bodyState(body, payloadSize)
    put("body", state)
    if (body != null && inlineBodyLimit > 0 && (state == "available" || state == "truncated")) {
        val size = body.encodeToByteArray().size
        if (size <= inlineBodyLimit) {
            put("bodyData", body)
            put("encoding", "utf8")
        }
    }
}

/**
 * The protocol's body state. Bodies are recorded as text cut at the integration's
 * `maxContentLength`, so a body shorter than the payload the integration measured was truncated.
 */
internal fun bodyState(body: String?, payloadSize: Long?): String = when {
    body == null -> if (payloadSize == 0L) "empty" else "not_captured"
    body.isEmpty() -> "empty"
    payloadSize != null && payloadSize > body.encodeToByteArray().size -> "truncated"
    else -> "available"
}

internal fun HttpTransaction.body(part: BodyPart): String? = when (part) {
    BodyPart.Request -> requestBody
    BodyPart.Response -> responseBody
    BodyPart.OriginalRequest -> originalRequestBody
    BodyPart.OriginalResponse -> originalResponseBody
}

/**
 * A `body.get` result. Without a range the body goes as UTF-8 text; a range of its bytes goes as
 * base64, because a byte range can split a character.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun bodyResult(
    body: String,
    truncated: Boolean,
    offset: Int?,
    length: Int?,
): JsonObject {
    val bytes = body.encodeToByteArray()
    return buildJsonObject {
        if (offset == null && length == null) {
            put("encoding", "utf8")
            put("data", body)
        } else {
            val start = (offset ?: 0).coerceIn(0, bytes.size)
            val end = (start + (length ?: bytes.size)).coerceIn(start, bytes.size)
            put("encoding", "base64")
            put("data", Base64.encode(bytes, start, end))
        }
        put("size", bytes.size)
        put("truncated", truncated)
    }
}

@OptIn(UnstableInspektorAPI::class)
internal fun overridesJson(overrides: List<Override>): JsonArray =
    RemoteJson.encodeToJsonElement(ListSerializer(Override.serializer()), overrides) as JsonArray

@OptIn(UnstableInspektorAPI::class)
internal fun overrideJson(override: Override): JsonElement =
    RemoteJson.encodeToJsonElement(Override.serializer(), override)

@OptIn(UnstableInspektorAPI::class)
internal fun overrideFromJson(json: JsonElement): Override =
    RemoteJson.decodeFromJsonElement(Override.serializer(), json)

internal val EmptyJsonObject: JsonObject = JsonObject(emptyMap())

internal fun jsonArrayOf(elements: List<JsonElement>): JsonArray = buildJsonArray {
    elements.forEach { add(it) }
}
