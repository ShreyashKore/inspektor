import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIApplication
import ui.App

public actual fun openInspektor() {
    val pluginViewController = ComposeUIViewController { App() }
    val topController = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: throw IllegalStateException("No key window or root view controller found")
    topController.presentViewController(pluginViewController, animated = true, completion = null)
}