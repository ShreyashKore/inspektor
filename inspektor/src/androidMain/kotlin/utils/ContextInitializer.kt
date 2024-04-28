package utils

import android.content.Context
import androidx.startup.Initializer

class ContextInitializer : Initializer<Context> {
    companion object {
        private var _appContext: Context? = null
        val appContext: Context get() = _appContext!!

    }

    override fun create(context: Context): Context {
        _appContext = context
        return context
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> =
        mutableListOf()
}