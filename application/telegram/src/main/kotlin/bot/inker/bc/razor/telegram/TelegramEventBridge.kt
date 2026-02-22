package bot.inker.bc.razor.telegram

import bot.inker.bc.razor.RazorClient
import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.DictionaryEntry
import bot.inker.bc.razor.protocol.chat.OutgoingChatMessage
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.UUID

class TelegramEventBridge(
    private val config: TelegramConfig,
    private val topicManager: ForumTopicManager,
    private val messageIdMapping: MessageIdMapping,
    private val commandHandler: BotCommandHandler? = null,
    private val mediaDownloader: TelegramMediaDownloader? = null,
) {
    private val logger = LoggerFactory.getLogger(TelegramEventBridge::class.java)

    @Volatile
    var client: RazorClient? = null

    fun onUpdate(update: Update) {
        if (!update.hasMessage()) return
        val message = update.message ?: return

        // Skip bot messages
        if (message.from?.isBot == true) return

        val tgUsername = message.from?.firstName ?: message.from?.userName ?: "Unknown"
        val threadId = message.messageThreadId

        val currentClient = client
        if (currentClient == null) {
            logger.warn("Received TG message but BC client is not connected")
            return
        }

        try {
            if (message.hasText()) {
                val text = message.text ?: return

                // Intercept bot commands from TG
                if (commandHandler != null && commandHandler.isCommand(text)) {
                    commandHandler.handleCommand(text)
                    return
                }

                if (topicManager.isGeneralTopic(threadId)) {
                    // General topic -> BC chat
                    sendToBcChat(currentClient, tgUsername, MessageFormatter.formatTgToBcChat(tgUsername, text), message.messageId, message.replyToMessage?.messageId)
                } else if (threadId != null) {
                    // BEEP topic -> BC beep
                    sendToBcBeep(currentClient, threadId, text)
                }
            } else {
                // Non-text message (photo, sticker, video, etc.)
                if (mediaDownloader == null) {
                    logger.debug("Received non-text message but media server is not enabled, skipping")
                    return
                }
                val media = mediaDownloader.extractAndDownload(message)
                if (media == null) {
                    logger.debug("Unsupported media type or download failed, skipping")
                    return
                }
                val bcMessage = MessageFormatter.formatTgMediaToBcChat(tgUsername, media)

                if (topicManager.isGeneralTopic(threadId)) {
                    sendToBcChat(currentClient, tgUsername, bcMessage, message.messageId, message.replyToMessage?.messageId)
                } else if (threadId != null) {
                    sendToBcBeep(currentClient, threadId, bcMessage)
                }
            }
        } catch (e: Exception) {
            logger.error("Error routing TG message to BC", e)
        }
    }

    private fun sendToBcChat(client: RazorClient, tgUsername: String, bcMessage: String, tgMessageId: Int, tgReplyToId: Int?) {
        val bcMsgId = UUID.randomUUID().toString()
        val dictionary = mutableListOf(DictionaryEntry.msgId(bcMsgId))

        if (tgReplyToId != null) {
            val bcReplyId = messageIdMapping.getBcMsgId(tgReplyToId)
            if (bcReplyId != null) {
                dictionary.add(DictionaryEntry.replyId(bcReplyId))
            }
        }

        client.chat.sendMessage(
            OutgoingChatMessage(
                content = bcMessage,
                type = ChatMessageType.CHAT,
                dictionary = dictionary,
            )
        )
        messageIdMapping.put(bcMsgId, tgMessageId)
        logger.debug("TG->BC chat: {}", bcMessage)
    }

    private fun sendToBcBeep(client: RazorClient, threadId: Int, text: String) {
        val member = topicManager.lookupMember(threadId)
        if (member != null) {
            client.social.beep(member.memberNumber, text)
            logger.debug("TG->BC beep to {}: {}", member, text)
        } else {
            logger.debug("Ignoring message from unknown topic threadId={}", threadId)
        }
    }
}
