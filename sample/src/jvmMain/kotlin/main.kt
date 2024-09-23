import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gyanoba.inspektor.sample.App
import data.db.APPLICATION_ID
import java.awt.Dimension

@OptIn(UnstableInspektorAPI::class)
fun main() = application {
    // set application id for desktop platforms to resolve the folder in which database will be stored
    APPLICATION_ID = "com.gyanoba.inspektor.sample"
    Window(
        title = "Inspektor Sample",
        state = rememberWindowState(width = 800.dp, height = 600.dp),
        onCloseRequest = ::exitApplication,
    ) {
        window.minimumSize = Dimension(350, 600)
        App()
    }
}