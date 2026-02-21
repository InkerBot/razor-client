package bot.inker.bc.razor

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.internal.GsonFactory
import bot.inker.bc.razor.protocol.auth.LoginResult
import bot.inker.bc.razor.protocol.chat.ReceivedChatMessage
import bot.inker.bc.razor.protocol.room.ChatRoomSearchRequest
import bot.inker.bc.razor.protocol.room.RoomJoinResult
import bot.inker.bc.razor.protocol.common.ForceDisconnectReason
import bot.inker.bc.razor.state.CharacterState
import bot.inker.bc.razor.state.RoomState
import bot.inker.bc.razor.transport.SocketTransport
import bot.inker.bc.razor.transport.SocketTransportListener
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class RazorClientIntegrationTest {

    private lateinit var mockTransport: MockTransport
    private lateinit var client: RazorClient

    @BeforeEach
    fun setUp() {
        mockTransport = MockTransport()
        client = RazorClient(mockTransport, RazorClientConfig(
            rateLimitMaxMessages = 100,
            rateLimitWindowMs = 100,
        ))
    }

    @AfterEach
    fun tearDown() {
        client.close()
    }

    @Test
    fun `connect and receive server info`() {
        val serverInfoReceived = CompletableFuture<Pair<Int, Long>>()
        client.on<RazorEvent.ServerInfo> { serverInfoReceived.complete(it.onlinePlayers to it.serverTime) }

        client.connect()
        mockTransport.simulateConnect()
        assertEquals(RazorClientState.CONNECTED, client.state)

        mockTransport.simulateEvent("ServerInfo", """{"Time":1700000000,"OnlinePlayers":42}""")
        val (players, time) = serverInfoReceived.get(5, TimeUnit.SECONDS)
        assertEquals(42, players)
        assertEquals(1700000000L, time)
    }

    @Test
    fun `login success flow`() {
        client.connect()
        mockTransport.simulateConnect()

        val loginFuture = client.login("user@test.com", "password123")

        // Verify the login message was sent
        val sentMsg = mockTransport.sentMessages.find { it.first == "AccountLogin" }
        assertNotNull(sentMsg)

        // Simulate server response
        val accountJson = """{
            "ID": "socket-id-123",
            "MemberNumber": 12345,
            "Name": "TestUser",
            "AccountName": "user@test.com",
            "Creation": 1700000000,
            "Money": 500,
            "Appearance": [],
            "FriendList": [99999],
            "WhiteList": [],
            "BlackList": []
        }"""
        mockTransport.simulateEvent("LoginResponse", accountJson)

        val result = loginFuture.get(5, TimeUnit.SECONDS)
        assertTrue(result is LoginResult.Success)
        val success = result as LoginResult.Success
        assertEquals(12345, success.data.memberNumber)
        assertEquals("TestUser", success.data.name)

        assertEquals(RazorClientState.LOGGED_IN, client.state)
        assertNotNull(client.player)
        assertEquals(12345, client.player!!.memberNumber)
    }

    @Test
    fun `login error flow`() {
        client.connect()
        mockTransport.simulateConnect()

        val loginFuture = client.login("bad@test.com", "wrong")
        mockTransport.simulateEvent("LoginResponse", """"InvalidNamePassword"""")

        val result = loginFuture.get(5, TimeUnit.SECONDS)
        assertTrue(result is LoginResult.Error)
        assertEquals("InvalidNamePassword", (result as LoginResult.Error).message)
    }

    @Test
    fun `room join and chat flow`() {
        // Setup: login first
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")

        // Join room
        val joinFuture = client.room.join("TestRoom")
        mockTransport.simulateEvent("ChatRoomSearchResponse", """"JoinedRoom"""")
        assertInstanceOf(RoomJoinResult.Success::class.java, joinFuture.get(5, TimeUnit.SECONDS))

        // Receive room sync
        mockTransport.simulateEvent("ChatRoomSync", """{
            "Name": "TestRoom",
            "Description": "A test room",
            "Admin": [111],
            "Background": "MainHall",
            "Limit": 10,
            "Language": "EN",
            "Space": "",
            "Character": [
                {"ID": "s1", "MemberNumber": 111, "Name": "Player1", "Appearance": []},
                {"ID": "s2", "MemberNumber": 222, "Name": "Player2", "Appearance": []}
            ]
        }""")

        // Wait for async event processing
        Thread.sleep(300)

        assertEquals(RazorClientState.IN_ROOM, client.state)
        assertNotNull(client.room.state)
        assertEquals("TestRoom", client.room.state!!.name)
        assertEquals(2, client.room.characters.size)

        // Receive a chat message
        val messages = CopyOnWriteArrayList<ReceivedChatMessage>()
        client.on<RazorEvent.ChatMessage> { messages.add(it.message) }

        mockTransport.simulateEvent("ChatRoomMessage", """{
            "Sender": 222,
            "Content": "Hello everyone!",
            "Type": "Chat"
        }""")

        Thread.sleep(200)
        assertEquals(1, messages.size)
        assertEquals(222, messages[0].sender)
        assertEquals("Hello everyone!", messages[0].content)
    }

    @Test
    fun `member join and leave`() {
        setupLoggedInRoom()

        val joined = CopyOnWriteArrayList<CharacterState>()
        val left = CopyOnWriteArrayList<Int>()
        client.on<RazorEvent.MemberJoined> { joined.add(it.character) }
        client.on<RazorEvent.MemberLeft> { left.add(it.memberNumber) }

        // Member joins
        mockTransport.simulateEvent("ChatRoomSyncMemberJoin", """{
            "SourceMemberNumber": 333,
            "Character": {"ID": "s3", "MemberNumber": 333, "Name": "Player3", "Appearance": []}
        }""")
        Thread.sleep(200)
        assertEquals(1, joined.size)
        assertEquals(333, joined[0].memberNumber)
        assertEquals(3, client.room.characters.size)

        // Member leaves
        mockTransport.simulateEvent("ChatRoomSyncMemberLeave", """{"SourceMemberNumber": 333}""")
        Thread.sleep(200)
        assertEquals(1, left.size)
        assertEquals(333, left[0])
        assertEquals(2, client.room.characters.size)
    }

    @Test
    fun `disconnect cleans up state`() {
        setupLoggedInRoom()
        var disconnectReason: String? = "initial"
        client.on<RazorEvent.Disconnected> { disconnectReason = it.reason }

        mockTransport.simulateDisconnect("transport close")
        Thread.sleep(200)
        assertEquals(RazorClientState.DISCONNECTED, client.state)
        assertNull(client.room.state)
        assertEquals("transport close", disconnectReason)
    }

    @Test
    fun `force disconnect dispatches event`() {
        client.connect()
        mockTransport.simulateConnect()

        var receivedReason: ForceDisconnectReason? = null
        client.on<RazorEvent.ForceDisconnect> { receivedReason = it.reason }

        mockTransport.simulateEvent("ForceDisconnect", """"ErrorDuplicatedLogin"""")
        Thread.sleep(200)
        assertEquals(ForceDisconnectReason.DUPLICATED_LOGIN, receivedReason)
    }

    @Test
    fun `send chat generates message with MsgId`() {
        setupLoggedInRoom()
        client.chat.send("Hello!")

        val chatMsg = mockTransport.sentMessages.find { it.first == "ChatRoomChat" }
        assertNotNull(chatMsg)
        assertTrue(chatMsg!!.second.contains("\"Content\":\"Hello!\""))
        assertTrue(chatMsg.second.contains("\"Type\":\"Chat\""))
        assertTrue(chatMsg.second.contains("MsgId"))
    }

    @Test
    fun `updateAccount DSL sends fields through transport`() {
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")

        client.updateAccount(force = true) {
            money = 500
            title = "Queen"
        }

        // Wait for rate limiter to process
        Thread.sleep(300)

        val updateMsg = mockTransport.sentMessages.find { it.first == "AccountUpdate" }
        assertNotNull(updateMsg)
        assertTrue(updateMsg!!.second.contains("\"Money\":500"))
        assertTrue(updateMsg.second.contains("\"Title\":\"Queen\""))
    }

    @Test
    fun `updateAccount DSL with empty block does not send`() {
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")

        client.updateAccount { /* nothing set */ }
        client.flushAccountUpdates()

        Thread.sleep(300)

        val updateMsg = mockTransport.sentMessages.find { it.first == "AccountUpdate" }
        assertNull(updateMsg)
    }

    @Test
    fun `room kick sends correct admin payload`() {
        setupLoggedInRoom()
        client.room.kick(222)

        Thread.sleep(200)
        val msg = mockTransport.sentMessages.find { it.first == "ChatRoomAdmin" }
        assertNotNull(msg)
        assertTrue(msg!!.second.contains("\"Action\":\"Kick\""))
        assertTrue(msg.second.contains("\"MemberNumber\":222"))
    }

    @Test
    fun `room ban sends correct admin payload`() {
        setupLoggedInRoom()
        client.room.ban(222)

        Thread.sleep(200)
        val msg = mockTransport.sentMessages.find { it.first == "ChatRoomAdmin" }
        assertNotNull(msg)
        assertTrue(msg!!.second.contains("\"Action\":\"Ban\""))
        assertTrue(msg.second.contains("\"MemberNumber\":222"))
    }

    @Test
    fun `room promote sends correct admin payload`() {
        setupLoggedInRoom()
        client.room.promote(222)

        Thread.sleep(200)
        val msg = mockTransport.sentMessages.find { it.first == "ChatRoomAdmin" }
        assertNotNull(msg)
        assertTrue(msg!!.second.contains("\"Action\":\"Promote\""))
        assertTrue(msg.second.contains("\"MemberNumber\":222"))
    }

    @Test
    fun `room shuffle sends correct admin payload`() {
        setupLoggedInRoom()
        client.room.shuffle()

        Thread.sleep(200)
        val msg = mockTransport.sentMessages.find { it.first == "ChatRoomAdmin" }
        assertNotNull(msg)
        assertTrue(msg!!.second.contains("\"Action\":\"Shuffle\""))
    }

    @Test
    fun `room swap sends target and destination`() {
        setupLoggedInRoom()
        client.room.swap(111, 222)

        Thread.sleep(200)
        val msg = mockTransport.sentMessages.find { it.first == "ChatRoomAdmin" }
        assertNotNull(msg)
        assertTrue(msg!!.second.contains("\"Action\":\"Swap\""))
        assertTrue(msg.second.contains("\"TargetMemberNumber\":111"))
        assertTrue(msg.second.contains("\"DestinationMemberNumber\":222"))
    }

    @Test
    fun `item sync replaces existing item in same group`() {
        setupLoggedInRoomWithAppearance()

        mockTransport.simulateEvent("ChatRoomSyncItem", """{
            "Source": 111,
            "Item": {
                "Target": 222,
                "Group": "Cloth",
                "Name": "Dress2",
                "Difficulty": 5
            }
        }""")
        Thread.sleep(200)

        val char = client.room.characters.find { it.memberNumber == 222 }!!
        val clothItem = char.appearance.find { it.group == "Cloth" }
        assertNotNull(clothItem)
        assertEquals("Dress2", clothItem!!.name)
        assertEquals(5, clothItem.difficulty)
        // Should still have same number of appearance items
        assertEquals(2, char.appearance.size)
    }

    @Test
    fun `item sync removes item when Name is null`() {
        setupLoggedInRoomWithAppearance()

        mockTransport.simulateEvent("ChatRoomSyncItem", """{
            "Source": 111,
            "Item": {
                "Target": 222,
                "Group": "Cloth",
                "Name": null
            }
        }""")
        Thread.sleep(200)

        val char = client.room.characters.find { it.memberNumber == 222 }!!
        assertNull(char.appearance.find { it.group == "Cloth" })
        assertEquals(1, char.appearance.size)
    }

    @Test
    fun `item sync adds item to new group`() {
        setupLoggedInRoomWithAppearance()

        mockTransport.simulateEvent("ChatRoomSyncItem", """{
            "Source": 111,
            "Item": {
                "Target": 222,
                "Group": "Hat",
                "Name": "TopHat"
            }
        }""")
        Thread.sleep(200)

        val char = client.room.characters.find { it.memberNumber == 222 }!!
        assertEquals(3, char.appearance.size)
        val hatItem = char.appearance.find { it.group == "Hat" }
        assertNotNull(hatItem)
        assertEquals("TopHat", hatItem!!.name)
    }

    @Test
    fun `expression sync sets Property Expression on existing appearance item`() {
        setupLoggedInRoomWithAppearance()

        mockTransport.simulateEvent("ChatRoomSyncExpression", """{
            "MemberNumber": 222,
            "Name": "Angry",
            "Group": "Cloth"
        }""")
        Thread.sleep(200)

        val char = client.room.characters.find { it.memberNumber == 222 }!!
        val clothItem = char.appearance.find { it.group == "Cloth" }!!
        val expr = clothItem.property?.asJsonObject?.get("Expression")?.asString
        assertEquals("Angry", expr)
    }

    @Test
    fun `expression sync creates Property when it was null`() {
        setupLoggedInRoomWithAppearance()

        // "Shoes" item has no Property in the fixture
        mockTransport.simulateEvent("ChatRoomSyncExpression", """{
            "MemberNumber": 222,
            "Name": "Happy",
            "Group": "Shoes"
        }""")
        Thread.sleep(200)

        val char = client.room.characters.find { it.memberNumber == 222 }!!
        val shoesItem = char.appearance.find { it.group == "Shoes" }!!
        val expr = shoesItem.property?.asJsonObject?.get("Expression")?.asString
        assertEquals("Happy", expr)
    }

    @Test
    fun `expression sync is no-op when group not found in appearance`() {
        setupLoggedInRoomWithAppearance()

        val charBefore = client.room.characters.find { it.memberNumber == 222 }!!

        mockTransport.simulateEvent("ChatRoomSyncExpression", """{
            "MemberNumber": 222,
            "Name": "Smile",
            "Group": "NonExistentGroup"
        }""")
        Thread.sleep(200)

        val charAfter = client.room.characters.find { it.memberNumber == 222 }!!
        assertEquals(charBefore.appearance, charAfter.appearance)
    }

    @Test
    fun `room search returns results`() {
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")

        val searchFuture = client.room.search(ChatRoomSearchRequest(query = "test"))

        // Verify the search message was sent with uppercase query
        Thread.sleep(200)
        val sentMsg = mockTransport.sentMessages.find { it.first == "ChatRoomSearch" }
        assertNotNull(sentMsg)
        assertTrue(sentMsg!!.second.contains("\"Query\":\"TEST\""), "Query should be uppercased, got: ${sentMsg.second}")

        // Simulate server response with room list
        mockTransport.simulateEvent("ChatRoomSearchResult", """[
            {
                "Name": "TestRoom1",
                "Description": "A test room",
                "MemberCount": 3,
                "MemberLimit": 10,
                "Creator": "Creator1",
                "CreatorMemberNumber": 100,
                "Language": "EN",
                "Space": "",
                "Locked": false
            },
            {
                "Name": "TestRoom2",
                "Description": "Another room",
                "MemberCount": 5,
                "MemberLimit": 8,
                "Creator": "Creator2",
                "CreatorMemberNumber": 200,
                "Language": "EN",
                "Space": "X",
                "Locked": true
            }
        ]""")

        val results = searchFuture.get(5, TimeUnit.SECONDS)
        assertEquals(2, results.size)

        assertEquals("TestRoom1", results[0].name)
        assertEquals("A test room", results[0].description)
        assertEquals(3, results[0].memberCount)
        assertEquals(10, results[0].memberLimit)
        assertEquals("Creator1", results[0].creator)
        assertEquals(false, results[0].locked)

        assertEquals("TestRoom2", results[1].name)
        assertEquals(5, results[1].memberCount)
        assertEquals(true, results[1].locked)
        assertEquals("X", results[1].space)
    }

    @Test
    fun `room search with space parameter`() {
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")

        val searchFuture = client.room.search(ChatRoomSearchRequest(query = "", space = "X"))

        Thread.sleep(200)
        val sentMsg = mockTransport.sentMessages.find { it.first == "ChatRoomSearch" }
        assertNotNull(sentMsg)
        assertTrue(sentMsg!!.second.contains("\"Space\":\"X\""))

        // Simulate empty result
        mockTransport.simulateEvent("ChatRoomSearchResult", """[]""")

        val results = searchFuture.get(5, TimeUnit.SECONDS)
        assertEquals(0, results.size)
    }

    @Test
    fun `room search sends all default fields`() {
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")

        client.room.search()

        Thread.sleep(200)
        val sentMsg = mockTransport.sentMessages.find { it.first == "ChatRoomSearch" }
        assertNotNull(sentMsg)
        val payload = sentMsg!!.second
        // Verify required fields are present with defaults
        assertTrue(payload.contains("\"Query\":\"\""), "Should contain Query field")
        assertTrue(payload.contains("\"Space\":\"\""), "Should contain Space field")
        assertTrue(payload.contains("\"Language\":\"\""), "Should contain Language field")
        assertTrue(payload.contains("\"Game\":\"\""), "Should contain Game field")
        assertTrue(payload.contains("\"FullRooms\":false"), "Should contain FullRooms field")
        assertTrue(payload.contains("\"ShowLocked\":true"), "Should contain ShowLocked field")
        assertTrue(payload.contains("\"SearchDescs\":false"), "Should contain SearchDescs field")
    }

    // -- Helpers --

    private fun setupLoggedInRoom() {
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")
        client.room.join("TestRoom")
        mockTransport.simulateEvent("ChatRoomSearchResponse", """"JoinedRoom"""")
        mockTransport.simulateEvent("ChatRoomSync", """{
            "Name": "TestRoom", "Description": "", "Admin": [111],
            "Background": "MainHall", "Limit": 10, "Language": "EN", "Space": "",
            "Character": [
                {"ID": "s1", "MemberNumber": 111, "Name": "Player1", "Appearance": []},
                {"ID": "s2", "MemberNumber": 222, "Name": "Player2", "Appearance": []}
            ]
        }""")
        // Give time for async event processing
        Thread.sleep(300)
    }

    private fun setupLoggedInRoomWithAppearance() {
        client.connect()
        mockTransport.simulateConnect()
        client.login("user@test.com", "pass")
        mockTransport.simulateEvent("LoginResponse", """{
            "ID": "s1", "MemberNumber": 111, "Name": "Player1"
        }""")
        client.room.join("TestRoom")
        mockTransport.simulateEvent("ChatRoomSearchResponse", """"JoinedRoom"""")
        mockTransport.simulateEvent("ChatRoomSync", """{
            "Name": "TestRoom", "Description": "", "Admin": [111],
            "Background": "MainHall", "Limit": 10, "Language": "EN", "Space": "",
            "Character": [
                {"ID": "s1", "MemberNumber": 111, "Name": "Player1", "Appearance": []},
                {"ID": "s2", "MemberNumber": 222, "Name": "Player2", "Appearance": [
                    {"Group": "Cloth", "Name": "Dress1", "Property": {"Effect": ["Slow"]}},
                    {"Group": "Shoes", "Name": "Boots1"}
                ]}
            ]
        }""")
        Thread.sleep(300)
    }

    class MockTransport : SocketTransport {
        private var listener: SocketTransportListener? = null
        private var _connected = false
        val sentMessages = CopyOnWriteArrayList<Pair<String, String>>()

        override fun connect(url: String, properties: Map<String, String>, listener: SocketTransportListener) {
            this.listener = listener
        }

        override fun disconnect() {
            _connected = false
        }

        override fun emit(event: String, payload: String) {
            sentMessages.add(event to payload)
        }

        override fun isConnected() = _connected

        fun simulateConnect() {
            _connected = true
            listener?.onConnect()
        }

        fun simulateDisconnect(reason: String? = null) {
            _connected = false
            listener?.onDisconnect(reason)
        }

        fun simulateEvent(event: String, payload: String) {
            listener?.onEvent(event, payload)
        }
    }
}
