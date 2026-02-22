package bot.inker.bc.razor.telegram

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile

class TelegramMessageSender(
    private val config: TelegramConfig,
    private val telegramClient: OkHttpTelegramClient,
    private val topicManager: ForumTopicManager,
) {
    private val logger = LoggerFactory.getLogger(TelegramMessageSender::class.java)

    fun sendToGeneral(htmlText: String, replyToMessageId: Int? = null): Int? {
        try {
            val message = SendMessage.builder()
                .chatId(config.chatId)
                .text(htmlText)
                .parseMode("HTML")

            val generalTopicId = topicManager.getGeneralTopicId()
            if (generalTopicId != null) {
                message.messageThreadId(generalTopicId)
            }
            if (replyToMessageId != null) {
                message.replyToMessageId(replyToMessageId)
            }

            val result = telegramClient.execute(message.build())
            return result.messageId
        } catch (e: Exception) {
            logger.error("Failed to send message to General topic", e)
            return null
        }
    }

    fun sendToBeepTopic(member: MemberIdentity, htmlText: String, replyToMessageId: Int? = null): Int? {
        try {
            val threadId = topicManager.getOrCreateTopic(member)
            val message = SendMessage.builder()
                .chatId(config.chatId)
                .messageThreadId(threadId)
                .text(htmlText)
                .parseMode("HTML")
            if (replyToMessageId != null) {
                message.replyToMessageId(replyToMessageId)
            }

            val result = telegramClient.execute(message.build())
            return result.messageId
        } catch (e: Exception) {
            logger.error("Failed to send message to BEEP topic for {}", member, e)
            return null
        }
    }

    fun sendPhotoToGeneral(photoUrl: String, caption: String? = null, replyToMessageId: Int? = null): Int? {
        try {
            val photo = SendPhoto.builder()
                .chatId(config.chatId)
                .photo(InputFile(photoUrl))

            val generalTopicId = topicManager.getGeneralTopicId()
            if (generalTopicId != null) {
                photo.messageThreadId(generalTopicId)
            }
            if (caption != null) {
                photo.caption(caption)
                photo.parseMode("HTML")
            }
            if (replyToMessageId != null) {
                photo.replyToMessageId(replyToMessageId)
            }

            val result = telegramClient.execute(photo.build())
            return result.messageId
        } catch (e: Exception) {
            logger.error("Failed to send photo to General topic", e)
            return null
        }
    }

    fun sendPhotoToBeepTopic(member: MemberIdentity, photoUrl: String, caption: String? = null, replyToMessageId: Int? = null): Int? {
        try {
            val threadId = topicManager.getOrCreateTopic(member)
            val photo = SendPhoto.builder()
                .chatId(config.chatId)
                .messageThreadId(threadId)
                .photo(InputFile(photoUrl))
            if (caption != null) {
                photo.caption(caption)
                photo.parseMode("HTML")
            }
            if (replyToMessageId != null) {
                photo.replyToMessageId(replyToMessageId)
            }

            val result = telegramClient.execute(photo.build())
            return result.messageId
        } catch (e: Exception) {
            logger.error("Failed to send photo to BEEP topic for {}", member, e)
            return null
        }
    }
}
