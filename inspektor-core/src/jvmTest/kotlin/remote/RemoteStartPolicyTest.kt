package remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.remote.ALLOW_PROPERTY
import com.gyanoba.inspektor.remote.InspektorRemote
import com.gyanoba.inspektor.remote.remoteStartRefusal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(UnstableInspektorAPI::class)
class RemoteStartPolicyTest {
    @AfterTest
    fun tearDown() {
        InspektorRemote.stop()
        System.clearProperty(ALLOW_PROPERTY)
    }

    @Test
    fun `a desktop app not launched with the opt-in does not serve`() {
        System.clearProperty(ALLOW_PROPERTY)

        InspektorRemote.start()

        assertFalse(InspektorRemote.isRunning)
        assertTrue(remoteStartRefusal()!!.contains("-D$ALLOW_PROPERTY=true"))
    }

    @Test
    fun `only the exact opt-in counts`() {
        System.setProperty(ALLOW_PROPERTY, "yes")
        assertNotNull(remoteStartRefusal())

        System.setProperty(ALLOW_PROPERTY, "true")
        assertNull(remoteStartRefusal())
    }
}
