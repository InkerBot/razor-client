package bot.inker.bc.razor.tui.screen

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.tui.ColorScheme
import bot.inker.bc.razor.tui.TuiApplication
import bot.inker.bc.razor.tui.TuiConfig
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.*

class ColorSchemeScreen(private val app: TuiApplication) : Screen {
    private lateinit var window: BasicWindow
    private val colorBoxes = mutableMapOf<String, ComboBox<String>>()
    private lateinit var previewPanel: PreviewPanel

    private data class ColorEntry(val key: String, val label: String)

    private val windowEntries = listOf(
        ColorEntry("windowForeground", "Foreground"),
        ColorEntry("windowBackground", "Background"),
    )

    private val chatColorEntries = listOf(
        ColorEntry("chatMessage", "Chat"),
        ColorEntry("whisperMessage", "Whisper"),
        ColorEntry("emoteMessage", "Emote"),
        ColorEntry("actionMessage", "Action"),
        ColorEntry("activityMessage", "Activity"),
        ColorEntry("serverMessage", "Server"),
        ColorEntry("statusMessage", "Status"),
        ColorEntry("hiddenMessage", "Hidden"),
        ColorEntry("localMessage", "Local"),
        ColorEntry("systemMessage", "System"),
        ColorEntry("errorMessage", "Error"),
        ColorEntry("helpMessage", "Help"),
    )

    private val memberListEntries = listOf(
        ColorEntry("memberListHeader", "Header"),
        ColorEntry("memberListSeparator", "Separator"),
        ColorEntry("memberListAdmin", "Admin"),
        ColorEntry("memberListDefault", "Default"),
    )

    private val statusBarEntries = listOf(
        ColorEntry("statusBarBackground", "Background"),
        ColorEntry("statusBarForeground", "Foreground"),
    )

    private val ansiColorNames = TextColor.ANSI.values().map { it.name }

    override fun createWindow(): BasicWindow {
        val scheme = app.config.colorScheme

        window = BasicWindow()
        window.setHints(listOf(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS, Window.Hint.NO_POST_RENDERING))

        val mainPanel = Panel(LinearLayout(Direction.VERTICAL))

        // Title
        mainPanel.addComponent(Label("Color Scheme"))
        mainPanel.addComponent(EmptySpace())

        // Preset buttons
        val presetPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        presetPanel.addComponent(Label("Presets:"))
        presetPanel.addComponent(Button("Default") { applyPreset(ColorScheme.default()) })
        presetPanel.addComponent(Button("Dark") { applyPreset(ColorScheme.dark()) })
        presetPanel.addComponent(Button("Light") { applyPreset(ColorScheme.light()) })
        mainPanel.addComponent(presetPanel)
        mainPanel.addComponent(EmptySpace())

        // Two-column layout
        val columnsPanel = Panel(LinearLayout(Direction.HORIZONTAL))

        // Left column: window + chat colors
        val leftColumn = Panel(GridLayout(2))
        addSectionHeader(leftColumn, "Window")
        for (entry in windowEntries) {
            addColorEntry(leftColumn, entry, getSchemeValue(scheme, entry.key))
        }
        addSectionHeader(leftColumn, "Chat Colors")
        for (entry in chatColorEntries) {
            addColorEntry(leftColumn, entry, getSchemeValue(scheme, entry.key))
        }
        columnsPanel.addComponent(leftColumn)

        // Spacer
        columnsPanel.addComponent(EmptySpace(TerminalSize(2, 1)))

        // Right column: member list + status bar
        val rightColumn = Panel(GridLayout(2))
        addSectionHeader(rightColumn, "Member List")
        for (entry in memberListEntries) {
            addColorEntry(rightColumn, entry, getSchemeValue(scheme, entry.key))
        }
        addSectionHeader(rightColumn, "Status Bar")
        for (entry in statusBarEntries) {
            addColorEntry(rightColumn, entry, getSchemeValue(scheme, entry.key))
        }
        rightColumn.addComponent(EmptySpace())
        rightColumn.addComponent(EmptySpace())
        columnsPanel.addComponent(rightColumn)

        mainPanel.addComponent(columnsPanel)
        mainPanel.addComponent(EmptySpace())

        // Preview
        previewPanel = PreviewPanel()
        mainPanel.addComponent(previewPanel.withBorder(Borders.singleLine("Preview")))
        updatePreview()

        mainPanel.addComponent(EmptySpace())

        // Buttons
        val buttonPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        buttonPanel.addComponent(Button("Save") { onSave() })
        buttonPanel.addComponent(Button("Back") { app.navigateToSettings() })
        mainPanel.addComponent(buttonPanel)

        window.component = mainPanel
        return window
    }

    private fun addSectionHeader(panel: Panel, title: String) {
        panel.addComponent(
            Label("-- $title --")
                .setLayoutData(GridLayout.createHorizontallyFilledLayoutData(2))
        )
    }

    private fun addColorEntry(panel: Panel, entry: ColorEntry, currentValue: String) {
        panel.addComponent(Label("${entry.label}:"))
        val comboBox = ComboBox<String>()
        for (color in ansiColorNames) {
            comboBox.addItem(color)
        }
        val index = ansiColorNames.indexOfFirst { it.equals(currentValue, ignoreCase = true) }
        if (index >= 0) comboBox.selectedIndex = index
        comboBox.isReadOnly = true
        comboBox.addListener { _, _, _ -> updatePreview() }
        colorBoxes[entry.key] = comboBox
        panel.addComponent(comboBox)
    }

    private fun applyPreset(scheme: ColorScheme) {
        for ((key, comboBox) in colorBoxes) {
            val value = getSchemeValue(scheme, key)
            val index = ansiColorNames.indexOfFirst { it.equals(value, ignoreCase = true) }
            if (index >= 0) comboBox.selectedIndex = index
        }
        updatePreview()
    }

