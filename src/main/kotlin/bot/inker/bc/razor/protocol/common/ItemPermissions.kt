package bot.inker.bc.razor.protocol.common

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class ItemPermissions(
    @SerializedName("BlockItems") val blockItems: JsonElement? = null,
    @SerializedName("LimitedItems") val limitedItems: JsonElement? = null,
    @SerializedName("FavoriteItems") val favoriteItems: JsonElement? = null,
    @SerializedName("HiddenItems") val hiddenItems: List<HiddenItem>? = null,
)

data class HiddenItem(
    @SerializedName("Name") val name: String,
    @SerializedName("Group") val group: String,
)
