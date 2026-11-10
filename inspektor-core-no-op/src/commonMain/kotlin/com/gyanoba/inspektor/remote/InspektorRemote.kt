package com.gyanoba.inspektor.remote

import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * No-op counterpart of `com.gyanoba.inspektor.remote.InspektorRemote`.
 *
 * Kept so that consumer code starting remote inspection still compiles against the no-op
 * artifacts. Nothing listens: a production build that swaps in the no-op exposes no socket.
 */
@UnstableInspektorAPI
public object InspektorRemote {
    /** Always false: the no-op never runs a server. */
    public val isRunning: Boolean get() = false

    /** Does nothing. */
    public fun start() {}

    /** Does nothing. */
    public fun stop() {}
}
