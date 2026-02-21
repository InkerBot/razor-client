package bot.inker.bc.razor.tui

import bot.inker.bc.razor.RazorClient
import bot.inker.bc.razor.i18n.ChatMessageResolver
import bot.inker.bc.razor.i18n.TranslationManager
import bot.inker.bc.razor.tui.event.TuiEventBridge
import bot.inker.bc.razor.tui.screen.*
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.graphics.SimpleTheme
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.ExtendedTerminal
import com.googlecode.lanterna.terminal.MouseCaptureMode
import com.googlecode.lanterna.terminal.Terminal
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

class TuiApplication(
    private val terminalOutput: OutputStream,
    private val terminalInput: InputStream,
) {
    private val logger = LoggerFactory.getLogger(TuiApplication::class.java)

    var config: TuiConfig = TuiConfig.load()
    var client: RazorClient? = null
        private set

    val translations: TranslationManager = TranslationManager()
    val messageResolver: ChatMessageResolver = ChatMessageResolver(translations)

    lateinit var gui: MultiWindowTextGUI
        private set

    @Volatile
    private var nextScreen: Screen? = null

    @Volatile
    private var running = true

    private lateinit var terminalScreen: TerminalScreen
    private val screenManager = ScreenManager()
    private lateinit var eventBridge: TuiEventBridge

    fun start() {
        val terminal = createTerminal()
        terminalScreen = TerminalScreen(terminal)
        terminalScreen.startScreen()

        if (terminal is ExtendedTerminal) {
            try {
                terminal.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE)
            } catch (_: Exception) {
                // Mouse capture not supported on this terminal type
            }
        }

        gui = MultiWindowTextGUI(terminalScreen)
        applyTheme()
        eventBridge = TuiEventBridge(gui, screenManager)

        // Auto-connect with saved/default config and go straight to login
        connect(config)
        nextScreen = LoginScreen(this)

        while (running) {
            val screen = nextScreen ?: break
            nextScreen = null

            screenManager.currentScreen?.onLeave()
            screenManager.currentScreen = screen

            val window = screen.createWindow()
            gui.addWindow(window)
            screen.onEnter()
            window.waitUntilClosed()
        }

        screenManager.currentScreen?.onLeave()
        screenManager.currentScreen = null
        closeClient()
        terminalScreen.stopScreen()
    }

    fun connect(config: TuiConfig) {
        try {
            closeClient()

            val builder = RazorClient.builder()
                .serverUrl(config.serverUrl)

            val transportBuilder = builder.transport()
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

            val newClient = builder.build()
            client = newClient
            eventBridge.attach(newClient)
            newClient.connect()
        } catch (e: Exception) {
            logger.error("Failed to connect", e)
        }
    }

    fun disconnect() {
        closeClient()
        navigateToLogin("Disconnected")
    }

    fun navigateToSettings() {
        nextScreen = SettingsScreen(this)
        gui.activeWindow?.close()
    }

    fun navigateToColorScheme() {
        nextScreen = ColorSchemeScreen(this)
        gui.activeWindow?.close()
    }

    fun navigateToLogin(errorMessage: String? = null) {
        nextScreen = LoginScreen(this, errorMessage)
        gui.activeWindow?.close()
    }

    fun reconnect() {
        closeClient()
        connect(config)
    }

    fun navigateToLobby() {
        nextScreen = LobbyScreen(this)
        gui.activeWindow?.close()
    }

    fun navigateToChatRoom() {
        nextScreen = ChatRoomScreen(this)
        gui.activeWindow?.close()
    }

    fun quit() {
        running = false
        closeClient()
        gui.activeWindow?.close()
    }

    private fun createTerminal(): Terminal {
        // Try text terminal first (real terminal / SSH session)
        try {
            return DefaultTerminalFactory(terminalOutput, terminalInput, Charsets.UTF_8)
                .setForceTextTerminal(true)
                .createTerminal()
        } catch (_: Exception) {
            // /dev/tty not available
        }

        // Try Swing terminal (GUI environment)
        try {
            return DefaultTerminalFactory(terminalOutput, terminalInput, Charsets.UTF_8)
                .createTerminalEmulator()
        } catch (_: Exception) {
            // No display available
        }

        throw IllegalStateException(
            "Cannot create terminal. Please run from an interactive terminal (with /dev/tty) " +
                    "or a graphical environment (with a working DISPLAY)."
        )
    }

    fun applyTheme() {
        val scheme = config.colorScheme
        val fg = ColorScheme.resolve(scheme.windowForeground)
        val bg = ColorScheme.resolve(scheme.windowBackground)
        val selectedFg = bg
        val selectedBg = fg
        val theme = SimpleTheme.makeTheme(
            true, fg, bg, fg, bg, selectedFg, selectedBg, bg
        )
        gui.theme = theme
    }

    private fun closeClient() {
        eventBridge.detach()
        client?.close()
        client = null
    }
}
