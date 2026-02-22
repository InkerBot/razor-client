package bot.inker.bc.razor.telegram

import bot.inker.bc.razor.RazorClient
import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.i18n.ChatMessageResolver
import bot.inker.bc.razor.i18n.TranslationManager
import bot.inker.bc.razor.protocol.auth.LoginResult
import bot.inker.bc.razor.protocol.room.ChatRoomCreateRequest
import bot.inker.bc.razor.protocol.room.RoomCreateResult
import bot.inker.bc.razor.protocol.room.RoomJoinResult
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication

class TelegramApplication {
    private val logger = LoggerFactory.getLogger(TelegramApplication::class.java)

    @Volatile
    var config: TelegramConfig = TelegramConfig.load()
        private set
    private val translations: TranslationManager = TranslationManager()
    private val messageResolver: ChatMessageResolver = ChatMessageResolver(translations)

    private val telegramClient = OkHttpTelegramClient(config.botToken)
    private val topicManager = ForumTopicManager(config, telegramClient)
    private val messageSender = TelegramMessageSender(config, telegramClient, topicManager)
    private val messageIdMapping = MessageIdMapping()
    private val commandHandler = BotCommandHandler(this, messageSender)
    private val bcEventBridge = BcEventBridge(messageSender, messageResolver, messageIdMapping, commandHandler)

    // Media components (conditional on config)
    private var mediaFileServer: MediaFileServer? = null
    private var mediaDownloader: TelegramMediaDownloader? = null

    private val telegramEventBridge: TelegramEventBridge
    private val bot: TelegramBot

    init {
        if (config.mediaServerEnabled) {
            val server = MediaFileServer(config)
            mediaFileServer = server
            mediaDownloader = TelegramMediaDownloader(config, telegramClient, server)
        }

        telegramEventBridge = TelegramEventBridge(config, topicManager, messageIdMapping, commandHandler, mediaDownloader)
        bot = TelegramBot(config.botToken, telegramEventBridge)
    }

    private val watchdog = ConnectionWatchdog(config) { connectAndLogin() }

    @Volatile
    private var client: RazorClient? = null

    private var botsApplication: TelegramBotsLongPollingApplication? = null

    fun start() {
        logger.info("Starting Telegram bridge...")

        require(config.botToken.isNotEmpty()) { "botToken must be configured in razor-telegram.json" }
        require(config.chatId != 0L) { "chatId must be configured in razor-telegram.json" }
        require(config.accountName.isNotEmpty()) { "accountName must be configured in razor-telegram.json" }
        require(config.password.isNotEmpty()) { "password must be configured in razor-telegram.json" }
        require(config.roomName.isNotEmpty()) { "roomName must be configured in razor-telegram.json" }

        topicManager.loadCache()

        // Start media file server if enabled
        mediaFileServer?.start()

        // Start Telegram bot long polling
        val app = TelegramBotsLongPollingApplication()
        app.registerBot(config.botToken, bot)
        botsApplication = app
        logger.info("Telegram bot registered and polling")

        // Start BC connection
        connectAndLogin()

        // Start watchdog
        watchdog.start()

        logger.info("Telegram bridge started successfully")
    }

    fun stop() {
        logger.info("Stopping Telegram bridge...")
        watchdog.stop()
        bcEventBridge.detach()
        closeClient()
        try {
            botsApplication?.close()
        } catch (e: Exception) {
            logger.warn("Error closing Telegram bot application", e)
        }
        mediaFileServer?.close()
        logger.info("Telegram bridge stopped")
    }

    @Synchronized
    internal fun connectAndLogin() {
        try {
            closeClient()

            val builder = RazorClient.builder()
                .serverUrl(config.serverUrl)

            val transportBuilder = builder.transport()
                .reconnection(true)
                .reconnectionAttempts(config.socketIoReconnectionAttempts)
                .reconnectionDelay(config.socketIoReconnectionDelay)
                .reconnectionDelayMax(config.socketIoReconnectionDelayMax)

            if (config.origin.isNotEmpty()) {
                transportBuilder.header("Origin", config.origin)
            }
            if (config.referer.isNotEmpty()) {
                transportBuilder.header("Referer", config.referer)
            }
            if (config.proxyHost.isNotEmpty() && config.proxyPort > 0) {
                transportBuilder.proxy(config.proxyHost, config.proxyPort)
            }
            transportBuilder.buildTransport()

            // Wire up watchdog event forwarding before build
            builder.onAny { event -> watchdog.onEvent(event) }

            val newClient = builder.build()
            client = newClient
            telegramEventBridge.client = newClient
            bcEventBridge.attach(newClient)

            // Subscribe to Connected event to auto-login
            newClient.on<RazorEvent.Connected> {
                logger.info("Connected to BC server, logging in as {}", config.accountName)
                newClient.login(config.accountName, config.password).thenAccept { result ->
                    when (result) {
                        is LoginResult.Success -> {
                            logger.info("Logged in successfully, joining room: {}", config.roomName)
                            joinOrCreateRoom(newClient)
                        }

                        is LoginResult.Error -> {
                            logger.error("Login failed: {}", result.message)
                        }
                    }
                }
            }

            newClient.connect()
            logger.info("BC client connecting to {}", config.serverUrl)
        } catch (e: Exception) {
            logger.error("Failed to connect and login", e)
        }
    }

    private fun joinOrCreateRoom(client: RazorClient) {
        client.room.join(config.roomName).thenAccept { joinResult ->
            when (joinResult) {
                is RoomJoinResult.Success -> {
                    logger.info("Joined room: {}", config.roomName)
                }

                is RoomJoinResult.Error -> {
                    logger.info("Join failed ({}), creating room: {}", joinResult.message, config.roomName)
                    createRoom(client)
                }
            }
        }.exceptionally { e ->
            logger.error("Error joining room", e)
            null
        }
    }

    private fun createRoom(client: RazorClient) {
        val memberNumber = client.player?.memberNumber
        if (memberNumber == null) {
            logger.error("Cannot create room: player not logged in")
            return
        }
        val rc = config.room
        val adminList = if (rc.admin.isEmpty()) listOf(memberNumber)
        else (rc.admin + memberNumber).distinct()
        val request = ChatRoomCreateRequest(
            name = config.roomName,
            description = rc.description,
            background = rc.background,
            limit = rc.limit.coerceIn(2, 10),
            admin = adminList,
            ban = rc.ban,
            whitelist = rc.whitelist,
            visibility = rc.visibility.ifEmpty { listOf("All") },
            access = rc.access.ifEmpty { listOf("All") },
            blockCategory = rc.blockCategory,
            language = rc.language,
            space = rc.space,
            game = "",
        )
        client.room.create(request).thenAccept { createResult ->
            when (createResult) {
                is RoomCreateResult.Success ->
                    logger.info("Created and joined room: {}", config.roomName)

                is RoomCreateResult.Error ->
                    logger.error("Failed to create room: {}", createResult.message)
            }
        }.exceptionally { e ->
            logger.error("Error creating room", e)
            null
        }
    }

    fun getClient(): RazorClient? = client

    fun updateConfig(newConfig: TelegramConfig) {
        config = newConfig
        TelegramConfig.save(newConfig)
        logger.info("Config saved to razor-telegram.json")
    }

    private fun closeClient() {
        bcEventBridge.detach()
        telegramEventBridge.client = null
        client?.close()
        client = null
    }
}
