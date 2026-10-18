import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.encodedPath
import kotlinx.coroutines.test.runTest
import utils.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterTest : TestBase() {

    @Test
    fun `only calls a filter accepts are recorded`() = runTest {
        db.httpTransactionQueries.deleteAll()
        val client = createMockClient(
            configure = { filter { it.url.encodedPath.startsWith("/keep") } },
        ) { respondOk("OK") }

        client.get("http://localhost/drop")
        client.get("http://localhost/keep")
        awaitResponseLogged()

        val paths = db.httpTransactionQueries.getAll().executeAsList().map { it.path }
        assertEquals(listOf("/keep"), paths)
    }
}
