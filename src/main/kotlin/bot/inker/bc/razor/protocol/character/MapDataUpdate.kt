package bot.inker.bc.razor.protocol.character

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class MapDataUpdate(
    @SerializedName("Pos") val pos: MapPosition,
    @SerializedName("PrivateState") val privateState: JsonElement? = null,
)

data class MapPosition(
    @SerializedName("X") val x: Int,
    @SerializedName("Y") val y: Int,
)
