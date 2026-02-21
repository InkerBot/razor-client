package bot.inker.bc.razor.tui.command

import bot.inker.bc.razor.RazorClient
import bot.inker.bc.razor.state.CharacterState
import bot.inker.bc.razor.tui.TuiApplication
import bot.inker.bc.razor.tui.format.ChatFormatter
import bot.inker.bc.razor.tui.format.FormattedMessage
import org.slf4j.LoggerFactory

class CommandExecutor(
    private val app: TuiApplication,
    private val addMessage: (FormattedMessage) -> Unit,
) {
    private val logger = LoggerFactory.getLogger(CommandExecutor::class.java)

    fun execute(command: Command) {
        try {
            executeCommand(command)
        } catch (e: Exception) {
            logger.error("Error executing command: {}", command, e)
            addMessage(ChatFormatter.errorMessage("Command failed: ${e.message}"))
        }
    }

    private fun executeCommand(command: Command) {
        val client = app.client ?: return

        when (command) {
            is Command.Chat -> {
                client.chat.send(command.message)
            }

            is Command.Whisper -> {
                val target = resolveTarget(command.target, client)
                if (target == null) {
                    addMessage(ChatFormatter.errorMessage("Player not found: ${command.target}"))
                    return
                }
                client.chat.whisper(target.memberNumber, command.message)
                addMessage(ChatFormatter.helpMessage("[You -> ${target.displayName}] ${command.message}"))
            }

            is Command.Emote -> {
                client.chat.emote(command.text)
            }

            is Command.Leave -> {
                client.room.leave()
            }

            is Command.Rooms -> {
                client.room.leave()
            }

            is Command.Kick -> {
                val target = resolveTarget(command.target, client)
                if (target == null) {
                    addMessage(ChatFormatter.errorMessage("Player not found: ${command.target}"))
                    return
                }
                client.room.kick(target.memberNumber)
                addMessage(ChatFormatter.systemMessage("Kicked ${target.displayName}"))
            }

            is Command.Ban -> {
                val target = resolveTarget(command.target, client)
                if (target == null) {
                    addMessage(ChatFormatter.errorMessage("Player not found: ${command.target}"))
                    return
                }
                client.room.ban(target.memberNumber)
                addMessage(ChatFormatter.systemMessage("Banned ${target.displayName}"))
            }

            is Command.Help -> {
                addMessage(ChatFormatter.helpMessage("=== Commands ==="))
                addMessage(ChatFormatter.helpMessage("/w <target> <msg> - Whisper to player"))
                addMessage(ChatFormatter.helpMessage("/me <text> - Emote action"))
                addMessage(ChatFormatter.helpMessage("/leave - Leave current room"))
                addMessage(ChatFormatter.helpMessage("/rooms - Leave room and return to lobby"))
                addMessage(ChatFormatter.helpMessage("/kick <target> - Kick player (admin)"))
                addMessage(ChatFormatter.helpMessage("/ban <target> - Ban player (admin)"))
                addMessage(ChatFormatter.helpMessage("/quit - Exit program"))
                addMessage(ChatFormatter.helpMessage("Target: name or #12345"))
            }

            is Command.Quit -> {
                app.quit()
            }
        }
    }

    private fun resolveTarget(target: String, client: RazorClient): CharacterState? {
        // Try member number (#12345 or plain 12345)
        val numberStr = if (target.startsWith("#")) target.substring(1) else target
        val memberNumber = numberStr.toIntOrNull()
        if (memberNumber != null) {
            return client.room.characters.find { it.memberNumber == memberNumber }
        }

        // Try displayName then name
        return client.room.characters.find { it.displayName.equals(target, ignoreCase = true) }
            ?: client.room.characters.find { it.name.equals(target, ignoreCase = true) }
    }
}
