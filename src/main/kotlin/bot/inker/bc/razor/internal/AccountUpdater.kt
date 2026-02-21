package bot.inker.bc.razor.internal

import bot.inker.bc.razor.RazorClientConfig
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class AccountUpdater(
    private val config: RazorClientConfig,
    private val executor: ScheduledExecutorService,
    private val sendFn: (Map<String, Any?>) -> Unit,
) {
    private val queue = LinkedHashMap<String, Any?>()
    private var debounceTimer: ScheduledFuture<*>? = null
    private var batchStart: Long = 0L

    @Synchronized
    fun queueData(fields: Map<String, Any?>, force: Boolean = false) {
        queue.putAll(fields)
        if (force) {
            syncToServer()
            return
        }
        if (batchStart == 0L) {
            batchStart = System.currentTimeMillis()
        }
        debounceTimer?.cancel(false)

        val elapsed = System.currentTimeMillis() - batchStart
        val delay = if (elapsed >= config.accountSyncMaxWaitMs) {
            0L
        } else {
            config.accountSyncDebounceMs.coerceAtMost(config.accountSyncMaxWaitMs - elapsed)
        }

        debounceTimer = executor.schedule({ syncToServer() }, delay, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun syncToServer() {
        debounceTimer?.cancel(false)
        debounceTimer = null
        if (queue.isEmpty()) return
        val data = LinkedHashMap(queue)
        queue.clear()
        batchStart = 0L
        sendFn(data)
    }

    @Synchronized
    fun clear() {
        debounceTimer?.cancel(false)
        debounceTimer = null
        queue.clear()
        batchStart = 0L
    }
}
