package bot.inker.bc.razor.protocol

import bot.inker.bc.razor.internal.GsonFactory
import bot.inker.bc.razor.protocol.auth.CreateAccountRequest
import bot.inker.bc.razor.protocol.auth.LoginRequest
import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.OutgoingChatMessage
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import bot.inker.bc.razor.protocol.common.ItemBundle
import bot.inker.bc.razor.protocol.common.ServerInfo
import bot.inker.bc.razor.protocol.room.ChatRoomSearchRequest
import bot.inker.bc.razor.protocol.room.ChatRoomSearchResult
import bot.inker.bc.razor.protocol.sync.ExpressionSyncMessage
import bot.inker.bc.razor.protocol.sync.MemberLeaveMessage
import bot.inker.bc.razor.protocol.sync.PoseSyncMessage
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SerializationTest {

    private val gson = GsonFactory.gson

    @Test
    fun `LoginRequest serializes with PascalCase`() {
        val req = LoginRequest("user@test.com", "password123")
        val json = gson.toJson(req)
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals("user@test.com", obj.get("AccountName").asString)
        assertEquals("password123", obj.get("Password").asString)
    }

    @Test
    fun `CreateAccountRequest round trip`() {
        val req = CreateAccountRequest("TestUser", "test@test.com", "pass123", "test@email.com")
        val json = gson.toJson(req)
        val deserialized = gson.fromJson(json, CreateAccountRequest::class.java)
        assertEquals(req, deserialized)
    }

    @Test
    fun `ServerInfo deserializes from server JSON`() {
        val json = """{"Time":1700000000000,"OnlinePlayers":42}"""
        val info = gson.fromJson(json, ServerInfo::class.java)
        assertEquals(1700000000000L, info.time)
        assertEquals(42, info.onlinePlayers)
    }

    @Test
    fun `ItemBundle serializes only non-null fields`() {
        val bundle = ItemBundle("ItemArms", "HempRope")
        val json = gson.toJson(bundle)
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals("ItemArms", obj.get("Group").asString)
        assertEquals("HempRope", obj.get("Name").asString)
        // Null fields should not be present (Gson default)
        assertFalse(obj.has("Color"))
        assertFalse(obj.has("Difficulty"))
    }

    @Test
    fun `ChatMessageType serializes to wire value`() {
        val msg = OutgoingChatMessage("Hello", ChatMessageType.WHISPER, target = 12345)
        val json = gson.toJson(msg)
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals("Whisper", obj.get("Type").asString)
        assertEquals("Hello", obj.get("Content").asString)
        assertEquals(12345, obj.get("Target").asInt)
    }

    @Test
    fun `ReceivedChatMessage deserializes`() {
        val json = """{"Sender":12345,"Content":"Hello world","Type":"Chat"}"""
        val msg = gson.fromJson(json, ReceivedChatMessage::class.java)
        assertEquals(12345, msg.sender)
        assertEquals("Hello world", msg.content)
        assertEquals(ChatMessageType.CHAT, msg.type)
        assertNull(msg.target)
    }

    @Test
    fun `ChatRoomSearchRequest serializes correctly`() {
        val req = ChatRoomSearchRequest(query = "test", language = "EN", fullRooms = false)
        val json = gson.toJson(req)
        val obj = JsonParser.parseString(json).asJsonObject
        assertEquals("test", obj.get("Query").asString)
        assertEquals("EN", obj.get("Language").asString)
        assertFalse(obj.get("FullRooms").asBoolean)
    }

    @Test
    fun `ChatRoomSearchResult list deserializes`() {
        val json = """[
            {"Name":"TestRoom","MemberCount":3,"MemberLimit":10,"Language":"EN"},
            {"Name":"Room2","MemberCount":1,"MemberLimit":5}
        ]"""
        val listType = object : TypeToken<List<ChatRoomSearchResult>>() {}.type
        val results: List<ChatRoomSearchResult> = gson.fromJson(json, listType)
        assertEquals(2, results.size)
        assertEquals("TestRoom", results[0].name)
        assertEquals(3, results[0].memberCount)
        assertEquals("Room2", results[1].name)
    }

    @Test
    fun `ExpressionSyncMessage round trip`() {
        val msg = ExpressionSyncMessage(12345, "Blush", "Blush")
        val json = gson.toJson(msg)
        val deserialized = gson.fromJson(json, ExpressionSyncMessage::class.java)
        assertEquals(msg, deserialized)
    }

    @Test
    fun `PoseSyncMessage deserializes`() {
        val json = """{"MemberNumber":12345,"Pose":["Kneel","BackElbowTouch"]}"""
        val msg = gson.fromJson(json, PoseSyncMessage::class.java)
        assertEquals(12345, msg.memberNumber)
        assertEquals(listOf("Kneel", "BackElbowTouch"), msg.pose)
    }

    @Test
    fun `MemberLeaveMessage round trip`() {
        val msg = MemberLeaveMessage(99999)
        val json = gson.toJson(msg)
        val deserialized = gson.fromJson(json, MemberLeaveMessage::class.java)
        assertEquals(99999, deserialized.sourceMemberNumber)
    }
}
