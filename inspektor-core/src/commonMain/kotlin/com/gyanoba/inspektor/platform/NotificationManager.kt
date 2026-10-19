package com.gyanoba.inspektor.platform

import com.gyanoba.inspektor.UnstableInspektorAPI

@UnstableInspektorAPI
public expect fun NotificationManager(): NotificationManager

/**
 * Surfaces "a request was just recorded" to the user.
 *
 * Integrations take one of these rather than constructing it, so a headless consumer can silence
 * notifications entirely by turning `showNotifications` off.
 */
@UnstableInspektorAPI
public interface NotificationManager {
    public fun notify(title: String, message: String)

    public companion object {
        internal const val TAG = "Inspektor com.gyanoba.inspektor.platform.NotificationManager"
    }
}