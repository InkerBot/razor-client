package bot.inker.bc.razor.protocol.auth

sealed class CreateAccountResult {
    data object Success : CreateAccountResult()
    data class Error(val message: String) : CreateAccountResult()
}
