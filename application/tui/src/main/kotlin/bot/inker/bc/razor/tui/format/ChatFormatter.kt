package bot.inker.bc.razor.tui.format

import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import bot.inker.bc.razor.tui.ColorScheme
import com.googlecode.lanterna.TextColor

data class FormattedMessage(
    val text: String,
    val color: TextColor,
)

class ChatFormatter(private val scheme: ColorScheme) {

    fun format(
        message: ReceivedChatMessage,
        senderName: String,
        targetName: String?,
        resolvedContent: String
    ): FormattedMessage {
        return when (message.type) {
            ChatMessageType.CHAT -> FormattedMessage(
                "[$senderName] $resolvedContent",
                ColorScheme.resolve(scheme.chatMessage),
            )

            ChatMessageType.WHISPER -> {
                val direction = if (targetName != null) "$senderName -> $targetName" else senderName
                FormattedMessage(
                    "[$direction] $resolvedContent",
                    ColorScheme.resolve(scheme.whisperMessage),
                )
            }

            ChatMessageType.EMOTE -> FormattedMessage(
                "* $senderName $resolvedContent",
                ColorScheme.resolve(scheme.emoteMessage),
            )

            ChatMessageType.ACTION -> FormattedMessage(
                "($resolvedContent)",
                ColorScheme.resolve(scheme.actionMessage),
            )

            ChatMessageType.ACTIVITY -> FormattedMessage(
                "($resolvedContent)",
                ColorScheme.resolve(scheme.activityMessage),
            )

            ChatMessageType.SERVER_MESSAGE -> FormattedMessage(
                "[Server] $resolvedContent",
                ColorScheme.resolve(scheme.serverMessage),
            )

            ChatMessageType.STATUS -> FormattedMessage(
                "-- $resolvedContent --",
                ColorScheme.resolve(scheme.statusMessage),
            )

            ChatMessageType.HIDDEN -> FormattedMessage(
                "[Hidden] $resolvedContent",
                ColorScheme.resolve(scheme.hiddenMessage),
            )

            ChatMessageType.LOCAL_MESSAGE -> FormattedMessage(
                "[Local] $resolvedContent",
                ColorScheme.resolve(scheme.localMessage),
            )
        }
    }

    fun systemMessage(text: String): FormattedMessage {
        return FormattedMessage("-- $text --", ColorScheme.resolve(scheme.systemMessage))
    }

    fun errorMessage(text: String): FormattedMessage {
        return FormattedMessage("[Error] $text", ColorScheme.resolve(scheme.errorMessage))
    }

    fun helpMessage(text: String): FormattedMessage {
        return FormattedMessage(text, ColorScheme.resolve(scheme.helpMessage))
    }
}
