package bot.inker.bc.razor.protocol.room

sealed class RoomCreateResult {
    data object Success : RoomCreateResult()
    data class Error(val message: String) : RoomCreateResult()
}
