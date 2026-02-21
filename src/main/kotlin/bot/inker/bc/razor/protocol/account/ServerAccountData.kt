package bot.inker.bc.razor.protocol.account

import bot.inker.bc.razor.protocol.common.ItemBundle
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ServerAccountData(
    @SerializedName("ID") val id: String,
    @SerializedName("MemberNumber") val memberNumber: Int,
    @SerializedName("Name") val name: String,
    @SerializedName("Nickname") val nickname: String? = null,
    @SerializedName("AccountName") val accountName: String? = null,
    @SerializedName("Creation") val creation: Long? = null,
    @SerializedName("Money") val money: Int? = null,
    @SerializedName("Owner") val owner: String? = null,
    @SerializedName("Lovership") val lovership: List<Lovership>? = null,
    @SerializedName("Ownership") val ownership: Ownership? = null,
    @SerializedName("Appearance") val appearance: List<ItemBundle>? = null,
    @SerializedName("Inventory") val inventory: JsonElement? = null,
    @SerializedName("InventoryData") val inventoryData: String? = null,
    @SerializedName("AllowedInteractions") val allowedInteractions: Int? = null,
    @SerializedName("BlockItems") val blockItems: JsonElement? = null,
    @SerializedName("LimitedItems") val limitedItems: JsonElement? = null,
    @SerializedName("FavoriteItems") val favoriteItems: JsonElement? = null,
    @SerializedName("HiddenItems") val hiddenItems: JsonElement? = null,
    @SerializedName("FriendList") val friendList: List<Int>? = null,
    @SerializedName("WhiteList") val whiteList: List<Int>? = null,
    @SerializedName("BlackList") val blackList: List<Int>? = null,
    @SerializedName("GhostList") val ghostList: List<Int>? = null,
    @SerializedName("FriendNames") val friendNames: String? = null,
    @SerializedName("SubmissivesList") val submissivesList: String? = null,
    @SerializedName("ChatSettings") val chatSettings: JsonElement? = null,
    @SerializedName("VisualSettings") val visualSettings: JsonElement? = null,
    @SerializedName("AudioSettings") val audioSettings: JsonElement? = null,
    @SerializedName("GameplaySettings") val gameplaySettings: JsonElement? = null,
    @SerializedName("ArousalSettings") val arousalSettings: JsonElement? = null,
    @SerializedName("Skill") val skill: List<Skill>? = null,
    @SerializedName("Reputation") val reputation: List<Reputation>? = null,
    @SerializedName("Difficulty") val difficulty: Difficulty? = null,
    @SerializedName("LastChatRoom") val lastChatRoom: JsonElement? = null,
    @SerializedName("MapData") val mapData: JsonElement? = null,
    @SerializedName("ActivePose") val activePose: List<String>? = null,
    @SerializedName("PrivateCharacter") val privateCharacter: JsonElement? = null,
    @SerializedName("Crafting") val crafting: JsonElement? = null,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("LabelColor") val labelColor: String? = null,
    @SerializedName("Description") val description: String? = null,
)

data class Skill(
    @SerializedName("Type") val type: String,
    @SerializedName("Level") val level: Int,
    @SerializedName("Progress") val progress: Double? = null,
)

data class Reputation(
    @SerializedName("Type") val type: String,
    @SerializedName("Value") val value: Int,
)

data class Difficulty(
    @SerializedName("Level") val level: Int,
    @SerializedName("LastChange") val lastChange: Long? = null,
)
