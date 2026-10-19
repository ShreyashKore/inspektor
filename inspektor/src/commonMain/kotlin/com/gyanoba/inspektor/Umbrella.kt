package com.gyanoba.inspektor

/**
 * The umbrella artifact carries no API of its own -- everything reaches consumers transitively from
 * `:inspektor-core`, `:inspektor-ui` and `:inspektor-ktor`.
 *
 * This file exists only so the module is not source-free. Kotlin/Native skips
 * `linkDebugFramework*` as `NO-SOURCE` when a module has no Kotlin sources at all, which would
 * leave a Swift consumer of `inspektor.framework` with no framework rather than merely an empty
 * one. With one source file present, the `export(...)` entries in `binaries.framework` put the
 * three real modules into the generated Objective-C headers.
 *
 * Deliberately `internal`: it must not appear in the public API dump.
 */
internal const val UMBRELLA_MARKER: String = "com.gyanoba.inspektor"
