package bot.inker.bc.razor.protocol.auth

import com.google.gson.annotations.SerializedName

data class CreateAccountRequest(
    @SerializedName("Name") val name: String,
    @SerializedName("AccountName") val accountName: String,
    @SerializedName("Password") val password: String,
    @SerializedName("Email") val email: String,
)
