package com.gyanoba.inspektor.data

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
        public val New: Override = Override(0, HttpRequest(HttpMethod.Get), emptyList(), NoAction)
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
public sealed interface OverrideAction {
    public enum class Type {
        FixedRequest, FixedResponse, None
    }
}


internal val OverrideAction.request: Boolean
    get() = this is FixedRequestAction

internal val OverrideAction.response: Boolean
    get() = this is FixedResponseAction


@Serializable
@SerialName("FixedRequest")
internal data class FixedRequestAction(
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
) : OverrideAction

@Serializable
@SerialName("FixedResponse")
internal data class FixedResponseAction(
    val statusCode: Int? = null,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String? = null,
) : OverrideAction

@Serializable
@SerialName("NoAction")
internal data object NoAction: OverrideAction


internal fun OverrideAction.copy(
    headers: Map<String, List<String>> = this.headersOrEmpty,
    body: String? = this.bodyOrEmpty,
    statusCode: Int? = (this as? FixedResponseAction)?.statusCode,
): OverrideAction = when (this) {
    is FixedRequestAction -> FixedRequestAction(headers, body)
    is FixedResponseAction -> FixedResponseAction(statusCode, headers, body)
    is NoAction -> NoAction
}


internal val OverrideAction.headersOrEmpty: Map<String, List<String>>
    get() = when (this) {
        is FixedRequestAction -> headers
        is FixedResponseAction -> headers
        NoAction -> emptyMap()
    }

internal val OverrideAction.bodyOrEmpty: String
    get() = when (this) {
        is FixedRequestAction -> body
        is FixedResponseAction -> body
        NoAction -> null
    } ?: ""