package bot.inker.bc.razor.protocol.chat

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class GameResponseData(
    @SerializedName("Sender") val sender: Int,
    @SerializedName("Data") val data: JsonElement? = null,
)
