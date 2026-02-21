package bot.inker.bc.razor.internal

import bot.inker.bc.razor.RazorClientState
import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.protocol.account.*
import bot.inker.bc.razor.protocol.auth.CreateAccountResult
import bot.inker.bc.razor.protocol.auth.LoginResult
import bot.inker.bc.razor.protocol.auth.PasswordResetResult
import bot.inker.bc.razor.protocol.chat.GameResponseData
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import bot.inker.bc.razor.protocol.common.ForceDisconnectReason
import bot.inker.bc.razor.protocol.common.ItemBundle
import bot.inker.bc.razor.protocol.common.ServerInfo
import bot.inker.bc.razor.protocol.room.*
import bot.inker.bc.razor.protocol.sync.*
import bot.inker.bc.razor.state.CharacterState
import bot.inker.bc.razor.state.PlayerState
import bot.inker.bc.razor.state.RoomState
import com.google.gson.*
import java.util.logging.Level
import java.util.logging.Logger

internal class EventRouter(
    private val eventBus: EventBus,
    private val pendingRequests: PendingRequests,
    private val stateAccessor: StateAccessor,
) {
    private val logger = Logger.getLogger(EventRouter::class.java.name)
    private val gson = GsonFactory.gson

    interface StateAccessor {
        var clientState: RazorClientState
        var player: PlayerState?
        var currentRoom: RoomState?
    }

    fun route(event: String, payload: String) {
        try {
            when (event) {
                "ServerInfo" -> handleServerInfo(payload)
                "LoginResponse" -> handleLoginResponse(payload)
                "LoginQueue" -> handleLoginQueue(payload)
                "CreationResponse" -> handleCreationResponse(payload)
                "PasswordResetResponse" -> handlePasswordResetResponse(payload)
                "ForceDisconnect" -> handleForceDisconnect(payload)
                "AccountQueryResult" -> handleAccountQueryResult(payload)
                "AccountBeep" -> handleAccountBeep(payload)
                "ChatRoomSearchResult" -> handleChatRoomSearchResult(payload)
                "ChatRoomSearchResponse" -> handleChatRoomSearchResponse(payload)
                "ChatRoomCreateResponse" -> handleChatRoomCreateResponse(payload)
                "ChatRoomSync" -> handleChatRoomSync(payload)
                "ChatRoomSyncMemberJoin" -> handleMemberJoin(payload)
                "ChatRoomSyncMemberLeave" -> handleMemberLeave(payload)
                "ChatRoomSyncRoomProperties" -> handleRoomPropertiesSync(payload)
                "ChatRoomSyncCharacter" -> handleCharacterSync(payload)
                "ChatRoomSyncSingle" -> handleCharacterSync(payload)
                "ChatRoomSyncExpression" -> handleExpressionSync(payload)
                "ChatRoomSyncPose" -> handlePoseSync(payload)
                "ChatRoomSyncArousal" -> handleArousalSync(payload)
                "ChatRoomSyncItem" -> handleItemSync(payload)
                "ChatRoomSyncMapData" -> handleMapDataSync(payload)
                "ChatRoomSyncReorderPlayers" -> handleReorderPlayers(payload)
                "ChatRoomMessage" -> handleChatMessage(payload)
                "ChatRoomGameResponse" -> handleGameResponse(payload)
                "ChatRoomAllowItem" -> handleAllowItem(payload)
                "ChatRoomUpdateResponse" -> handleChatRoomUpdateResponse(payload)
                else -> logger.fine("Unhandled event: $event")
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error handling event '$event'", e)
        }
    }

    private fun handleServerInfo(payload: String) {
        val info = gson.fromJson(payload, ServerInfo::class.java)
        eventBus.dispatch(RazorEvent.ServerInfo(info.onlinePlayers, info.time))
    }

    private fun handleLoginResponse(payload: String) {
        val element = JsonParser.parseString(payload)
        if (element.isJsonPrimitive) {
            val errorMsg = element.asString
            pendingRequests.complete("LoginResponse", LoginResult.Error(errorMsg))
        } else {
            val accountData = gson.fromJson(element, ServerAccountData::class.java)
            val playerState = PlayerState.fromAccountData(accountData)
            stateAccessor.player = playerState
            stateAccessor.clientState = RazorClientState.LOGGED_IN
            pendingRequests.complete("LoginResponse", LoginResult.Success(accountData))
        }
    }

    private fun handleLoginQueue(payload: String) {
        val position = payload.trim().toIntOrNull() ?: return
        eventBus.dispatch(RazorEvent.LoginQueue(position))
    }

    private fun handleCreationResponse(payload: String) {
        val element = JsonParser.parseString(payload)
        val message = element.asString
        if (message == "AccountCreated") {
            pendingRequests.complete("CreationResponse", CreateAccountResult.Success)
        } else {
            pendingRequests.complete("CreationResponse", CreateAccountResult.Error(message))
        }
    }

    private fun handlePasswordResetResponse(payload: String) {
        val element = JsonParser.parseString(payload)
        val result = when (val message = element.asString) {
            "EmailSent" -> PasswordResetResult.EmailSent
            "PasswordResetSuccessful" -> PasswordResetResult.PasswordResetSuccessful
            else -> PasswordResetResult.Error(message)
        }
        pendingRequests.complete("PasswordResetResponse", result)
    }

    private fun handleForceDisconnect(payload: String) {
        val element = JsonParser.parseString(payload)
        val reason = ForceDisconnectReason.fromWireValue(element.asString)
        eventBus.dispatch(RazorEvent.ForceDisconnect(reason))
    }

    private fun handleAccountQueryResult(payload: String) {
        val result = gson.fromJson(payload, AccountQueryResult::class.java)
        pendingRequests.complete("AccountQueryResult_${result.query}", result)
        eventBus.dispatch(RazorEvent.AccountQueryResult(result.query, result))
    }

    private fun handleAccountBeep(payload: String) {
        val beep = gson.fromJson(payload, BeepMessage::class.java)
        eventBus.dispatch(RazorEvent.BeepReceived(beep))
    }

    private fun handleChatRoomSearchResult(payload: String) {
        val element = JsonParser.parseString(payload)
        pendingRequests.complete("ChatRoomSearchResult", element)
    }

    private fun handleChatRoomSearchResponse(payload: String) {
        val element = JsonParser.parseString(payload)
        val response = element.asString
        val result = if (response == "JoinedRoom") {
            RoomJoinResult.Success
        } else {
            RoomJoinResult.Error(response)
        }
        pendingRequests.complete("ChatRoomSearchResponse", result)
    }

    private fun handleChatRoomCreateResponse(payload: String) {
        val element = JsonParser.parseString(payload)
        val response = element.asString
        val result = if (response == "ChatRoomCreated") {
            RoomCreateResult.Success
        } else {
            RoomCreateResult.Error(response)
        }
        pendingRequests.complete("ChatRoomCreateResponse", result)
    }

    private fun handleChatRoomSync(payload: String) {
        val syncMsg = gson.fromJson(payload, RoomSyncMessage::class.java)

        // Parse characters from raw JSON
        val characters = syncMsg.character?.mapNotNull { charJson ->
            parseCharacterFromJson(charJson)
        } ?: emptyList()

        val room = RoomState.fromSyncMessage(syncMsg).copy(characters = characters)
        stateAccessor.currentRoom = room
        stateAccessor.clientState = RazorClientState.IN_ROOM
        eventBus.dispatch(RazorEvent.RoomJoined(room))
    }

    private fun handleMemberJoin(payload: String) {
        val msg = gson.fromJson(payload, MemberJoinMessage::class.java)
        val character = parseCharacterFromJson(msg.character) ?: return
        val room = stateAccessor.currentRoom ?: return
        stateAccessor.currentRoom = room.copy(characters = room.characters + character)
        eventBus.dispatch(RazorEvent.MemberJoined(character))
    }

    private fun handleMemberLeave(payload: String) {
        val msg = gson.fromJson(payload, MemberLeaveMessage::class.java)
        val room = stateAccessor.currentRoom ?: return
        stateAccessor.currentRoom = room.withCharacterRemoved(msg.sourceMemberNumber)

        // If we're the one leaving
        val player = stateAccessor.player
        if (player != null && msg.sourceMemberNumber == player.memberNumber) {
            stateAccessor.currentRoom = null
            stateAccessor.clientState = RazorClientState.LOGGED_IN
            pendingRequests.complete("ChatRoomLeave", Unit)
            eventBus.dispatch(RazorEvent.RoomLeft)
        } else {
            eventBus.dispatch(RazorEvent.MemberLeft(msg.sourceMemberNumber))
        }
    }

    private fun handleRoomPropertiesSync(payload: String) {
        val msg = gson.fromJson(payload, RoomPropertiesSyncMessage::class.java)
        val room = stateAccessor.currentRoom ?: return
        val updated = room.copy(
            name = msg.name ?: room.name,
            description = msg.description ?: room.description,
            admin = msg.admin ?: room.admin,
            ban = msg.ban ?: room.ban,
            whitelist = msg.whitelist ?: room.whitelist,
            background = msg.background ?: room.background,
            limit = msg.limit ?: room.limit,
            locked = msg.locked ?: room.locked,
            game = msg.game?.let { ChatRoomGame.fromWireValue(it) } ?: room.game,
            visibility = msg.visibility?.mapNotNull { ChatRoomAccessRole.fromWireValue(it) } ?: room.visibility,
            access = msg.access?.mapNotNull { ChatRoomAccessRole.fromWireValue(it) } ?: room.access,
            blockCategory = msg.blockCategory?.mapNotNull { ChatRoomBlockCategory.fromWireValue(it) }
                ?: room.blockCategory,
            language = msg.language?.let { ChatRoomLanguage.fromWireValue(it) } ?: room.language,
            space = msg.space?.let { ChatRoomSpace.fromWireValue(it) } ?: room.space,
            mapData = msg.mapData ?: room.mapData,
            custom = msg.custom ?: room.custom,
            characters = room.characters,
        )
        stateAccessor.currentRoom = updated
        eventBus.dispatch(RazorEvent.RoomUpdated(updated))
    }

    private fun handleCharacterSync(payload: String) {
        val msg = gson.fromJson(payload, CharacterSyncMessage::class.java)
        val character = parseCharacterFromJson(msg.character) ?: return
        val room = stateAccessor.currentRoom ?: return

        val existing = room.characters.indexOfFirst { it.memberNumber == character.memberNumber }
        stateAccessor.currentRoom = if (existing >= 0) {
            room.copy(characters = room.characters.toMutableList().also { it[existing] = character })
        } else {
            room.copy(characters = room.characters + character)
        }
        eventBus.dispatch(RazorEvent.CharacterUpdated(character.memberNumber, character))
    }

    private fun handleExpressionSync(payload: String) {
        val msg = gson.fromJson(payload, ExpressionSyncMessage::class.java)
        stateAccessor.currentRoom?.let { room ->
            stateAccessor.currentRoom = room.withCharacterUpdated(msg.memberNumber) { char ->
                val itemIndex = char.appearance.indexOfFirst { it.group == msg.group }
                if (itemIndex == -1) return@withCharacterUpdated char
                val oldItem = char.appearance[itemIndex]
                val newProperty =
                    (oldItem.property?.takeIf { it.isJsonObject }?.asJsonObject?.deepCopy() ?: JsonObject()).apply {
                        val exprValue = msg.name?.let { JsonPrimitive(it) } ?: JsonNull.INSTANCE
                        add("Expression", exprValue)
                    }
                val newItem = oldItem.copy(property = newProperty)
                char.copy(appearance = char.appearance.toMutableList().also { it[itemIndex] = newItem })
            }
        }
        eventBus.dispatch(RazorEvent.ExpressionChanged(msg.memberNumber, msg.name, msg.group))
    }

    private fun handlePoseSync(payload: String) {
        val msg = gson.fromJson(payload, PoseSyncMessage::class.java)
        stateAccessor.currentRoom?.let { room ->
            stateAccessor.currentRoom = room.withCharacterUpdated(msg.memberNumber) {
                it.copy(activePose = msg.pose ?: emptyList())
            }
        }
        eventBus.dispatch(RazorEvent.PoseChanged(msg.memberNumber, msg.pose ?: emptyList()))
    }

    private fun handleArousalSync(payload: String) {
        val msg = gson.fromJson(payload, ArousalSyncMessage::class.java)
        stateAccessor.currentRoom?.let { room ->
            stateAccessor.currentRoom = room.withCharacterUpdated(msg.memberNumber) {
                it.copy(
                    arousal = CharacterState.ArousalState(
                        orgasmTimer = msg.orgasmTimer,
                        orgasmCount = msg.orgasmCount,
                        progress = msg.progress,
                        progressTimer = msg.progressTimer,
                    )
                )
            }
        }
        eventBus.dispatch(RazorEvent.ArousalChanged(msg.memberNumber, msg))
    }

    private fun handleItemSync(payload: String) {
        val msg = gson.fromJson(payload, ItemSyncMessage::class.java)
        stateAccessor.currentRoom?.let { room ->
            stateAccessor.currentRoom = room.withCharacterUpdated(msg.item.target) { char ->
                if (msg.item.name == null) {
                    // Remove item from that group
                    char.copy(appearance = char.appearance.filter { it.group != msg.item.group })
                } else {
                    val newItem = ItemBundle(
                        group = msg.item.group,
                        name = msg.item.name,
                        color = msg.item.color,
                        difficulty = msg.item.difficulty,
                        property = msg.item.property,
                        craft = msg.item.craft,
                    )
                    val existingIndex = char.appearance.indexOfFirst { it.group == msg.item.group }
                    if (existingIndex >= 0) {
                        // Replace existing item in same group
                        char.copy(appearance = char.appearance.toMutableList().also { it[existingIndex] = newItem })
                    } else {
                        // Append new item
                        char.copy(appearance = char.appearance + newItem)
                    }
                }
            }
        }
        eventBus.dispatch(RazorEvent.ItemChanged(msg.source, msg))
    }

    private fun handleMapDataSync(payload: String) {
        val msg = gson.fromJson(payload, MapDataSyncMessage::class.java)
        stateAccessor.currentRoom?.let { room ->
            stateAccessor.currentRoom = room.withCharacterUpdated(msg.memberNumber) {
                it.copy(mapData = msg.mapData)
            }
        }
        eventBus.dispatch(RazorEvent.MapDataChanged(msg.memberNumber))
    }

    private fun handleReorderPlayers(payload: String) {
        val msg = gson.fromJson(payload, ReorderPlayersSyncMessage::class.java)
        val room = stateAccessor.currentRoom ?: return
        val orderMap = msg.playerOrder.withIndex().associate { (index, memberNumber) -> memberNumber to index }
        val sorted = room.characters.sortedBy { orderMap[it.memberNumber] ?: Int.MAX_VALUE }
        stateAccessor.currentRoom = room.copy(characters = sorted)
        eventBus.dispatch(RazorEvent.PlayersReordered(msg.playerOrder))
    }

    private fun handleChatMessage(payload: String) {
        val msg = gson.fromJson(payload, ReceivedChatMessage::class.java)
        eventBus.dispatch(RazorEvent.ChatMessage(msg))
    }

    private fun handleGameResponse(payload: String) {
        val msg = gson.fromJson(payload, GameResponseData::class.java)
        eventBus.dispatch(RazorEvent.GameResponse(msg))
    }

    private fun handleAllowItem(payload: String) {
        val element = JsonParser.parseString(payload).asJsonObject
        val memberNumber = element.get("MemberNumber")?.asInt ?: return
        val allowItem = element.get("AllowItem")?.asBoolean ?: return
        pendingRequests.complete("ChatRoomAllowItem_$memberNumber", allowItem)
    }

    private fun handleChatRoomUpdateResponse(payload: String) {
        val element = JsonParser.parseString(payload)
        val response = element.asString
        pendingRequests.complete("ChatRoomUpdateResponse", response)
    }

    internal fun parseCharacterFromJson(json: JsonElement): CharacterState? {
        if (!json.isJsonObject) return null
        val obj = json.asJsonObject

        val memberNumber = obj.get("MemberNumber")?.asInt ?: return null
        val name = obj.get("Name")?.asString ?: return null
        val id = obj.get("ID")?.asString ?: memberNumber.toString()

        val appearance = if (obj.has("Appearance") && obj.get("Appearance").isJsonArray) {
            obj.getAsJsonArray("Appearance").mapNotNull { elem ->
                try {
                    gson.fromJson(elem, ItemBundle::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } else emptyList()

        val activePose = if (obj.has("ActivePose") && obj.get("ActivePose").isJsonArray) {
            obj.getAsJsonArray("ActivePose").map { it.asString }
        } else emptyList()

        val owner = obj.get("Owner")?.takeIf { !it.isJsonNull }?.asString

        val lovership = if (obj.has("Lovership") && obj.get("Lovership").isJsonArray) {
            obj.getAsJsonArray("Lovership").mapNotNull { elem ->
                try {
                    gson.fromJson(elem, Lovership::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } else emptyList()

        val reputation = if (obj.has("Reputation") && obj.get("Reputation").isJsonArray) {
            obj.getAsJsonArray("Reputation").mapNotNull { elem ->
                try {
                    gson.fromJson(elem, Reputation::class.java)
                } catch (e: Exception) {
                    null
                }
            }
        } else emptyList()

        val whiteList = if (obj.has("WhiteList") && obj.get("WhiteList").isJsonArray) {
            obj.getAsJsonArray("WhiteList").map { it.asInt }
        } else emptyList()

        val blackList = if (obj.has("BlackList") && obj.get("BlackList").isJsonArray) {
            obj.getAsJsonArray("BlackList").map { it.asInt }
        } else emptyList()

        val ownership = obj.get("Ownership")?.takeIf { it.isJsonObject }?.let {
            try {
                gson.fromJson(it, Ownership::class.java)
            } catch (e: Exception) {
                null
            }
        }

        val difficulty = obj.get("Difficulty")?.takeIf { it.isJsonObject }?.let {
            try {
                gson.fromJson(it, Difficulty::class.java)
            } catch (e: Exception) {
                null
            }
        }

        val nickname = obj.get("Nickname")?.takeIf { !it.isJsonNull }?.asString

        return CharacterState(
            id = id,
            memberNumber = memberNumber,
            name = name,
            nickname = nickname,
            appearance = appearance,
            activePose = activePose,
            owner = owner,
            ownership = ownership,
            lovership = lovership,
            reputation = reputation,
            whiteList = whiteList,
            blackList = blackList,
            labelColor = obj.get("LabelColor")?.takeIf { !it.isJsonNull }?.asString,
            description = obj.get("Description")?.takeIf { !it.isJsonNull }?.asString?.let { desc ->
                if (desc.startsWith("\u256C")) {
                    LZString.decompressFromUTF16(desc.substring(1))
                } else {
                    desc
                }
            },
            title = obj.get("Title")?.takeIf { !it.isJsonNull }?.asString,
            creation = obj.get("Creation")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
            difficulty = difficulty,
            rawData = json,
        )
    }
}
