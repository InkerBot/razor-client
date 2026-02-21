package bot.inker.bc.razor.protocol.character

import bot.inker.bc.razor.protocol.common.ItemBundle
import com.google.gson.annotations.SerializedName

data class ExpressionUpdate(
    @SerializedName("Name") val name: String,
    @SerializedName("Group") val group: String,
    @SerializedName("Appearance") val appearance: List<ItemBundle>,
)
