package bot.inker.bc.razor

data class RazorClientConfig(
    val serverUrl: String = "http://localhost:4288",
    val rateLimitMaxMessages: Int = 14,
    val rateLimitWindowMs: Long = 1200L,
    val accountSyncDebounceMs: Long = 2000L,
    val accountSyncMaxWaitMs: Long = 8000L,
    val requestTimeoutMs: Long = 30000L,
)
