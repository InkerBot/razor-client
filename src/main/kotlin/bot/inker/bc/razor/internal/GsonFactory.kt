package bot.inker.bc.razor.internal

import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.common.ForceDisconnectReason
import com.google.gson.*
import java.lang.reflect.Type

internal object GsonFactory {
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ChatMessageType::class.java, ChatMessageTypeAdapter())
        .registerTypeAdapter(ForceDisconnectReason::class.java, ForceDisconnectReasonAdapter())
        .create()

    private class ChatMessageTypeAdapter : JsonSerializer<ChatMessageType>, JsonDeserializer<ChatMessageType> {
        override fun serialize(src: ChatMessageType, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            return JsonPrimitive(src.wireValue)
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): ChatMessageType {
            return ChatMessageType.fromWireValue(json.asString)
        }
    }

    private class ForceDisconnectReasonAdapter : JsonSerializer<ForceDisconnectReason>,
        JsonDeserializer<ForceDisconnectReason> {
        override fun serialize(
            src: ForceDisconnectReason,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            return JsonPrimitive(src.wireValue)
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): ForceDisconnectReason {
            return ForceDisconnectReason.fromWireValue(json.asString)
        }
    }
}
