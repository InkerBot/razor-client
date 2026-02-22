package bot.inker.bc.razor.telegram

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.protocol.common.ForceDisconnectReason
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ConnectionWatchdog(
    private val config: TelegramConfig,
    private val reconnectAction: () -> Unit,
) {
    private val logger = LoggerFactory.getLogger(ConnectionWatchdog::class.java)

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "connection-watchdog").apply { isDaemon = true }
    }

    private val failureCount = AtomicInteger(0)
    private val lastServerInfoTime = AtomicLong(System.currentTimeMillis())
    private val disconnectedSince = AtomicLong(0L)
    private val state = AtomicReference(WatchdogState.CONNECTED)
    private var watchdogTask: ScheduledFuture<*>? = null

    @Volatile
    private var suppressReconnect = false

    enum class WatchdogState {
        CONNECTED,
        DISCONNECTED,
        RECONNECTING,
    }

    fun start() {
        lastServerInfoTime.set(System.currentTimeMillis())
        watchdogTask = executor.scheduleAtFixedRate(
            ::checkHealth,
            config.livenessCheckIntervalMs,
            config.livenessCheckIntervalMs,
            TimeUnit.MILLISECONDS,
        )
        logger.info(
            "Watchdog started (disconnectTimeout={}ms, livenessTimeout={}ms, checkInterval={}ms)",
            config.watchdogDisconnectTimeoutMs,
            config.livenessTimeoutMs,
            config.livenessCheckIntervalMs,
        )
    }

    fun stop() {
        watchdogTask?.cancel(false)
        watchdogTask = null
        executor.shutdown()
    }

    fun onEvent(event: RazorEvent) {
        when (event) {
            is RazorEvent.ServerInfo -> {
                lastServerInfoTime.set(System.currentTimeMillis())
                disconnectedSince.set(0L)
                failureCount.set(0)
                state.set(WatchdogState.CONNECTED)
                suppressReconnect = false
            }

            is RazorEvent.Connected -> {
                disconnectedSince.set(0L)
                state.set(WatchdogState.CONNECTED)
            }

            is RazorEvent.Disconnected -> {
                if (disconnectedSince.get() == 0L) {
                    disconnectedSince.set(System.currentTimeMillis())
                }
                state.set(WatchdogState.DISCONNECTED)
            }

            is RazorEvent.ForceDisconnect -> {
                handleForceDisconnect(event.reason)
            }

            else -> {}
        }
    }

    private fun handleForceDisconnect(reason: ForceDisconnectReason) {
        when (reason) {
            ForceDisconnectReason.DUPLICATED_LOGIN -> {
                logger.warn("Force disconnected: duplicated login. Auto-reconnect suppressed.")
                suppressReconnect = true
            }

            ForceDisconnectReason.RATE_LIMITED -> {
                logger.warn("Force disconnected: rate limited. Auto-reconnect suppressed.")
                suppressReconnect = true
            }

            else -> {
                logger.warn("Force disconnected: {}", reason)
            }
        }
    }

    private fun checkHealth() {
        if (suppressReconnect) return

        val now = System.currentTimeMillis()

        // Layer 2: Application-level watchdog - disconnect timeout
        val disconnectTime = disconnectedSince.get()
        if (disconnectTime > 0 && now - disconnectTime > config.watchdogDisconnectTimeoutMs) {
            logger.warn(
                "Disconnected for {}ms (threshold={}ms), triggering full reconnect",
                now - disconnectTime,
                config.watchdogDisconnectTimeoutMs,
            )
            triggerReconnect()
            return
        }

        // Layer 3: Liveness heartbeat - ServerInfo timeout
        val lastInfo = lastServerInfoTime.get()
        if (state.get() == WatchdogState.CONNECTED && now - lastInfo > config.livenessTimeoutMs) {
            logger.warn(
                "No ServerInfo for {}ms (threshold={}ms), suspected silent disconnect, triggering full reconnect",
                now - lastInfo,
                config.livenessTimeoutMs,
            )
            triggerReconnect()
            return
        }
    }

    private fun triggerReconnect() {
        if (state.compareAndSet(WatchdogState.DISCONNECTED, WatchdogState.RECONNECTING) ||
            state.compareAndSet(WatchdogState.CONNECTED, WatchdogState.RECONNECTING)
        ) {
            val failures = failureCount.incrementAndGet()
            val backoffMs = calculateBackoff(failures)
            logger.info("Scheduling reconnect in {}ms (failure #{})", backoffMs, failures)

            executor.schedule({
                try {
                    lastServerInfoTime.set(System.currentTimeMillis())
                    disconnectedSince.set(0L)
                    reconnectAction()
                } catch (e: Exception) {
                    logger.error("Reconnect failed", e)
                    state.set(WatchdogState.DISCONNECTED)
                    disconnectedSince.set(System.currentTimeMillis())
                }
            }, backoffMs, TimeUnit.MILLISECONDS)
        }
    }

    private fun calculateBackoff(failures: Int): Long {
        // Exponential backoff: 5s, 10s, 20s, 40s, ... capped at maxBackoff
        val baseMs = 5000L
        val backoff = baseMs * (1L shl minOf(failures - 1, 20))
        return minOf(backoff, config.watchdogMaxBackoffMs)
    }
}
