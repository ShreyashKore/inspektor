package com.gyanoba.inspektor.data

import com.gyanoba.inspektor.data.OverrideAction.Type.FixedRequest
import com.gyanoba.inspektor.data.OverrideAction.Type.FixedRequestResponse
import com.gyanoba.inspektor.data.OverrideAction.Type.FixedResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class Override(
    val id: Long,
    val type: RequestType,
    val matchers: List<Matcher>,
    val action: OverrideAction,
    val name: String? = null,
    val enabled: Boolean = true,
) {
    public companion object {
        public val New: Override = Override(
            0, HttpRequest(HttpMethod.Get), emptyList(),
            OverrideAction(OverrideAction.Type.None)
        )
    }
}

@Serializable
public sealed interface RequestType

@Serializable
@SerialName("http")
internal data class HttpRequest(val method: HttpMethod) : RequestType

internal enum class HttpMethod {
    Get, Post, Put, Delete, Patch, Head, Options, Trace, Connect, Custom, Any;

    companion object {
        val currentlySupported = listOf(
            Get, Post, Put, Delete, Patch
        )

        fun parse(method: String): HttpMethod {
            return currentlySupported.firstOrNull { it.name.equals(method, ignoreCase = true) } ?: Get
        }
    }
}

@Serializable
public data class Replacement(
    val statusCode: Int? = null,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
)

@Serializable
public sealed interface Matcher

@Serializable
@SerialName("url")
internal data class UrlMatcher(
    val url: String,
) : Matcher

@Serializable
@SerialName("urlRegex")
internal data class UrlRegexMatcher(
    val url: String,
) : Matcher

@Serializable
@SerialName("hostMatcher")
internal data class HostMatcher(
    val host: String,
) : Matcher

@Serializable
@SerialName("pathMatcher")
internal data class PathMatcher(
    val path: String,
) : Matcher

@Serializable
public data class OverrideAction(
    val type: Type,
    val requestHeaders: Map<String, List<String>> = emptyMap(),
    val requestBody: String? = null,
    val statusCode: Int? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    val responseBody: String? = null,
) {
    public enum class Type {
        FixedRequest, FixedResponse, FixedRequestResponse, None;
    }
    internal val request: Boolean get() = this.type == FixedRequest || this.type == FixedRequestResponse
    internal val response: Boolean get() = this.type == FixedResponse || this.type == FixedRequestResponse
}