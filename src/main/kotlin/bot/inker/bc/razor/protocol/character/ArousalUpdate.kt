package bot.inker.bc.razor.protocol.character

import com.google.gson.annotations.SerializedName

data class ArousalUpdate(
    @SerializedName("OrgasmTimer") val orgasmTimer: Long? = null,
    @SerializedName("OrgasmCount") val orgasmCount: Int? = null,
    @SerializedName("Progress") val progress: Double? = null,
    @SerializedName("ProgressTimer") val progressTimer: Long? = null,
)
