package bot.inker.bc.razor.tui.screen

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.tui.TuiApplication
import bot.inker.bc.razor.tui.TuiConfig
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*

class SettingsScreen(private val app: TuiApplication) : Screen {
    private lateinit var window: BasicWindow
    private lateinit var serverUrlInput: TextBox
    private lateinit var originInput: TextBox
    private lateinit var refererInput: TextBox
    private lateinit var proxyHostInput: TextBox
    private lateinit var proxyPortInput: TextBox
    private lateinit var statusLabel: Label

    override fun createWindow(): BasicWindow {
        val config = app.config

        window = BasicWindow("Razor Client - Settings")
        window.setHints(listOf(Window.Hint.CENTERED))

        val panel = Panel(GridLayout(2))

        panel.addComponent(Label("Server URL:"))
        serverUrlInput = TextBox(TerminalSize(50, 1), config.serverUrl)
        panel.addComponent(serverUrlInput)

        panel.addComponent(Label("Origin:"))
        originInput = TextBox(TerminalSize(50, 1), config.origin)
        panel.addComponent(originInput)

        panel.addComponent(Label("Referer:"))
        refererInput = TextBox(TerminalSize(50, 1), config.referer)
        panel.addComponent(refererInput)

        panel.addComponent(Label("Proxy Host:"))
        proxyHostInput = TextBox(TerminalSize(50, 1), config.proxyHost)
        panel.addComponent(proxyHostInput)

        panel.addComponent(Label("Proxy Port:"))
        proxyPortInput = TextBox(TerminalSize(50, 1), if (config.proxyPort > 0) config.proxyPort.toString() else "")
        panel.addComponent(proxyPortInput)

        panel.addComponent(EmptySpace())
        panel.addComponent(EmptySpace())

        statusLabel = Label("")
        panel.addComponent(statusLabel.setLayoutData(GridLayout.createHorizontallyFilledLayoutData(2)))

        val buttonPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        buttonPanel.addComponent(Button("Save & Reconnect") { onSaveAndReconnect() })
        buttonPanel.addComponent(Button("Back") { app.navigateToLogin() })
        panel.addComponent(buttonPanel.setLayoutData(GridLayout.createHorizontallyFilledLayoutData(2)))

        window.component = panel
        return window
    }

    private fun onSaveAndReconnect() {
        val serverUrl = serverUrlInput.text.trim()
        if (serverUrl.isEmpty()) {
            statusLabel.text = "Server URL is required"
            return
        }

        val config = TuiConfig(
            serverUrl = serverUrl,
            origin = originInput.text.trim(),
            referer = refererInput.text.trim(),
            proxyHost = proxyHostInput.text.trim(),
            proxyPort = proxyPortInput.text.trim().toIntOrNull() ?: 0,
            lastUsername = app.config.lastUsername,
        )
        app.config = config
        TuiConfig.save(config)

        app.reconnect()
        app.navigateToLogin()
    }

    override fun onEvent(event: RazorEvent) {
        // Settings screen doesn't need to handle events
    }
}