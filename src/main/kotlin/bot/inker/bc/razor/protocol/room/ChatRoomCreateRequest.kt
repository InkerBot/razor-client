package bot.inker.bc.razor.protocol.room

import com.google.gson.annotations.SerializedName

data class ChatRoomCreateRequest(
    @SerializedName("Name") val name: String,
    @SerializedName("Description") val description: String = "",
    @SerializedName("Background") val background: String = "MainHall",
    @SerializedName("Limit") val limit: Int = 10,
    @SerializedName("Admin") val admin: List<Int>,
    @SerializedName("Ban") val ban: List<Int>? = null,
    @SerializedName("Whitelist") val whitelist: List<Int>? = null,
    @SerializedName("Locked") val locked: Boolean? = null,
    @SerializedName("Game") val game: String? = null,
    @SerializedName("Visibility") val visibility: List<String>? = null,
    @SerializedName("Access") val access: List<String>? = null,
    @SerializedName("BlockCategory") val blockCategory: List<String>? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("Space") val space: String? = null,
    @SerializedName("MapData") val mapData: ChatRoomMapData? = null,
    @SerializedName("Custom") val custom: ChatRoomCustomData? = null,
)
