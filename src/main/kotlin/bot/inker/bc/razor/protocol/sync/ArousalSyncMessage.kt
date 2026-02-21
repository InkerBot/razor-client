package bot.inker.bc.razor.protocol.sync

import com.google.gson.annotations.SerializedName

data class ArousalSyncMessage(
    @SerializedName("MemberNumber") val memberNumber: Int,
    @SerializedName("OrgasmTimer") val orgasmTimer: Long? = null,
    @SerializedName("OrgasmCount") val orgasmCount: Int? = null,
    @SerializedName("Progress") val progress: Double? = null,
    @SerializedName("ProgressTimer") val progressTimer: Long? = null,
)
