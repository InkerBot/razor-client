package bot.inker.bc.razor.internal

import bot.inker.bc.razor.RazorClientConfig
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RateLimiterTest {

    private lateinit var config: RazorClientConfig
    private lateinit var rateLimiter: RateLimiter
    private val sent = mutableListOf<Pair<String, String>>()

    @BeforeEach
    fun setUp() {
        config = RazorClientConfig(rateLimitMaxMessages = 3, rateLimitWindowMs = 500)
        rateLimiter = RateLimiter(config)
        sent.clear()
        rateLimiter.setEmitter { event, payload -> sent.add(event to payload) }
    }

    @Test
    fun `sends messages up to limit`() {
        rateLimiter.enqueue("A", "1")
        rateLimiter.enqueue("B", "2")
        rateLimiter.enqueue("C", "3")
        assertEquals(3, sent.size)
        assertEquals("A" to "1", sent[0])
        assertEquals("B" to "2", sent[1])
        assertEquals("C" to "3", sent[2])
    }

    @Test
    fun `queues messages beyond limit`() {
        rateLimiter.enqueue("A", "1")
        rateLimiter.enqueue("B", "2")
        rateLimiter.enqueue("C", "3")
        rateLimiter.enqueue("D", "4")
        assertEquals(3, sent.size)
        assertEquals(1, rateLimiter.pendingCount)
    }

    @Test
    fun `processes queue after window expires`() {
        rateLimiter.enqueue("A", "1")
        rateLimiter.enqueue("B", "2")
        rateLimiter.enqueue("C", "3")
        rateLimiter.enqueue("D", "4")
        assertEquals(3, sent.size)

        // Wait for window to expire
        Thread.sleep(600)
        rateLimiter.processQueue()

        assertEquals(4, sent.size)
        assertEquals("D" to "4", sent[3])
    }

    @Test
    fun `clear removes all pending messages`() {
        rateLimiter.enqueue("A", "1")
        rateLimiter.enqueue("B", "2")
        rateLimiter.enqueue("C", "3")
        rateLimiter.enqueue("D", "4")
        rateLimiter.enqueue("E", "5")

        rateLimiter.clear()
        assertEquals(0, rateLimiter.pendingCount)
    }

    @Test
    fun `no emitter means no sends`() {
        val limiter = RateLimiter(config)
        limiter.enqueue("A", "1")
        assertEquals(0, sent.size)
    }
}
