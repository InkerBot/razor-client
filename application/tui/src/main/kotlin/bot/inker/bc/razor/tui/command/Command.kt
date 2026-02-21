package bot.inker.bc.razor.tui.command

sealed class Command {
    data class Chat(val message: String) : Command()
    data class Whisper(val target: String, val message: String) : Command()
    data class Emote(val text: String) : Command()
    data object Leave : Command()
    data object Rooms : Command()
    data class Kick(val target: String) : Command()
    data class Ban(val target: String) : Command()
    data object Help : Command()
    data object Quit : Command()
}
