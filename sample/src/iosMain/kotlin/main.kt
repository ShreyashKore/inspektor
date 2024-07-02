import androidx.compose.ui.window.ComposeUIViewController
import com.gyanoba.inspektor.sample.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
