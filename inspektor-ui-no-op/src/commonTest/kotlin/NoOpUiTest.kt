import com.gyanoba.inspektor.openInspektor
import kotlin.test.Test

/**
 * The contract of the no-op viewer. The real `openInspektor()` starts an Activity, opens a Swing
 * window or presents a view controller; this one must do none of that, on any platform.
 */
class NoOpUiTest {

    @Test
    fun `openInspektor does nothing and does not throw`() {
        openInspektor()
    }
}
