package bot.inker.bc.razor.protocol.room

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ChatRoomAdminRequest(
    @SerializedName("Action") val action: String,
    @SerializedName("MemberNumber") val memberNumber: Int? = null,
    @SerializedName("Publish") val publish: Boolean? = null,
    @SerializedName("Room") val room: JsonElement? = null,
)
