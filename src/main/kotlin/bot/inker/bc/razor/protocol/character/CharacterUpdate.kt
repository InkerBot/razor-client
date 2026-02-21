package bot.inker.bc.razor.protocol.character

import bot.inker.bc.razor.protocol.common.ItemBundle
import com.google.gson.annotations.SerializedName

data class CharacterUpdate(
    @SerializedName("ID") val id: String,
    @SerializedName("ActivePose") val activePose: List<String>? = null,
    @SerializedName("Appearance") val appearance: List<ItemBundle>,
)
