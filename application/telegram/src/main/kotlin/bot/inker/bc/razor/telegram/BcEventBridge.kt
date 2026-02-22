package bot.inker.bc.razor.telegram

import bot.inker.bc.razor.RazorClient
import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.event.Subscription
import bot.inker.bc.razor.i18n.ChatMessageResolver
import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import org.slf4j.LoggerFactory

class BcEventBridge(
    private val messageSender: TelegramMessageSender,
    private val messageResolver: ChatMessageResolver,
    private val messageIdMapping: MessageIdMapping,
    private val commandHandler: BotCommandHandler? = null,
) {
    companion object {
        private val IMAGE_URL_REGEX = Regex("""\bhttps?://\S+/[^/]+\.(png|jpe?g|webp|gif)\b""", RegexOption.IGNORE_CASE)
    }

    private val logger = LoggerFactory.getLogger(BcEventBridge::class.java)
    private var subscription: Subscription? = null

    fun attach(client: RazorClient) {
        detach()
        subscription = client.onAny { event ->
            try {
                handleEvent(client, event)
            } catch (e: Exception) {
                logger.error("Error handling BC event: {}", event::class.simpleName, e)
            }
        }
    }

    fun detach() {
        subscription?.cancel()
        subscription = null
    }

    private fun handleEvent(client: RazorClient, event: RazorEvent) {
        when (event) {
            is RazorEvent.ChatMessage -> handleChatMessage(client, event)
            is RazorEvent.BeepReceived -> handleBeepReceived(event)
            is RazorEvent.MemberJoined -> {}
            is RazorEvent.MemberLeft -> {}

            is RazorEvent.RoomJoined -> {
                messageSender.sendToGeneral(MessageFormatter.formatSystemMessage("Joined room: ${event.room.name}"))
            }

            is RazorEvent.RoomLeft -> {
                messageSender.sendToGeneral(MessageFormatter.formatSystemMessage("Left room"))
            }

            is RazorEvent.Connected -> {
                messageSender.sendToGeneral(MessageFormatter.formatSystemMessage("Connected to server"))
            }

            is RazorEvent.Disconnected -> {
                messageSender.sendToGeneral(
                    MessageFormatter.formatSystemMessage("Disconnected: ${event.reason ?: "Unknown"}")
                )
            }

            else -> {}
        }
    }

    private fun handleChatMessage(client: RazorClient, event: RazorEvent.ChatMessage) {
        val msg = event.message
        if (msg.type == ChatMessageType.HIDDEN || msg.type == ChatMessageType.STATUS) return

        // Skip messages from self to prevent echo
        val myMemberNumber = client.player?.memberNumber
        if (myMemberNumber != null && msg.sender == myMemberNumber) return

        val characters = client.room.characters
        val senderName = characters
            .find { it.memberNumber == msg.sender }?.displayName
            ?: "#${msg.sender}"

        val resolvedContent = messageResolver.resolve(msg) { memberNumber ->
            characters.find { it.memberNumber == memberNumber }
        }

        // Intercept bot commands from BC chat (whisper only)
        if (msg.type == ChatMessageType.WHISPER && commandHandler != null && commandHandler.isCommand(resolvedContent)) {
            commandHandler.handleCommand(resolvedContent)
            return
        }

        val bcMsgId = extractDictionaryField(msg, "MsgId", "MsgId")
        val bcReplyId = extractDictionaryField(msg, "ReplyId", "ReplyId")
        val tgReplyId = bcReplyId?.let { messageIdMapping.getTgMsgId(it) }

        when (msg.type) {
            ChatMessageType.WHISPER -> {
                val member = MemberIdentity(senderName, msg.sender)
                val formatted = MessageFormatter.formatChatMessage(msg.type, senderName, msg.sender, resolvedContent)
                val tgMsgId = messageSender.sendToBeepTopic(member, formatted, tgReplyId)
                if (bcMsgId != null && tgMsgId != null) {
                    messageIdMapping.put(bcMsgId, tgMsgId)
                }
            }

            ChatMessageType.CHAT,
            ChatMessageType.EMOTE,
            ChatMessageType.ACTION,
            ChatMessageType.ACTIVITY,
            ChatMessageType.SERVER_MESSAGE -> {
                val formatted = MessageFormatter.formatChatMessage(msg.type, senderName, msg.sender, resolvedContent)
                val tgMsgId = trySendAsPhoto(resolvedContent, formatted, tgReplyId)
                    ?: messageSender.sendToGeneral(formatted, tgReplyId)
                if (bcMsgId != null && tgMsgId != null) {
                    messageIdMapping.put(bcMsgId, tgMsgId)
                }
            }

            else -> {}
        }
    }

    private fun trySendAsPhoto(content: String, caption: String, replyToMessageId: Int?): Int? {
        val match = IMAGE_URL_REGEX.find(content) ?: return null
        val imageUrl = match.value
        return try {
            messageSender.sendPhotoToGeneral(imageUrl, caption, replyToMessageId)
        } catch (e: Exception) {
            logger.debug("Failed to send as photo, falling back to text: {}", e.message)
            null
        }
    }

    private fun extractDictionaryField(msg: ReceivedChatMessage, tag: String, field: String): String? {
        val dict = msg.dictionary
        if (dict == null || !dict.isJsonArray) return null
        val array = dict.asJsonArray
        for (entry in array) {
            if (!entry.isJsonObject) continue
            val obj = entry.asJsonObject
            if (obj.has("Tag") && obj.get("Tag").asString == tag && obj.has(field)) {
                return obj.get(field).asString
            }
        }
        return null
    }

    private fun handleBeepReceived(event: RazorEvent.BeepReceived) {
        val beep = event.beep
        val fromName = beep.memberName ?: "#${beep.memberNumber}"
        val member = MemberIdentity(fromName, beep.memberNumber)
        val formatted = MessageFormatter.formatBeepReceived(fromName, beep.memberNumber, beep.message)
        messageSender.sendToBeepTopic(member, formatted)
    }
}
