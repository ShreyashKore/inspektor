package utils

import com.gyanoba.inspektor.Inspektor
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.DriverFactory
import com.gyanoba.inspektor.data.HttpTransaction
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.InspektorDatabase
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.adapters.instantAdapter
import com.gyanoba.inspektor.data.adapters.setMapEntryAdapter
import com.gyanoba.inspektor.data.setApplicationId
import com.gyanoba.inspektor.platform.getAppDataDir
import io.github.xxfast.kstore.file.extensions.listStoreOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import okio.Path.Companion.toPath

abstract class TestBase {
    internal val db by lazy { createTestDb() }

    internal val store by lazy {
        listStoreOf<Override>(
            file = getAppDataDir().toPath().resolve("overrideStore-test")
        )
    }

    init {
        @OptIn(UnstableInspektorAPI::class)
        setApplicationId("com.test.inspektor")
    }

    private fun createTestDb(): InspektorDatabase {
        val driver = DriverFactory.createTempDbDriver()
        return InspektorDatabase(
            driver, HttpTransaction.Adapter(
                requestDateAdapter = instantAdapter,
                responseDateAdapter = instantAdapter,
                requestHeadersAdapter = setMapEntryAdapter,
                responseHeadersAdapter = setMapEntryAdapter,
                replacedResponseHeadersAdapter = setMapEntryAdapter,
                replacedRequestHeadersAdapter = setMapEntryAdapter,
            )
        )
    }

    protected fun createMockClient(
        logLevel: LogLevel = LogLevel.BODY,
        block: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        return HttpClient(MockEngine { block(it) }) {
            install(Inspektor) {
                level = logLevel
                this.dataSource = InspektorDataSourceImpl(db)
                this.overrideRepository = OverrideRepositoryImpl(store)
            }
        }
    }
}