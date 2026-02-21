package bot.inker.bc.razor.protocol.auth

import com.google.gson.annotations.SerializedName

data class PasswordResetRequest(
    @SerializedName("AccountName") val accountName: String? = null,
    @SerializedName("ResetNumber") val resetNumber: String? = null,
    @SerializedName("NewPassword") val newPassword: String? = null,
)
