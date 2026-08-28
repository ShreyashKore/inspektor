package com.gyanoba.inspektor.data

import com.gyanoba.inspektor.InspektorRequest
import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * Decides *whether* a call is overridden. Applying the result is the integration's job, because
 * swapping a body means building an `OutgoingContent`, an OkHttp `RequestBody` or an
 * `NSURLRequest` respectively -- there is nothing shared to share.
 *
 * Lookups are synchronous by design: they run inside interceptor and pipeline hooks that cannot
 * suspend. [OverrideRepositoryImpl] keeps a warm `StateFlow` cache so that stays cheap.
 */
@UnstableInspektorAPI
public class OverrideEngine(
    private val repository: OverrideRepository,
) {
    /** The override to apply to an outgoing [request], or null. */
    public fun findRequestOverride(request: InspektorRequest): Override? =
        find(request) { it.action.request }

    /** The override to apply to the response of [request], or null. */
    public fun findResponseOverride(request: InspektorRequest): Override? =
        find(request) { it.action.response }

    private inline fun find(
        request: InspektorRequest,
        directionMatches: (Override) -> Boolean,
    ): Override? = repository.all.firstOrNull { override ->
        override.enabled &&
            directionMatches(override) &&
            override.type.let { it is HttpRequest && it.method.name.equals(request.method, true) } &&
            override.matchers.all { it.matches(request) }
    }
}

/** True when this matcher accepts [request]. */
@UnstableInspektorAPI
public fun Matcher.matches(request: InspektorRequest): Boolean = when (this) {
    is UrlMatcher -> url == request.url
    is HostMatcher -> host == request.host
    is PathMatcher -> path == request.path
    is UrlRegexMatcher -> Regex(url).matches(request.url)
}
