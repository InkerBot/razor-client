package bot.inker.bc.razor.event

import bot.inker.bc.razor.protocol.account.BeepMessage
import bot.inker.bc.razor.protocol.chat.GameResponseData
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import bot.inker.bc.razor.protocol.common.ForceDisconnectReason
import bot.inker.bc.razor.protocol.sync.ArousalSyncMessage
import bot.inker.bc.razor.protocol.sync.ItemSyncMessage
import bot.inker.bc.razor.state.CharacterState
import bot.inker.bc.razor.state.RoomState

sealed interface RazorEvent {

    data object Connected : RazorEvent
    data class Disconnected(val reason: String?) : RazorEvent
    data class Reconnecting(val attemptNumber: Int) : RazorEvent
    data class ForceDisconnect(val reason: ForceDisconnectReason) : RazorEvent


    data class ServerInfo(val onlinePlayers: Int, val serverTime: Long) : RazorEvent
    data class LoginQueue(val position: Int) : RazorEvent


    data class RoomJoined(val room: RoomState) : RazorEvent
    data object RoomLeft : RazorEvent
    data class RoomUpdated(val room: RoomState) : RazorEvent
    data class MemberJoined(val character: CharacterState) : RazorEvent
    data class MemberLeft(val memberNumber: Int) : RazorEvent


    data class ChatMessage(val message: ReceivedChatMessage) : RazorEvent
    data class GameResponse(val response: GameResponseData) : RazorEvent


    data class CharacterUpdated(val memberNumber: Int, val character: CharacterState) : RazorEvent
    data class ExpressionChanged(val memberNumber: Int, val name: String?, val group: String) : RazorEvent
    data class PoseChanged(val memberNumber: Int, val poses: List<String>) : RazorEvent
    data class ArousalChanged(val memberNumber: Int, val arousal: ArousalSyncMessage) : RazorEvent
    data class ItemChanged(val source: Int, val item: ItemSyncMessage) : RazorEvent
    data class MapDataChanged(val memberNumber: Int) : RazorEvent
    data class PlayersReordered(val order: List<Int>) : RazorEvent


    data class BeepReceived(val beep: BeepMessage) : RazorEvent
    data class AccountQueryResult(
        val query: String,
        val result: bot.inker.bc.razor.protocol.account.AccountQueryResult
    ) : RazorEvent
}
