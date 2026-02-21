package bot.inker.bc.razor.protocol.account

import bot.inker.bc.razor.OmittableProperty
import bot.inker.bc.razor.internal.LZString
import bot.inker.bc.razor.protocol.common.ItemBundle
import com.google.gson.JsonElement

@DslMarker
annotation class AccountUpdateDsl

@AccountUpdateDsl
class AccountUpdateScope @PublishedApi internal constructor() {
    @PublishedApi
    internal val fields = LinkedHashMap<String, Any?>()

    private fun <T> field(wireName: String) =
        OmittableProperty<T>(fields, wireName)

    private fun <T> lzField(wireName: String) =
        OmittableProperty<T>(fields, wireName) { value ->
            (value as? String)?.let { LZString.compressToUTF16(it) }
        }


    var money: Int? by field("Money")
    var owner: String? by field("Owner")
    var title: String? by field("Title")
    var labelColor: String? by field("LabelColor")
    var description: String? by field("Description")
    var allowedInteractions: Int? by field("AllowedInteractions")


    var friendList: List<Int>? by field("FriendList")
    var whiteList: List<Int>? by field("WhiteList")
    var blackList: List<Int>? by field("BlackList")
    var ghostList: List<Int>? by field("GhostList")
    var friendNames: String? by lzField("FriendNames")
    var submissivesList: String? by lzField("SubmissivesList")


    var appearance: List<ItemBundle>? by field("Appearance")
    var activePose: List<String>? by field("ActivePose")
    var ownership: Ownership? by field("Ownership")
    var lovership: List<Lovership>? by field("Lovership")


    var skill: List<Skill>? by field("Skill")
    var reputation: List<Reputation>? by field("Reputation")
    var difficulty: Difficulty? by field("Difficulty")


    var chatSettings: JsonElement? by field("ChatSettings")
    var visualSettings: JsonElement? by field("VisualSettings")
    var audioSettings: JsonElement? by field("AudioSettings")
    var gameplaySettings: JsonElement? by field("GameplaySettings")
    var arousalSettings: JsonElement? by field("ArousalSettings")


    var inventory: JsonElement? by field("Inventory")
    var inventoryData: String? by field("InventoryData")
    var blockItems: JsonElement? by field("BlockItems")
    var limitedItems: JsonElement? by field("LimitedItems")
    var favoriteItems: JsonElement? by field("FavoriteItems")
    var hiddenItems: JsonElement? by field("HiddenItems")


    var lastChatRoom: JsonElement? by field("LastChatRoom")
    var mapData: JsonElement? by field("MapData")
    var privateCharacter: JsonElement? by field("PrivateCharacter")
    var crafting: JsonElement? by field("Crafting")

    fun setRaw(wireName: String, value: Any?) {
        fields[wireName] = value
    }
}
