import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.setApplicationId
import com.gyanoba.inspektor.sample.data.MockApi
import kotlin.test.Test
import kotlin.test.assertEquals

class MockApiJvmTest {
    init {
        @OptIn(UnstableInspektorAPI::class)
        setApplicationId("com.test.inspektor.sample")
    }

    @Test
    fun `mock api todo can be deserialized on jvm`() {
        val todo = MockApi.getTodo()

        assertEquals(1, todo.id)
        assertEquals(1, todo.userId)
        assertEquals("Todo 1", todo.title)
        assertEquals(false, todo.completed)
    }
}

