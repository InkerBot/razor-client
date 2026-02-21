package bot.inker.bc.razor.protocol.room

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ChatRoomMapData(
    @SerializedName("Type") val type: String? = null,
    @SerializedName("Tiles") val tiles: JsonElement? = null,
    @SerializedName("Objects") val objects: JsonElement? = null,
)
