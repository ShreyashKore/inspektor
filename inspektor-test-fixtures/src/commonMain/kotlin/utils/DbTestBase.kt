package utils

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.InspektorDatabase
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.createInspektorDatabase
import com.gyanoba.inspektor.data.setApplicationId
import io.github.xxfast.kstore.storeOf
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

val TEST_DIR: Path = Path("test")

/**
 * A fresh in-memory database and override store per test class, plus a scratch directory.
 *
 * Shared by every module's tests. The column adapters come from
 * [com.gyanoba.inspektor.data.createInspektorDatabase] rather than being restated here -- the two
 * copies used to drift.
 */
abstract class DbTestBase {
    val db: InspektorDatabase by lazy { createInspektorDatabase(createTempDbDriver()) }

    val store by lazy { storeOf<List<Override>>(codec = KStoreInMemoryCodec()) }

    /**
     * The real store over [db], signalling every write. Hand this to the integration under test,
     * then [awaitRequestLogged] / [awaitResponseLogged] before reading the table.
     *
     * Named so that `dataSource = testDataSource` inside a config block cannot quietly become a
     * self-assignment of the config's own `dataSource`.
     */
    val testDataSource: SignallingDataSource by lazy {
        SignallingDataSource(InspektorDataSourceImpl(db))
    }

    /** Suspends until one more call's request has been written to [testDataSource]. */
    suspend fun awaitRequestLogged(): Unit = testDataSource.awaitRequestLogged()

    /** Suspends until one more call's response has been written to [testDataSource]. */
    suspend fun awaitResponseLogged(): Unit = testDataSource.awaitResponseLogged()

    @BeforeTest
    fun createTestDir() {
        with(SystemFileSystem) { if (!exists(TEST_DIR)) createDirectories(TEST_DIR) }
    }

    @AfterTest
    fun deleteTestDir() {
        with(SystemFileSystem) { if (exists(TEST_DIR)) deleteRecursively(TEST_DIR) }
    }

    init {
        @OptIn(UnstableInspektorAPI::class)
        setApplicationId("com.test.inspektor")
    }
}

private fun FileSystem.deleteRecursively(path: Path, mustExist: Boolean = true) {
    list(path).forEach {
        if (metadataOrNull(it)?.isDirectory == true) {
            deleteRecursively(it, mustExist)
        } else {
            delete(it, mustExist)
        }
    }
    delete(path, mustExist)
}
