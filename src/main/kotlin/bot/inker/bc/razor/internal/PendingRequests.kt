package bot.inker.bc.razor.internal

import java.util.concurrent.*

internal class PendingRequests(
    private val timeoutMs: Long,
    private val executor: ScheduledExecutorService,
) {
    private data class PendingEntry<T>(
        val future: CompletableFuture<T>,
        val timeoutHandle: ScheduledFuture<*>,
    )

    private val pending = ConcurrentHashMap<String, PendingEntry<*>>()

    fun <T> create(key: String): CompletableFuture<T> {
        cancel(key)
        val future = CompletableFuture<T>()
        val timeout = executor.schedule({
            future.completeExceptionally(TimeoutException("Request '$key' timed out after ${timeoutMs}ms"))
            pending.remove(key)
        }, timeoutMs, TimeUnit.MILLISECONDS)
        pending[key] = PendingEntry(future, timeout)
        return future
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> complete(key: String, value: T): Boolean {
        val entry = pending.remove(key) ?: return false
        entry.timeoutHandle.cancel(false)
        (entry.future as CompletableFuture<T>).complete(value)
        return true
    }

    fun completeExceptionally(key: String, error: Throwable): Boolean {
        val entry = pending.remove(key) ?: return false
        entry.timeoutHandle.cancel(false)
        entry.future.completeExceptionally(error)
        return true
    }

    fun resetTimeout(key: String) {
        val entry = pending[key] ?: return
        entry.timeoutHandle.cancel(false)
        val newTimeout = executor.schedule({
            entry.future.completeExceptionally(TimeoutException("Request '$key' timed out after ${timeoutMs}ms"))
            pending.remove(key)
        }, timeoutMs, TimeUnit.MILLISECONDS)
        pending[key] = entry.copy(timeoutHandle = newTimeout)
    }

    fun cancel(key: String) {
        val entry = pending.remove(key) ?: return
        entry.timeoutHandle.cancel(false)
        entry.future.cancel(false)
    }

    fun cancelAll() {
        for ((_, entry) in pending) {
            entry.timeoutHandle.cancel(false)
            entry.future.cancel(false)
        }
        pending.clear()
    }
}
