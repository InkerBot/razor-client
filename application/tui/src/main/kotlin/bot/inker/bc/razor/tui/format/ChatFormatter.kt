package bot.inker.bc.razor.tui.format

import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import com.googlecode.lanterna.TextColor

data class FormattedMessage(
    val text: String,
    val color: TextColor,
)

object ChatFormatter {

    fun format(
        message: ReceivedChatMessage,
        senderName: String,
        targetName: String?,
        resolvedContent: String
    ): FormattedMessage {
        return when (message.type) {
            ChatMessageType.CHAT -> FormattedMessage(
                "[$senderName] $resolvedContent",
                TextColor.ANSI.DEFAULT,
            )

            ChatMessageType.WHISPER -> {
                val direction = if (targetName != null) "$senderName -> $targetName" else senderName
                FormattedMessage(
                    "[$direction] $resolvedContent",
                    TextColor.ANSI.MAGENTA,
                )
            }

            ChatMessageType.EMOTE -> FormattedMessage(
                "* $senderName $resolvedContent",
                TextColor.ANSI.YELLOW,
            )

            ChatMessageType.ACTION -> FormattedMessage(
                "($resolvedContent)",
                TextColor.ANSI.WHITE_BRIGHT,
            )

            ChatMessageType.ACTIVITY -> FormattedMessage(
                "($resolvedContent)",
                TextColor.ANSI.WHITE_BRIGHT,
            )

            ChatMessageType.SERVER_MESSAGE -> FormattedMessage(
                "[Server] $resolvedContent",
                TextColor.ANSI.RED,
            )

            ChatMessageType.STATUS -> FormattedMessage(
                "-- $resolvedContent --",
                TextColor.ANSI.WHITE_BRIGHT,
            )

            ChatMessageType.HIDDEN -> FormattedMessage(
                "[Hidden] $resolvedContent",
                TextColor.ANSI.WHITE_BRIGHT,
            )

            ChatMessageType.LOCAL_MESSAGE -> FormattedMessage(
                "[Local] $resolvedContent",
                TextColor.ANSI.CYAN,
            )
        }
    }

    fun systemMessage(text: String): FormattedMessage {
        return FormattedMessage("-- $text --", TextColor.ANSI.GREEN)
    }

    fun errorMessage(text: String): FormattedMessage {
        return FormattedMessage("[Error] $text", TextColor.ANSI.RED)
    }

    fun helpMessage(text: String): FormattedMessage {
        return FormattedMessage(text, TextColor.ANSI.CYAN)
    }
}
