package bot.inker.bc.razor.tui.command

object CommandParser {

    fun parse(input: String): Command {
        if (!input.startsWith("/")) {
            return Command.Chat(input)
        }

        val parts = input.substring(1).split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val args = if (parts.size > 1) parts[1] else ""

        return when (cmd) {
            "w", "whisper" -> {
                val whisperParts = args.split(" ", limit = 2)
                if (whisperParts.size < 2 || whisperParts[0].isBlank()) {
                    Command.Chat(input) // malformed, treat as chat
                } else {
                    Command.Whisper(whisperParts[0], whisperParts[1])
                }
            }
            "me", "emote" -> {
                if (args.isBlank()) Command.Chat(input) else Command.Emote(args)
            }
            "leave" -> Command.Leave
            "rooms" -> Command.Rooms
            "kick" -> {
                if (args.isBlank()) Command.Chat(input) else Command.Kick(args.trim())
            }
            "ban" -> {
                if (args.isBlank()) Command.Chat(input) else Command.Ban(args.trim())
            }
            "help" -> Command.Help
            "quit", "exit" -> Command.Quit
            else -> Command.Chat(input) // unknown command, treat as chat
        }
    }
}
