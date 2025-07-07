import com.gyanoba.inspektor.RetentionManager
import com.gyanoba.inspektor.data.InspektorDataSource
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RetentionManagerTest {

    private lateinit var mockDataSource: InspektorDataSource
    private lateinit var clock: Clock
    private lateinit var retentionManager: RetentionManager

    @BeforeTest
    fun setup() {
        mockDataSource = mock()
        clock = mock()
    }

    @AfterTest
    fun teardown() {
    }

    @Test
    fun `checkAndCleanUp should clean up after cleanupFrequency for short retention`() = runTest {
        val retentionDuration = 5.minutes
        val initialTime = Clock.System.now()
        every { clock.now() } returns initialTime

        retentionManager = RetentionManager(
            retentionDuration = retentionDuration,
            dataSource = mockDataSource,
            clock = clock,
        )

        // First call to set lastCleanupTime internally
        val firstCheckTime = initialTime + 1.seconds // Simulate a small time passing for the first check
        every { clock.now() } returns firstCheckTime
        retentionManager.checkAndCleanUp()
        verifySuspend(VerifyMode.exactly(1)) { mockDataSource.deleteBefore(any()) } // Still not called yet

        // Advance time by cleanupFrequency (1 minute for 0-60 minutes retention)
        val secondCheckTime = firstCheckTime + 1.minutes + 1.seconds
        every { clock.now() } returns secondCheckTime

        retentionManager.checkAndCleanUp()

        val expectedDeleteBefore = secondCheckTime - retentionDuration
        verifySuspend(VerifyMode.exactly(1)) { mockDataSource.deleteBefore(expectedDeleteBefore) }
    }

    @Test
    fun `checkAndCleanUp should clean up after cleanupFrequency for long retention`() = runTest {
        val retentionDuration = 90.minutes // Greater than 60 minutes
        val initialTime = Clock.System.now()
        every { clock.now() } returns initialTime

        retentionManager = RetentionManager(
            retentionDuration = retentionDuration,
            dataSource = mockDataSource,
            clock = clock,
        )

        // First call to set lastCleanupTime internally
        val firstCheckTime = initialTime + 1.seconds
        every { clock.now() } returns firstCheckTime
        retentionManager.checkAndCleanUp()
        verifySuspend(VerifyMode.exactly(1)) { mockDataSource.deleteBefore(any()) }

        // Advance time by cleanupFrequency (10 minutes for > 60 minutes retention)
        val secondCheckTime = firstCheckTime + 10.minutes + 1.seconds
        every { clock.now() } returns secondCheckTime

        retentionManager.checkAndCleanUp()

        val expectedDeleteBefore = secondCheckTime - retentionDuration
        verifySuspend(VerifyMode.exactly(1)) { mockDataSource.deleteBefore(expectedDeleteBefore) }
    }

    @Test
    fun `checkAndCleanUp should not clean up if cleanupFrequency has not passed`() = runTest {
        val retentionDuration = 5.minutes
        val initialTime = Clock.System.now()
        every { clock.now() } returns initialTime

        retentionManager = RetentionManager(
            retentionDuration = retentionDuration,
            dataSource = mockDataSource,
            clock = clock,
        )

        // First call to set lastCleanupTime internally
        val firstCheckTime = initialTime + 1.seconds
        every { clock.now() } returns firstCheckTime
        retentionManager.checkAndCleanUp()
        verifySuspend(VerifyMode.exactly(1)) { mockDataSource.deleteBefore(any()) }

        // Advance time by less than cleanupFrequency (1 minute - 1 second)
        val secondCheckTime = firstCheckTime + 1.minutes - 1.seconds
        every { clock.now() } returns secondCheckTime
        retentionManager.checkAndCleanUp()
        verifySuspend(VerifyMode.exactly(0)) { mockDataSource.deleteBefore(any()) }
    }

    @Test
    fun `cleanUpOldTransactions should handle exceptions from dataSource`() = runTest {
        val retentionDuration = 5.minutes
        val initialTime = Clock.System.now()
        every { clock.now() } returns initialTime

        retentionManager = RetentionManager(
            retentionDuration = retentionDuration,
            dataSource = mockDataSource,
            clock = clock,
        )

        // First call to set lastCleanupTime internally
        val firstCheckTime = initialTime + 1.seconds
        every { clock.now() } returns firstCheckTime
        retentionManager.checkAndCleanUp()

        // Simulate an exception from deleteBefore when it's actually called
        val secondCheckTime = firstCheckTime + 1.minutes + 1.seconds
        every { clock.now() } returns secondCheckTime
        everySuspend { mockDataSource.deleteBefore(any()) } throws RuntimeException("Database error")

        retentionManager.checkAndCleanUp()

        // Verify that deleteBefore was attempted, even if it threw an exception
        val expectedDeleteBefore = secondCheckTime - retentionDuration
        verifySuspend(VerifyMode.exactly(1)) { mockDataSource.deleteBefore(expectedDeleteBefore) }
        // The test should pass without crashing due to the exception being caught internally by RetentionManager.
    }
}