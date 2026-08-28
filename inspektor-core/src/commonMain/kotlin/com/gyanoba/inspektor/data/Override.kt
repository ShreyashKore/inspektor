package com.gyanoba.inspektor.data

import com.gyanoba.inspektor.data.OverrideAction.Type.FixedRequest
import com.gyanoba.inspektor.data.OverrideAction.Type.FixedRequestResponse
import com.gyanoba.inspektor.data.OverrideAction.Type.FixedResponse
import kotlinx.serialization.SerialName
import com.gyanoba.inspektor.UnstableInspektorAPI
import kotlinx.serialization.Serializable

@Serializable
@UnstableInspektorAPI
public data class Override(
    public val id: Long,
    public val type: RequestType,
    public val matchers: List<Matcher>,
    public val action: OverrideAction,
    public val name: String? = null,
    public val enabled: Boolean = true,
) {
    public companion object {
        public val New: Override = Override(
            0, HttpRequest(HttpMethod.Get), emptyList(),
            OverrideAction(OverrideAction.Type.None)
        )
    }
}

@Serializable
@UnstableInspektorAPI
public sealed interface RequestType

@Serializable
@SerialName("http")
@UnstableInspektorAPI
public data class HttpRequest(public val method: HttpMethod) : RequestType

@UnstableInspektorAPI
public enum class HttpMethod {
    Get, Post, Put, Delete, Patch, Head, Options, Trace, Connect, Custom, Any;

    public companion object {
        public val currentlySupported = listOf(
            Get, Post, Put, Delete, Patch
        )

        public fun parse(method: String): HttpMethod {
            return currentlySupported.firstOrNull { it.name.equals(method, ignoreCase = true) } ?: Get
        }
    }
}

@Serializable
@UnstableInspektorAPI
public data class Replacement(
    public val statusCode: Int? = null,
    public val headers: Map<String, List<String>> = emptyMap(),
    public val body: String? = null,
)

@Serializable
@UnstableInspektorAPI
public sealed interface Matcher

@Serializable
@SerialName("url")
@UnstableInspektorAPI
public data class UrlMatcher(
    public val url: String,
) : Matcher

@Serializable
@SerialName("urlRegex")
@UnstableInspektorAPI
public data class UrlRegexMatcher(
    public val url: String,
) : Matcher

@Serializable
@SerialName("hostMatcher")
@UnstableInspektorAPI
public data class HostMatcher(
    public val host: String,
) : Matcher

@Serializable
@SerialName("pathMatcher")
@UnstableInspektorAPI
public data class PathMatcher(
    public val path: String,
) : Matcher

@Serializable
@UnstableInspektorAPI
public data class OverrideAction(
    public val type: Type,
    public val requestHeaders: Map<String, List<String>> = emptyMap(),
    public val requestBody: String? = null,
    public val statusCode: Int? = null,
    public val responseHeaders: Map<String, List<String>> = emptyMap(),
    public val responseBody: String? = null,
) {
    public enum class Type {
        FixedRequest, FixedResponse, FixedRequestResponse, None;
    }
    public val request: Boolean get() = this.type == FixedRequest || this.type == FixedRequestResponse
    public val response: Boolean get() = this.type == FixedResponse || this.type == FixedRequestResponse
}