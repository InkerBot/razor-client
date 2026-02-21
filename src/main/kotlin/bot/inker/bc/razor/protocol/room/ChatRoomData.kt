package bot.inker.bc.razor.protocol.room

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ChatRoomData(
    @SerializedName("Name") val name: String,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("Admin") val admin: List<Int>? = null,
    @SerializedName("Ban") val ban: List<Int>? = null,
    @SerializedName("Whitelist") val whitelist: List<Int>? = null,
    @SerializedName("Background") val background: String? = null,
    @SerializedName("Limit") val limit: Int? = null,
    @SerializedName("Game") val game: String? = null,
    @SerializedName("Locked") val locked: Boolean? = null,
    @SerializedName("Visibility") val visibility: List<String>? = null,
    @SerializedName("Access") val access: List<String>? = null,
    @SerializedName("BlockCategory") val blockCategory: List<String>? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("Space") val space: String? = null,
    @SerializedName("MapData") val mapData: JsonElement? = null,
    @SerializedName("Custom") val custom: ChatRoomCustomData? = null,
    @SerializedName("Character") val character: List<JsonElement>? = null,
    @SerializedName("SourceMemberNumber") val sourceMemberNumber: Int? = null,
)
