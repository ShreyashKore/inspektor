package utils

import com.gyanoba.inspektor.Inspektor
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.platform.NotificationManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData

/** [DbTestBase] plus a Ktor client wired to the in-memory store through a [MockEngine]. */
abstract class TestBase : DbTestBase() {

    protected fun createMockClient(
        logLevel: LogLevel = LogLevel.BODY,
        block: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        return HttpClient(MockEngine { block(it) }) {
            install(Inspektor) {
                level = logLevel
                this.dataSource = InspektorDataSourceImpl(db)
                this.overrideRepository = OverrideRepositoryImpl(store)
                this.notificationManager = object : NotificationManager {
                    override fun notify(title: String, message: String) {
                        println("$title: $message")
                    }
                }
            }
        }
    }
}
