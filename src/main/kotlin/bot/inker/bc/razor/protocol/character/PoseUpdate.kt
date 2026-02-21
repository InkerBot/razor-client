package bot.inker.bc.razor.protocol.character

import com.google.gson.annotations.SerializedName

data class PoseUpdate(
    @SerializedName("Pose") val pose: List<String>,
)
