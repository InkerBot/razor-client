package bot.inker.bc.razor.telegram

import bot.inker.bc.razor.protocol.chat.ChatMessageType

object MessageFormatter {

    fun formatChatMessage(type: ChatMessageType, senderName: String, senderNumber: Int, content: String): String {
        val sender = "${escapeHtml(senderName)}(${senderNumber})"
        return when (type) {
            ChatMessageType.CHAT -> "<b>$sender</b>: ${escapeHtml(content)}"
            ChatMessageType.EMOTE -> "<i>* $sender ${escapeHtml(content)}</i>"
            ChatMessageType.WHISPER -> "<b>$sender</b> (whisper): ${escapeHtml(content)}"
            ChatMessageType.ACTION,
            ChatMessageType.ACTIVITY -> "<i>($sender ${escapeHtml(content)})</i>"
            ChatMessageType.SERVER_MESSAGE -> "\uD83D\uDCE2 ${escapeHtml(content)}"
            else -> escapeHtml(content)
        }
    }

    fun formatBeepReceived(fromName: String, fromNumber: Int, message: String?): String {
        val sender = "${escapeHtml(fromName)}(${fromNumber})"
        return if (message != null) {
            "<b>$sender</b>: ${escapeHtml(message)}"
        } else {
            "<b>$sender</b> sent a beep"
        }
    }

    fun formatSystemMessage(text: String): String {
        return escapeHtml(text)
    }

    fun formatTgToBcChat(tgUsername: String, content: String): String {
        return "[$tgUsername] $content"
    }

    fun formatTgMediaToBcChat(tgUsername: String, media: DownloadedMedia): String {
        val tag = when {
            media.mediaType is MediaType.Document && media.originalFilename != null ->
                "[${media.mediaType.label} (${media.originalFilename})]"
            else ->
                "[${media.mediaType.label}]"
        }
        val caption = media.caption?.let { " $it" } ?: ""
        return "[$tgUsername] $tag ${media.publicUrl}$caption"
    }

    fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
