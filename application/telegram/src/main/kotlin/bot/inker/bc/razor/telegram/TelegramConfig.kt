package bot.inker.bc.razor.telegram

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

private val logger = LoggerFactory.getLogger(TelegramConfig::class.java)

data class RoomConfig(
    val description: String = "",
    val background: String = "MainHall",
    val limit: Int = 10,
    val admin: List<Int> = emptyList(),
    val ban: List<Int> = emptyList(),
    val whitelist: List<Int> = emptyList(),
    val visibility: List<String> = emptyList(),
    val access: List<String> = emptyList(),
    val blockCategory: List<String> = emptyList(),
    val language: String = "CN",
    val space: String = "",
)

data class TelegramConfig(
    // BC connection
    val serverUrl: String = "https://bondage-club-server.herokuapp.com/",
    val origin: String = "https://www.bondageprojects.elementfx.com",
    val referer: String = "https://www.bondageprojects.elementfx.com/R124/BondageClub/",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val accountName: String = "",
    val password: String = "",
    val roomName: String = "",

    // Room settings
    val room: RoomConfig = RoomConfig(),

    // Telegram
    val botToken: String = "",
    val chatId: Long = 0L,
    val generalTopicId: Int? = null,

    // Reconnection - Layer 1 (Socket.IO built-in)
    val socketIoReconnectionAttempts: Int = 50,
    val socketIoReconnectionDelay: Long = 1000,
    val socketIoReconnectionDelayMax: Long = 30000,

    // Reconnection - Layer 2 (application-level watchdog)
    val watchdogDisconnectTimeoutMs: Long = 120000,
    val watchdogMaxBackoffMs: Long = 300000,

    // Reconnection - Layer 3 (liveness heartbeat)
    val livenessTimeoutMs: Long = 180000,
    val livenessCheckIntervalMs: Long = 30000,

    // Media server
    val mediaServerEnabled: Boolean = false,
    val mediaServerHost: String = "0.0.0.0",
    val mediaServerPort: Int = 8090,
    val mediaBaseUrl: String = "http://localhost:8090",
    val mediaStoragePath: String = "media",

    // Topic cache
    val topicCachePath: String = "topic-cache.json",
) {
    companion object {
        private val CONFIG_PATH: Path = Path.of("razor-telegram.json")
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(): TelegramConfig {
            if (!Files.exists(CONFIG_PATH)) {
                return TelegramConfig()
            }
            return try {
                val json = Files.readString(CONFIG_PATH)
                gson.fromJson(json, TelegramConfig::class.java) ?: TelegramConfig()
            } catch (e: Exception) {
                logger.warn("Failed to load config", e)
                TelegramConfig()
            }
        }

        fun save(config: TelegramConfig) {
            Files.writeString(CONFIG_PATH, gson.toJson(config))
        }
    }
}
