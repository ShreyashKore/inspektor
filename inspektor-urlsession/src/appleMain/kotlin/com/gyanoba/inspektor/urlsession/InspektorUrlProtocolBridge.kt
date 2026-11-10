package com.gyanoba.inspektor.urlsession

import platform.Foundation.NSURLSessionConfiguration

/**
 * [InspektorUrlProtocol] for Swift and Objective-C.
 *
 * Kotlin/Native declares a Kotlin subclass of an Objective-C class in the framework header with
 * none of its members, so Swift sees [InspektorUrlProtocol] but cannot call its companion. This
 * object forwards to it.
 *
 * It also adds the protocol to a session configuration. That is how to capture a session the app
 * does not build itself -- React Native's, for one -- since a session made from its own
 * configuration does not consult protocols registered globally.
 *
 * ```swift
 * let inspektor = InspektorUrlProtocolBridge.shared
 * inspektor.prepare(loaderConfiguration: .default) { $0.level = .body }
 *
 * let configuration = URLSessionConfiguration.default
 * inspektor.addTo(configuration: configuration)
 * ```
 */
public object InspektorUrlProtocolBridge {
    /** [InspektorUrlProtocol.register], with nothing left to a default argument. */
    public fun register(
        loaderConfiguration: NSURLSessionConfiguration,
        configure: (InspektorUrlSessionConfig) -> Unit,
    ) {
        InspektorUrlProtocol.register(loaderConfiguration) { configure(this) }
    }

    /** [InspektorUrlProtocol.prepare], with nothing left to a default argument. */
    public fun prepare(
        loaderConfiguration: NSURLSessionConfiguration,
        configure: (InspektorUrlSessionConfig) -> Unit,
    ) {
        InspektorUrlProtocol.prepare(loaderConfiguration) { configure(this) }
    }

    /** [InspektorUrlProtocol.unregister]. */
    public fun unregister() {
        InspektorUrlProtocol.unregister()
    }

    /**
     * Puts [InspektorUrlProtocol] first in [configuration]'s `protocolClasses`, so that sessions
     * made from it are captured. They record nothing until [prepare] or [register] has run.
     */
    public fun addTo(configuration: NSURLSessionConfiguration) {
        configuration.protocolClasses =
            listOf(InspektorUrlProtocol) + configuration.protocolClasses.orEmpty()
    }
}
