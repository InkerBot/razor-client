package bot.inker.bc.razor

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.event.Subscription
import bot.inker.bc.razor.internal.*
import bot.inker.bc.razor.protocol.account.AccountQueryRequest
import bot.inker.bc.razor.protocol.account.AccountQueryResult
import bot.inker.bc.razor.protocol.account.AccountUpdateScope
import bot.inker.bc.razor.protocol.account.BeepRequest
import bot.inker.bc.razor.protocol.auth.*
import bot.inker.bc.razor.protocol.character.*
import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.protocol.chat.OutgoingChatMessage
import bot.inker.bc.razor.protocol.room.*
import bot.inker.bc.razor.state.CharacterState
import bot.inker.bc.razor.state.PlayerState
import bot.inker.bc.razor.state.RoomState
import bot.inker.bc.razor.transport.SocketTransport
import bot.inker.bc.razor.transport.SocketTransportListener
import bot.inker.bc.razor.transport.SocketTransports
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

class RazorClient(
    private val transport: SocketTransport,
    private val config: RazorClientConfig,
    private val defaultConnectionProperties: Map<String, String> = emptyMap(),
) : AutoCloseable {

    private val gson = GsonFactory.gson
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "razor-client").apply { isDaemon = true }
    }

    @PublishedApi
    internal val eventBus = EventBus()
    private val pendingRequests = PendingRequests(config.requestTimeoutMs, executor)
    private val rateLimiter = RateLimiter(config)
    private val accountUpdater = AccountUpdater(config, executor) { fields ->
        sendRateLimited("AccountUpdate", gson.toJson(fields))
    }

    private val stateAccessor = object : EventRouter.StateAccessor {
        override var clientState: RazorClientState = RazorClientState.DISCONNECTED
        override var player: PlayerState? = null
        override var currentRoom: RoomState? = null
    }

    private val eventRouter = EventRouter(eventBus, pendingRequests, stateAccessor)

    init {
        rateLimiter.setEmitter { event, payload ->
            transport.emit(event, payload)
        }
        executor.scheduleAtFixedRate({ rateLimiter.processQueue() }, 100, 100, TimeUnit.MILLISECONDS)
    }


    val state: RazorClientState get() = stateAccessor.clientState

    fun connect(url: String = config.serverUrl, properties: Map<String, String> = defaultConnectionProperties) {
        transport.connect(url, properties, object : SocketTransportListener {
            override fun onConnect() {
                stateAccessor.clientState = RazorClientState.CONNECTED
                eventBus.dispatch(RazorEvent.Connected)
            }

            override fun onDisconnect(reason: String?) {
                val prevState = stateAccessor.clientState
                stateAccessor.clientState = RazorClientState.DISCONNECTED
                stateAccessor.currentRoom = null
                rateLimiter.clear()
                accountUpdater.clear()
                pendingRequests.cancelAll()
                if (prevState != RazorClientState.DISCONNECTED) {
                    eventBus.dispatch(RazorEvent.Disconnected(reason))
                }
            }

            override fun onReconnecting(attemptNumber: Int) {
                eventBus.dispatch(RazorEvent.Reconnecting(attemptNumber))
            }

            override fun onEvent(event: String, payload: String) {
                executor.execute { eventRouter.route(event, payload) }
            }
        })
    }

    fun disconnect() {
        accountUpdater.syncToServer()
        transport.disconnect()
    }


    inline fun <reified T : RazorEvent> on(noinline handler: (T) -> Unit): Subscription {
        return on(T::class, handler)
    }

    fun <T : RazorEvent> on(eventType: KClass<T>, handler: (T) -> Unit): Subscription {
        return eventBus.subscribe(eventType, handler)
    }

    fun <T : RazorEvent> on(eventType: Class<T>, handler: (T) -> Unit): Subscription {
        return eventBus.subscribe(eventType.kotlin, handler)
    }

    fun onAny(handler: (RazorEvent) -> Unit): Subscription {
        return eventBus.onAny(handler)
    }


    fun login(accountName: String, password: String): CompletableFuture<LoginResult> {
        val future = pendingRequests.create<LoginResult>("LoginResponse")
        val request = LoginRequest(accountName, password)
        sendRateLimited("AccountLogin", gson.toJson(request))
        return future
    }

    fun createAccount(
        name: String,
        accountName: String,
        password: String,
        email: String
    ): CompletableFuture<CreateAccountResult> {
        val future = pendingRequests.create<CreateAccountResult>("CreationResponse")
        val request = CreateAccountRequest(name, accountName, password, email)
        sendRateLimited("AccountCreate", gson.toJson(request))
        return future
    }

    fun resetPassword(email: String): CompletableFuture<PasswordResetResult> {
        val future = pendingRequests.create<PasswordResetResult>("PasswordResetResponse")
        sendRateLimited("PasswordReset", gson.toJson(email))
        return future
    }

    fun resetPasswordProcess(
        accountName: String,
        resetNumber: String,
        newPassword: String
    ): CompletableFuture<PasswordResetResult> {
        val future = pendingRequests.create<PasswordResetResult>("PasswordResetResponse")
        val request = PasswordResetRequest(accountName, resetNumber, newPassword)
        sendRateLimited("PasswordResetProcess", gson.toJson(request))
        return future
    }


    val player: PlayerState? get() = stateAccessor.player


    val room: RoomScope = RoomScope()
    val chat: ChatScope = ChatScope()
    val character: CharacterScope = CharacterScope()
    val social: SocialScope = SocialScope()


    inline fun updateAccount(block: AccountUpdateScope.() -> Unit) {
        val scope = AccountUpdateScope()
        scope.block()
        if (scope.fields.isNotEmpty()) {
            queueAccountUpdate(scope.fields)
        }
    }

    inline fun updateAccount(force: Boolean, block: AccountUpdateScope.() -> Unit) {
        val scope = AccountUpdateScope()
        scope.block()
        if (scope.fields.isNotEmpty()) {
            queueAccountUpdate(scope.fields, force = force)
        }
    }

    @PublishedApi
    internal fun queueAccountUpdate(fields: Map<String, Any?>, force: Boolean = false) {
        accountUpdater.queueData(fields, force = force)
    }

    fun flushAccountUpdates() {
        accountUpdater.syncToServer()
    }


    inner class RoomScope {
        val state: RoomState? get() = stateAccessor.currentRoom
        val characters: List<CharacterState> get() = stateAccessor.currentRoom?.characters ?: emptyList()

        fun search(query: ChatRoomSearchRequest = ChatRoomSearchRequest()): CompletableFuture<List<ChatRoomSearchResult>> {
            val normalized = query.copy(query = query.query.uppercase().trim())
            val resultFuture = pendingRequests.create<JsonElement>("ChatRoomSearchResult")
            sendRateLimited("ChatRoomSearch", gson.toJson(normalized))
            return resultFuture.thenApply { json ->
                val listType = object : TypeToken<List<ChatRoomSearchResult>>() {}.type
                gson.fromJson<List<ChatRoomSearchResult>>(json, listType)
            }
        }

        fun create(settings: ChatRoomCreateRequest): CompletableFuture<RoomCreateResult> {
            val future = pendingRequests.create<RoomCreateResult>("ChatRoomCreateResponse")
            sendRateLimited("ChatRoomCreate", gson.toJson(settings))
            return future
        }

        fun join(name: String): CompletableFuture<RoomJoinResult> {
            val future = pendingRequests.create<RoomJoinResult>("ChatRoomSearchResponse")
            sendRateLimited("ChatRoomJoin", gson.toJson(mapOf("Name" to name)))
            return future
        }

        fun leave(): CompletableFuture<Unit> {
            accountUpdater.syncToServer()
            // Clear state immediately like the JS client does — the server does not
            // send ChatRoomSyncMemberLeave back to the leaving player.
            stateAccessor.currentRoom = null
            stateAccessor.clientState = RazorClientState.LOGGED_IN
            sendRateLimited("ChatRoomLeave", gson.toJson(""))
            eventBus.dispatch(RazorEvent.RoomLeft)
            return CompletableFuture.completedFuture(Unit)
        }


        fun kick(memberNumber: Int) {
            sendAdmin(ChatRoomAdminAction.KICK, memberNumber)
        }

        fun ban(memberNumber: Int) {
            sendAdmin(ChatRoomAdminAction.BAN, memberNumber)
        }

        fun unban(memberNumber: Int) {
            sendAdmin(ChatRoomAdminAction.UNBAN, memberNumber)
        }

        fun promote(memberNumber: Int) {
            sendAdmin(ChatRoomAdminAction.PROMOTE, memberNumber)
        }

        fun demote(memberNumber: Int) {
            sendAdmin(ChatRoomAdminAction.DEMOTE, memberNumber)
        }

        fun moveLeft(memberNumber: Int) {
            sendAdmin(ChatRoomAdminAction.MOVE_LEFT, memberNumber)
        }

        fun moveRight(memberNumber: Int) {
            sendAdmin(ChatRoomAdminAction.MOVE_RIGHT, memberNumber)
        }

        fun shuffle() {
            sendRateLimited(
                "ChatRoomAdmin", gson.toJson(
                    ChatRoomAdminRequest(action = ChatRoomAdminAction.SHUFFLE.wireValue)
                )
            )
        }

        fun swap(target: Int, destination: Int) {
            val json = JsonObject().apply {
                addProperty("Action", ChatRoomAdminAction.SWAP.wireValue)
                addProperty("TargetMemberNumber", target)
                addProperty("DestinationMemberNumber", destination)
            }
            sendRateLimited("ChatRoomAdmin", gson.toJson(json))
        }

        fun updateSettings(room: ChatRoomData) {
            val roomJson = gson.toJsonTree(room)
            sendRateLimited(
                "ChatRoomAdmin", gson.toJson(
                    ChatRoomAdminRequest(action = ChatRoomAdminAction.UPDATE.wireValue, room = roomJson)
                )
            )
        }

        private fun sendAdmin(action: ChatRoomAdminAction, memberNumber: Int) {
            sendRateLimited(
                "ChatRoomAdmin", gson.toJson(
                    ChatRoomAdminRequest(action = action.wireValue, memberNumber = memberNumber)
                )
            )
        }
    }


    inner class ChatScope {
        fun send(content: String) {
            sendMessage(
                OutgoingChatMessage(
                    content = content,
                    type = ChatMessageType.CHAT,
                    dictionary = listOf(
                        bot.inker.bc.razor.protocol.chat.DictionaryEntry.msgId(
                            UUID.randomUUID().toString()
                        )
                    ),
                )
            )
        }

        fun whisper(target: Int, content: String) {
            sendMessage(
                OutgoingChatMessage(
                    content = content,
                    type = ChatMessageType.WHISPER,
                    target = target,
                    dictionary = listOf(
                        bot.inker.bc.razor.protocol.chat.DictionaryEntry.msgId(
                            UUID.randomUUID().toString()
                        )
                    ),
                )
            )
        }

        fun emote(content: String) {
            sendMessage(
                OutgoingChatMessage(
                    content = content,
                    type = ChatMessageType.EMOTE,
                    dictionary = listOf(
                        bot.inker.bc.razor.protocol.chat.DictionaryEntry.msgId(
                            UUID.randomUUID().toString()
                        )
                    ),
                )
            )
        }

        fun sendMessage(message: OutgoingChatMessage) {
            sendRateLimited("ChatRoomChat", gson.toJson(message))
        }

        fun sendGame(data: JsonElement) {
            sendRateLimited("ChatRoomGame", gson.toJson(data))
        }
    }

    inner class CharacterScope {
        fun updateExpression(
            name: String,
            group: String,
            appearance: List<bot.inker.bc.razor.protocol.common.ItemBundle>
        ) {
            val update = ExpressionUpdate(name, group, appearance)
            sendRateLimited("ChatRoomCharacterExpressionUpdate", gson.toJson(update))
        }

        fun updatePose(poses: List<String>) {
            val update = PoseUpdate(poses)
            sendRateLimited("ChatRoomCharacterPoseUpdate", gson.toJson(update))
        }

        fun updateItem(
            target: Int,
            group: String,
            name: String?,
            color: JsonElement? = null,
            difficulty: Int? = null,
            property: JsonElement? = null,
            craft: JsonElement? = null
        ) {
            val update = ItemUpdate(target, group, name, color, difficulty, property, craft)
            sendRateLimited("ChatRoomCharacterItemUpdate", gson.toJson(update))
        }

        fun updateArousal(arousal: ArousalUpdate) {
            sendRateLimited("ChatRoomCharacterArousalUpdate", gson.toJson(arousal))
        }

        fun updateMapData(pos: MapPosition, privateState: JsonElement? = null) {
            val update = MapDataUpdate(pos, privateState)
            sendRateLimited("ChatRoomCharacterMapDataUpdate", gson.toJson(update))
        }

        fun updateAppearance(
            id: String,
            activePose: List<String>?,
            appearance: List<bot.inker.bc.razor.protocol.common.ItemBundle>
        ) {
            val update = CharacterUpdate(id, activePose, appearance)
            sendRateLimited("ChatRoomCharacterUpdate", gson.toJson(update))
        }
    }


    inner class SocialScope {
        fun beep(memberNumber: Int, message: String? = null, isSecret: Boolean? = null) {
            val request = BeepRequest(memberNumber, message = message, isSecret = isSecret)
            sendRateLimited("AccountBeep", gson.toJson(request))
        }

        fun queryOnlineFriends(): CompletableFuture<AccountQueryResult> {
            val future = pendingRequests.create<AccountQueryResult>("AccountQueryResult_OnlineFriends")
            val request = AccountQueryRequest("OnlineFriends")
            sendRateLimited("AccountQuery", gson.toJson(request))
            return future
        }

        fun queryAllowItem(memberNumber: Int): CompletableFuture<Boolean> {
            val future = pendingRequests.create<Boolean>("ChatRoomAllowItem_$memberNumber")
            sendRateLimited("ChatRoomAllowItem", gson.toJson(mapOf("MemberNumber" to memberNumber)))
            return future
        }
    }


    override fun close() {
        accountUpdater.clear()
        rateLimiter.clear()
        pendingRequests.cancelAll()
        eventBus.clear()
        executor.shutdown()
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            executor.shutdownNow()
        }
        transport.disconnect()
    }


    private fun sendRateLimited(event: String, payload: String) {
        rateLimiter.enqueue(event, payload)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder = Builder()
    }

    class TransportBuilder internal constructor(
        private val parent: Builder,
        private val transportName: String?,
        private val transport: SocketTransport?,
    ) {
        private val properties: MutableMap<String, String> = mutableMapOf()

        fun header(name: String, value: String): TransportBuilder = apply { properties["header.$name"] = value }
        fun proxy(host: String, port: Int): TransportBuilder = apply {
            properties["proxy.host"] = host
            properties["proxy.port"] = port.toString()
        }

        fun proxyAuth(username: String, password: String): TransportBuilder = apply {
            properties["proxy.username"] = username
            properties["proxy.password"] = password
        }

        fun reconnection(enabled: Boolean): TransportBuilder = apply { properties["reconnection"] = enabled.toString() }
        fun forceNew(forceNew: Boolean): TransportBuilder = apply { properties["forceNew"] = forceNew.toString() }
        fun reconnectionAttempts(attempts: Int): TransportBuilder =
            apply { properties["reconnectionAttempts"] = attempts.toString() }

        fun reconnectionDelay(delayMs: Long): TransportBuilder =
            apply { properties["reconnectionDelay"] = delayMs.toString() }

        fun reconnectionDelayMax(maxDelayMs: Long): TransportBuilder =
            apply { properties["reconnectionDelayMax"] = maxDelayMs.toString() }

        fun timeout(timeoutMs: Long): TransportBuilder = apply { properties["timeout"] = timeoutMs.toString() }
        fun path(path: String): TransportBuilder = apply { properties["path"] = path }
        fun query(query: String): TransportBuilder = apply { properties["query"] = query }
        fun property(key: String, value: String): TransportBuilder = apply { properties[key] = value }

        fun buildTransport(): Builder {
            parent.applyTransport(transport, transportName, properties.toMap())
            return parent
        }
    }

    class Builder {
        private var transport: SocketTransport? = null
        private var transportName: String = "socketio"
        private var connectionProperties: Map<String, String> = emptyMap()
        private var serverUrl: String = "http://localhost:4288"
        private var rateLimitMaxMessages: Int = 14
        private var rateLimitWindowMs: Long = 1200L
        private var accountSyncDebounceMs: Long = 2000L
        private var accountSyncMaxWaitMs: Long = 8000L
        private var requestTimeoutMs: Long = 30000L

        @PublishedApi
        internal val deferredSubscriptions: MutableList<(RazorClient) -> Subscription> = mutableListOf()

        fun transport(): TransportBuilder = TransportBuilder(this, "socketio", null)
        fun transport(name: String): TransportBuilder = TransportBuilder(this, name, null)
        fun transport(transport: SocketTransport): TransportBuilder = TransportBuilder(this, null, transport)

        internal fun applyTransport(transport: SocketTransport?, name: String?, properties: Map<String, String>) {
            if (transport != null) this.transport = transport
            if (name != null) this.transportName = name
            this.connectionProperties = properties
        }

        fun serverUrl(url: String): Builder = apply { this.serverUrl = url }
        fun rateLimitMaxMessages(max: Int): Builder = apply { this.rateLimitMaxMessages = max }
        fun rateLimitWindowMs(windowMs: Long): Builder = apply { this.rateLimitWindowMs = windowMs }
        fun accountSyncDebounceMs(debounceMs: Long): Builder = apply { this.accountSyncDebounceMs = debounceMs }
        fun accountSyncMaxWaitMs(maxWaitMs: Long): Builder = apply { this.accountSyncMaxWaitMs = maxWaitMs }
        fun requestTimeoutMs(timeoutMs: Long): Builder = apply { this.requestTimeoutMs = timeoutMs }

        inline fun <reified T : RazorEvent> on(noinline handler: (T) -> Unit): Builder = apply {
            val eventType = T::class
            deferredSubscriptions.add { client -> client.on(eventType, handler) }
        }

        fun onAny(handler: (RazorEvent) -> Unit): Builder = apply {
            deferredSubscriptions.add { client -> client.onAny(handler) }
        }

        fun build(): RazorClient {
            val resolvedTransport = transport ?: SocketTransports.create(transportName)
            val config = RazorClientConfig(
                serverUrl = serverUrl,
                rateLimitMaxMessages = rateLimitMaxMessages,
                rateLimitWindowMs = rateLimitWindowMs,
                accountSyncDebounceMs = accountSyncDebounceMs,
                accountSyncMaxWaitMs = accountSyncMaxWaitMs,
                requestTimeoutMs = requestTimeoutMs,
            )
            val client = RazorClient(resolvedTransport, config, connectionProperties)
            deferredSubscriptions.forEach { it(client) }
            return client
        }
    }
}
