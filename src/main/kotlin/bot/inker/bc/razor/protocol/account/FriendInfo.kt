package bot.inker.bc.razor.protocol.account

import com.google.gson.annotations.SerializedName

data class FriendInfo(
    @SerializedName("MemberNumber") val memberNumber: Int,
    @SerializedName("MemberName") val memberName: String? = null,
    @SerializedName("ChatRoomName") val chatRoomName: String? = null,
    @SerializedName("ChatRoomSpace") val chatRoomSpace: String? = null,
    @SerializedName("Private") val private_: Boolean? = null,
    @SerializedName("Type") val type: String? = null,
)
