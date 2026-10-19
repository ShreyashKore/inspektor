package com.gyanoba.inspektor

/**
 * Marks declarations that exist so that integrations (Ktor, OkHttp, URLSession, ...) can drive the
 * core, but which are not yet a stable contract for application code.
 *
 * Anything an integration module needs has to be `public`, which would otherwise freeze it under
 * binary compatibility validation. This marker keeps those declarations honest: they are visible,
 * they are validated, and they may still change.
 */
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    message = "This API is unstable and may be removed in the future.",
    level = RequiresOptIn.Level.ERROR,
)
public annotation class UnstableInspektorAPI
