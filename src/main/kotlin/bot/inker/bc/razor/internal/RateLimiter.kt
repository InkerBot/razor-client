package bot.inker.bc.razor.internal

import bot.inker.bc.razor.RazorClientConfig
import java.util.*

internal class RateLimiter(private val config: RazorClientConfig) {
    private val queue = LinkedList<QueuedMessage>()
    private val sendTimestamps = LinkedList<Long>()
    private var emitter: ((String, String) -> Unit)? = null

    data class QueuedMessage(val event: String, val payload: String)

    fun setEmitter(emitter: (String, String) -> Unit) {
        this.emitter = emitter
    }

    fun enqueue(event: String, payload: String) {
        synchronized(this) {
            queue.add(QueuedMessage(event, payload))
        }
        processQueue()
    }

    fun processQueue() {
        val emit = emitter ?: return
        synchronized(this) {
            val now = System.currentTimeMillis()
            val windowStart = now - config.rateLimitWindowMs
            sendTimestamps.removeAll { it < windowStart }

            while (sendTimestamps.size < config.rateLimitMaxMessages && queue.isNotEmpty()) {
                val msg = queue.poll() ?: break
                emit(msg.event, msg.payload)
                sendTimestamps.add(System.currentTimeMillis())
            }
        }
    }

    fun clear() {
        synchronized(this) {
            queue.clear()
            sendTimestamps.clear()
        }
    }

    val pendingCount: Int
        get() = synchronized(this) { queue.size }
}
