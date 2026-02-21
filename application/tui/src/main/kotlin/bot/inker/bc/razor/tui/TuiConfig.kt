package bot.inker.bc.razor.tui

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path

data class TuiConfig(
    val serverUrl: String = "https://bondage-club-server.herokuapp.com/",
    val origin: String = "https://www.bondageprojects.elementfx.com",
    val referer: String = "https://www.bondageprojects.elementfx.com/R124/BondageClub/",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val lastUsername: String = "",
    val colorScheme: ColorScheme = ColorScheme(),
    val disableShadows: Boolean? = null,
) {
    companion object {
        private val CONFIG_PATH: Path = Path.of("razor-tui.json")
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(): TuiConfig {
            if (!Files.exists(CONFIG_PATH)) {
                return TuiConfig()
            }
            return try {
                val json = Files.readString(CONFIG_PATH)
                gson.fromJson(json, TuiConfig::class.java) ?: TuiConfig()
            } catch (e: Exception) {
                TuiConfig()
            }
        }

        fun save(config: TuiConfig) {
            Files.writeString(CONFIG_PATH, gson.toJson(config))
        }
    }
}
