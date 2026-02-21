package bot.inker.bc.razor.protocol.auth

sealed class PasswordResetResult {
    data object EmailSent : PasswordResetResult()
    data object PasswordResetSuccessful : PasswordResetResult()
    data class Error(val message: String) : PasswordResetResult()
}
