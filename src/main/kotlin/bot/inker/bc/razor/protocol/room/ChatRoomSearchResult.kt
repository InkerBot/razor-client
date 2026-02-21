package bot.inker.bc.razor.protocol.room

import com.google.gson.annotations.SerializedName

data class ChatRoomSearchResult(
    @SerializedName("Name") val name: String,
    @SerializedName("Description") val description: String? = null,
    @SerializedName("MemberCount") val memberCount: Int? = null,
    @SerializedName("MemberLimit") val memberLimit: Int? = null,
    @SerializedName("Creator") val creator: String? = null,
    @SerializedName("CreatorMemberNumber") val creatorMemberNumber: Int? = null,
    @SerializedName("Game") val game: String? = null,
    @SerializedName("Friends") val friends: List<FriendEntry>? = null,
    @SerializedName("BlockCategory") val blockCategory: List<String>? = null,
    @SerializedName("Language") val language: String? = null,
    @SerializedName("Space") val space: String? = null,
    @SerializedName("Locked") val locked: Boolean? = null,
    @SerializedName("MapType") val mapType: String? = null,
    @SerializedName("Order") val order: Double? = null,
)

data class FriendEntry(
    @SerializedName("MemberName") val memberName: String,
    @SerializedName("MemberNumber") val memberNumber: Int,
    @SerializedName("Type") val type: String? = null,
)
