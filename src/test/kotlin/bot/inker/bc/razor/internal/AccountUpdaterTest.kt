package bot.inker.bc.razor.internal

import bot.inker.bc.razor.RazorClientConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class AccountUpdaterTest {

    private lateinit var executor: ScheduledExecutorService
    private lateinit var updater: AccountUpdater
    private val sentData = CopyOnWriteArrayList<Map<String, Any?>>()

    @BeforeEach
    fun setUp() {
        executor = Executors.newSingleThreadScheduledExecutor()
        sentData.clear()
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `force sync sends immediately`() {
        val config = RazorClientConfig(accountSyncDebounceMs = 5000, accountSyncMaxWaitMs = 10000)
        updater = AccountUpdater(config, executor) { sentData.add(it) }
        updater.queueData(mapOf("Money" to 100), force = true)
        assertEquals(1, sentData.size)
        assertEquals(100, sentData[0]["Money"])
    }

    @Test
    fun `debounce batches multiple updates`() {
        val config = RazorClientConfig(accountSyncDebounceMs = 200, accountSyncMaxWaitMs = 2000)
        updater = AccountUpdater(config, executor) { sentData.add(it) }

        updater.queueData(mapOf("Money" to 100))
        updater.queueData(mapOf("Owner" to "TestOwner"))

        // Nothing sent yet
        assertEquals(0, sentData.size)

        // Wait for debounce
        Thread.sleep(400)
        assertEquals(1, sentData.size)
        assertEquals(100, sentData[0]["Money"])
        assertEquals("TestOwner", sentData[0]["Owner"])
    }

    @Test
    fun `later updates overwrite earlier values for same key`() {
        val config = RazorClientConfig(accountSyncDebounceMs = 200, accountSyncMaxWaitMs = 2000)
        updater = AccountUpdater(config, executor) { sentData.add(it) }

        updater.queueData(mapOf("Money" to 100))
        updater.queueData(mapOf("Money" to 200))

        Thread.sleep(400)
        assertEquals(1, sentData.size)
        assertEquals(200, sentData[0]["Money"])
    }

    @Test
    fun `syncToServer flushes immediately`() {
        val config = RazorClientConfig(accountSyncDebounceMs = 5000, accountSyncMaxWaitMs = 10000)
        updater = AccountUpdater(config, executor) { sentData.add(it) }

        updater.queueData(mapOf("Money" to 100))
        assertEquals(0, sentData.size)

        updater.syncToServer()
        assertEquals(1, sentData.size)
    }

    @Test
    fun `clear prevents pending sends`() {
        val config = RazorClientConfig(accountSyncDebounceMs = 200, accountSyncMaxWaitMs = 2000)
        updater = AccountUpdater(config, executor) { sentData.add(it) }

        updater.queueData(mapOf("Money" to 100))
        updater.clear()

        Thread.sleep(400)
        assertEquals(0, sentData.size)
    }

    @Test
    fun `empty queue does not send`() {
        val config = RazorClientConfig(accountSyncDebounceMs = 100, accountSyncMaxWaitMs = 2000)
        updater = AccountUpdater(config, executor) { sentData.add(it) }
        updater.syncToServer()
        assertEquals(0, sentData.size)
    }
}