    private fun updatePreview() {
        fun resolve(key: String): TextColor {
            val box = colorBoxes[key] ?: return TextColor.ANSI.DEFAULT
            return ColorScheme.resolve(box.selectedItem)
        }

        previewPanel.lines = listOf(
            "[Alice] Hello everyone!" to resolve("chatMessage"),
            "[Bob -> Alice] Hey there" to resolve("whisperMessage"),
            "* Alice waves" to resolve("emoteMessage"),
            "(Action performed)" to resolve("actionMessage"),
            "[Server] Welcome!" to resolve("serverMessage"),
            "-- System message --" to resolve("systemMessage"),
            "[Error] Something failed" to resolve("errorMessage"),
            "/help command list" to resolve("helpMessage"),
        )
    }

    private fun onSave() {
        val newScheme = buildScheme()
        val config = app.config.copy(colorScheme = newScheme)
        app.config = config
        TuiConfig.save(config)
        app.applyTheme()
        app.navigateToSettings()
    }

    private fun buildScheme(): ColorScheme = ColorScheme(
        windowForeground = colorBoxes["windowForeground"]?.selectedItem ?: "DEFAULT",
        windowBackground = colorBoxes["windowBackground"]?.selectedItem ?: "DEFAULT",
        chatMessage = colorBoxes["chatMessage"]?.selectedItem ?: "DEFAULT",
        whisperMessage = colorBoxes["whisperMessage"]?.selectedItem ?: "MAGENTA",
        emoteMessage = colorBoxes["emoteMessage"]?.selectedItem ?: "YELLOW",
        actionMessage = colorBoxes["actionMessage"]?.selectedItem ?: "WHITE_BRIGHT",
        activityMessage = colorBoxes["activityMessage"]?.selectedItem ?: "WHITE_BRIGHT",
        serverMessage = colorBoxes["serverMessage"]?.selectedItem ?: "RED",
        statusMessage = colorBoxes["statusMessage"]?.selectedItem ?: "WHITE_BRIGHT",
        hiddenMessage = colorBoxes["hiddenMessage"]?.selectedItem ?: "WHITE_BRIGHT",
        localMessage = colorBoxes["localMessage"]?.selectedItem ?: "CYAN",
        systemMessage = colorBoxes["systemMessage"]?.selectedItem ?: "GREEN",
        errorMessage = colorBoxes["errorMessage"]?.selectedItem ?: "RED",
        helpMessage = colorBoxes["helpMessage"]?.selectedItem ?: "CYAN",
        memberListHeader = colorBoxes["memberListHeader"]?.selectedItem ?: "CYAN",
        memberListSeparator = colorBoxes["memberListSeparator"]?.selectedItem ?: "WHITE_BRIGHT",
        memberListAdmin = colorBoxes["memberListAdmin"]?.selectedItem ?: "YELLOW",
        memberListDefault = colorBoxes["memberListDefault"]?.selectedItem ?: "DEFAULT",
        statusBarBackground = colorBoxes["statusBarBackground"]?.selectedItem ?: "BLUE",
        statusBarForeground = colorBoxes["statusBarForeground"]?.selectedItem ?: "WHITE",
    )

    private fun getSchemeValue(scheme: ColorScheme, key: String): String = when (key) {
        "windowForeground" -> scheme.windowForeground
        "windowBackground" -> scheme.windowBackground
        "chatMessage" -> scheme.chatMessage
        "whisperMessage" -> scheme.whisperMessage
        "emoteMessage" -> scheme.emoteMessage
        "actionMessage" -> scheme.actionMessage
        "activityMessage" -> scheme.activityMessage
        "serverMessage" -> scheme.serverMessage
        "statusMessage" -> scheme.statusMessage
        "hiddenMessage" -> scheme.hiddenMessage
        "localMessage" -> scheme.localMessage
        "systemMessage" -> scheme.systemMessage
        "errorMessage" -> scheme.errorMessage
        "helpMessage" -> scheme.helpMessage
        "memberListHeader" -> scheme.memberListHeader
        "memberListSeparator" -> scheme.memberListSeparator
        "memberListAdmin" -> scheme.memberListAdmin
        "memberListDefault" -> scheme.memberListDefault
        "statusBarBackground" -> scheme.statusBarBackground
        "statusBarForeground" -> scheme.statusBarForeground
        else -> "DEFAULT"
    }

    override fun onEvent(event: RazorEvent) {
        // Color scheme screen doesn't need to handle events
    }

    private class PreviewPanel : AbstractComponent<PreviewPanel>() {
        var lines: List<Pair<String, TextColor>> = emptyList()
            set(value) {
                field = value
                invalidate()
            }

        override fun createDefaultRenderer(): ComponentRenderer<PreviewPanel> {
            return object : ComponentRenderer<PreviewPanel> {
                override fun getPreferredSize(component: PreviewPanel): TerminalSize {
                    val width = component.lines.maxOfOrNull { it.first.length }?.coerceAtLeast(40) ?: 40
                    return TerminalSize(width, component.lines.size.coerceAtLeast(1))
                }

                override fun drawComponent(graphics: TextGUIGraphics, component: PreviewPanel) {
                    graphics.fill(' ')
                    for ((idx, line) in component.lines.withIndex()) {
                        if (idx >= graphics.size.rows) break
                        graphics.setForegroundColor(line.second)
                        graphics.putString(0, idx, line.first.take(graphics.size.columns))
                    }
                    graphics.setForegroundColor(TextColor.ANSI.DEFAULT)
                }
            }
        }
    }
}