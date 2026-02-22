package bot.inker.bc.razor.telegram

import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update

class TelegramBot(
    private val botToken: String,
    private val telegramEventBridge: TelegramEventBridge,
) : LongPollingSingleThreadUpdateConsumer {

    fun getBotToken(): String = botToken

    override fun consume(update: Update) {
        telegramEventBridge.onUpdate(update)
    }
}
