package com.gyanoba.inspektor

/**
 * Opens the Inspektor UI. No-op — this artifact ships no UI.
 *
 * `expect`/`actual` rather than one common function so that each platform's facade class matches
 * `inspektor-ui`'s (`Inspektor_jvmKt`, `Inspektor_androidKt`), keeping the two binary-compatible.
 */
public expect fun openInspektor()
