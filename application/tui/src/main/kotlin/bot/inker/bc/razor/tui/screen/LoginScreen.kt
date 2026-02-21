package bot.inker.bc.razor.tui.screen

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.protocol.auth.LoginResult
import bot.inker.bc.razor.tui.TuiApplication
import bot.inker.bc.razor.tui.TuiConfig
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton
import org.slf4j.LoggerFactory

class LoginScreen(
    private val app: TuiApplication,
    private val initialError: String? = null,
) : Screen {
    private lateinit var window: BasicWindow
    private lateinit var connectionStatusLabel: Label
    private lateinit var usernameInput: TextBox
    private lateinit var passwordInput: TextBox
    private lateinit var statusLabel: Label
    private lateinit var loginButton: Button

    private val logger = LoggerFactory.getLogger(LoginScreen::class.java)
    private var connected = false

    override fun createWindow(): BasicWindow {
        window = BasicWindow("Razor Client - Login")
        window.setHints(listOf(Window.Hint.CENTERED))

        val panel = Panel(GridLayout(2))

        // Connection status row at the top
        panel.addComponent(Label("Server:"))
        connectionStatusLabel = Label("Connecting...")
        panel.addComponent(connectionStatusLabel)

        panel.addComponent(EmptySpace())
        panel.addComponent(Separator(Direction.HORIZONTAL).setLayoutData(GridLayout.createHorizontallyFilledLayoutData(1)))

        panel.addComponent(Label("Username:"))
        usernameInput = TextBox(TerminalSize(30, 1), app.config.lastUsername)
        panel.addComponent(usernameInput)

        panel.addComponent(Label("Password:"))
        passwordInput = TextBox(TerminalSize(30, 1), "").setMask('*')
        panel.addComponent(passwordInput)

        panel.addComponent(EmptySpace())
        panel.addComponent(EmptySpace())

        statusLabel = Label("")
        panel.addComponent(statusLabel.setLayoutData(GridLayout.createHorizontallyFilledLayoutData(2)))

        val buttonPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        loginButton = Button("Login") { onLogin() }
        loginButton.isEnabled = false
        buttonPanel.addComponent(loginButton)
        buttonPanel.addComponent(Button("Settings") { app.navigateToSettings() })
        buttonPanel.addComponent(Button("Quit") { app.quit() })
        panel.addComponent(buttonPanel.setLayoutData(GridLayout.createHorizontallyFilledLayoutData(2)))

        window.component = panel
        return window
    }

    override fun onEnter() {
        // Check if already connected
        if (app.client != null) {
            // Client exists; we might already be connected or still connecting
        }
        if (initialError != null) {
            app.gui.guiThread.invokeLater {
                statusLabel.text = initialError
            }
        }
    }

    private fun onLogin() {
        val username = usernameInput.text.trim()
        val password = passwordInput.text

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.text = "Username and password required"
            return
        }

        statusLabel.text = "Logging in..."
        loginButton.isEnabled = false

        val config = app.config.copy(lastUsername = username)
        app.config = config
        TuiConfig.save(config)

        val client = app.client ?: return
        client.login(username, password).thenAccept { result ->
            app.gui.guiThread.invokeLater {
                when (result) {
                    is LoginResult.Success -> {
                        app.navigateToLobby()
                    }
                    is LoginResult.Error -> {
                        loginButton.isEnabled = true
                        statusLabel.text = ""
                        MessageDialogBuilder()
                            .setTitle("Login Failed")
                            .setText(result.message)
                            .addButton(MessageDialogButton.OK)
                            .build()
                            .showDialog(app.gui)
                    }
                }
            }
        }.exceptionally { ex ->
            logger.error("Login failed with exception", ex)
            app.gui.guiThread.invokeLater {
                loginButton.isEnabled = true
                statusLabel.text = "Login error: ${ex.cause?.message ?: ex.message}"
            }
            null
        }
    }

    override fun onEvent(event: RazorEvent) {
        when (event) {
            is RazorEvent.Connected -> {
                connected = true
                connectionStatusLabel.text = "Connected"
                loginButton.isEnabled = true
            }
            is RazorEvent.LoginQueue -> {
                statusLabel.text = "Queue position: ${event.position}"
            }
            is RazorEvent.Disconnected -> {
                connected = false
                connectionStatusLabel.text = "Disconnected"
                loginButton.isEnabled = false
                statusLabel.text = event.reason ?: "Connection lost"
            }
            is RazorEvent.ForceDisconnect -> {
                connected = false
                connectionStatusLabel.text = "Disconnected"
                loginButton.isEnabled = false
                statusLabel.text = event.reason.toString()
            }
            is RazorEvent.Reconnecting -> {
                connected = false
                connectionStatusLabel.text = "Reconnecting..."
                loginButton.isEnabled = false
            }
            else -> {}
        }
    }
}