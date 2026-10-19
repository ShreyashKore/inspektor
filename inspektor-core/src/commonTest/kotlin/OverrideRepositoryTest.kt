import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.PathMatcher
import io.github.xxfast.kstore.Codec
import io.github.xxfast.kstore.storeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Keeps the store in memory, so the test touches no file. */
@OptIn(UnstableInspektorAPI::class)
private class MemoryCodec : Codec<List<Override>> {
    private var stored: List<Override>? = null
    override suspend fun decode(): List<Override>? = stored
    override suspend fun encode(value: List<Override>?) {
        stored = value
    }
}

@OptIn(UnstableInspektorAPI::class)
class OverrideRepositoryTest {

    /**
     * Interceptors read overrides through the synchronous [OverrideRepositoryImpl.all] cache, so
     * an override the user adds while the app runs must reach it. The cache used to be driven by
     * `MainScope()`, and a JVM with no Swing, JavaFX or Android has no Main dispatcher: the cache
     * kept whatever it read at startup, and every later override was silently ignored.
     */
    @Test
    fun `an override added after startup reaches the synchronous cache`() = runTest {
        val repository = OverrideRepositoryImpl(storeOf(codec = MemoryCodec()))
        repository.add(
            Override(
                id = 0,
                type = HttpRequest(HttpMethod.Get),
                matchers = listOf(PathMatcher("/added-later")),
                action = OverrideAction(OverrideAction.Type.FixedResponse, responseBody = "x"),
            )
        )

        // Real time, not runTest's virtual clock: the cache is updated from another dispatcher.
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (repository.all.isEmpty()) delay(10)
            }
        }
        assertEquals(listOf(PathMatcher("/added-later")), repository.all.single().matchers)
    }
}
