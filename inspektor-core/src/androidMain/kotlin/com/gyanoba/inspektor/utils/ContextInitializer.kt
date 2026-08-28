package com.gyanoba.inspektor.utils

import android.content.Context
import androidx.startup.Initializer
import com.gyanoba.inspektor.UnstableInspektorAPI

/**
 * Captures the application [Context] at process start via `androidx.startup`.
 *
 * Lives in core because storage needs a Context before any UI exists, and is public because
 * `:inspektor-ui` and every Android integration module need it too. The `<meta-data>` entry that
 * registers it is in this module's manifest; referenced there by fully qualified name, so neither
 * the package nor the class name may change.
 */
@UnstableInspektorAPI
public class ContextInitializer : Initializer<Context> {
    public companion object {
        private var _appContext: Context? = null
        public val appContext: Context get() = _appContext!!
    }

    override fun create(context: Context): Context {
        _appContext = context
        return context
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> = mutableListOf()
}
