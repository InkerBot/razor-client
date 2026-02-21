package bot.inker.bc.razor.state

import bot.inker.bc.razor.protocol.room.*
import bot.inker.bc.razor.protocol.sync.RoomSyncMessage
import com.google.gson.JsonElement

data class RoomState(
    val name: String,
    val description: String,
    val admin: List<Int>,
    val ban: List<Int>,
    val whitelist: List<Int>,
    val background: String,
    val limit: Int,
    val locked: Boolean,
    val game: ChatRoomGame,
    val visibility: List<ChatRoomAccessRole>,
    val access: List<ChatRoomAccessRole>,
    val blockCategory: List<ChatRoomBlockCategory>,
    val language: ChatRoomLanguage,
    val space: ChatRoomSpace,
    val mapData: JsonElement?,
    val custom: ChatRoomCustomData?,
    val characters: List<CharacterState> = emptyList(),
) {
    companion object {
        fun fromSyncMessage(msg: RoomSyncMessage): RoomState {
            return RoomState(
                name = msg.name,
                description = msg.description ?: "",
                admin = msg.admin ?: emptyList(),
                ban = msg.ban ?: emptyList(),
                whitelist = msg.whitelist ?: emptyList(),
                background = msg.background ?: "MainHall",
                limit = msg.limit ?: 10,
                locked = msg.locked ?: false,
                game = ChatRoomGame.fromWireValue(msg.game ?: ""),
                visibility = msg.visibility?.mapNotNull { ChatRoomAccessRole.fromWireValue(it) } ?: emptyList(),
                access = msg.access?.mapNotNull { ChatRoomAccessRole.fromWireValue(it) } ?: emptyList(),
                blockCategory = msg.blockCategory?.mapNotNull { ChatRoomBlockCategory.fromWireValue(it) }
                    ?: emptyList(),
                language = ChatRoomLanguage.fromWireValue(msg.language ?: ""),
                space = ChatRoomSpace.fromWireValue(msg.space ?: ""),
                mapData = msg.mapData,
                custom = msg.custom,
            )
        }
    }

    fun findCharacter(memberNumber: Int): CharacterState? {
        return characters.find { it.memberNumber == memberNumber }
    }

    fun withCharacterRemoved(memberNumber: Int): RoomState {
        return copy(characters = characters.filter { it.memberNumber != memberNumber })
    }

    fun withCharacterUpdated(memberNumber: Int, update: (CharacterState) -> CharacterState): RoomState {
        val index = characters.indexOfFirst { it.memberNumber == memberNumber }
        if (index == -1) return this
        return copy(characters = characters.toMutableList().also { it[index] = update(it[index]) })
    }
}
