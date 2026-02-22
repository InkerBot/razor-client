package bot.inker.bc.razor.protocol.room

import com.google.gson.annotations.SerializedName

data class ChatRoomCreateRequest(
    @SerializedName("Name") val name: String,
    @SerializedName("Description") val description: String = "",
    @SerializedName("Background") val background: String = "MainHall",
    @SerializedName("Limit") val limit: Int = 10,
    @SerializedName("Admin") val admin: List<Int>,
    @SerializedName("Ban") val ban: List<Int> = emptyList(),
    @SerializedName("Whitelist") val whitelist: List<Int> = emptyList(),
    @SerializedName("Game") val game: String = "",
    @SerializedName("Visibility") val visibility: List<String> = listOf("All"),
    @SerializedName("Access") val access: List<String> = listOf("All"),
    @SerializedName("BlockCategory") val blockCategory: List<String> = emptyList(),
    @SerializedName("Language") val language: String = "",
    @SerializedName("Space") val space: String = "",
    @SerializedName("MapData") val mapData: ChatRoomMapData? = null,
    @SerializedName("Custom") val custom: ChatRoomCustomData? = null,
)
