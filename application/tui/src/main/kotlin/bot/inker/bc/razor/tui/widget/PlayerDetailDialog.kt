package bot.inker.bc.razor.tui.widget

import bot.inker.bc.razor.state.CharacterState
import bot.inker.bc.razor.tui.ColorScheme
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.dialogs.DialogWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PlayerDetailDialog(
    character: CharacterState,
    scheme: ColorScheme,
    disableShadows: Boolean?,
    terminalSize: TerminalSize,
) : DialogWindow("Player Info") {

    init {
        setCloseWindowWithEscape(true)

        val hints = mutableSetOf(Window.Hint.CENTERED, Window.Hint.MODAL)
        if (disableShadows != false) {
            hints.add(Window.Hint.NO_POST_RENDERING)
        }
        setHints(hints)

        val dialogWidth = (terminalSize.columns * 2 / 3).coerceIn(40, 120)
        val descHeight = (terminalSize.rows * 2 / 5).coerceIn(5, 30)

        val mainPanel = Panel(LinearLayout(Direction.VERTICAL))

        // Info grid
        val infoGrid = Panel(GridLayout(2))
        val layoutFill = GridLayout.createLayoutData(
            GridLayout.Alignment.BEGINNING,
            GridLayout.Alignment.CENTER,
            true,
            false,
        )

        fun addRow(label: String, value: String) {
            infoGrid.addComponent(Label("$label:"))
            infoGrid.addComponent(Label(value).setLayoutData(layoutFill))
        }

        addRow("Name", character.displayName)

        val nick = character.nickname?.trim()?.take(20)
        if (!nick.isNullOrEmpty() && nick != character.name) {
            addRow("Account", character.name)
        }

        addRow("Member #", character.memberNumber.toString())

        character.title?.takeIf { it.isNotBlank() }?.let { addRow("Title", it) }

        if (character.creation > 0L) {
            val formatted = Instant.ofEpochMilli(character.creation)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            addRow("Created", formatted)
        }

        val ownerText = character.ownership?.name
            ?: character.owner?.takeIf { it.isNotBlank() }
        if (ownerText != null) {
            addRow("Owner", ownerText)
        }

        if (character.lovership.isNotEmpty()) {
            val loverNames = character.lovership.joinToString(", ") { it.name }
            addRow("Lover(s)", loverNames)
        }

        character.difficulty?.let { diff ->
            val label = when (diff.level) {
                0 -> "Roleplay"
                1 -> "Regular"
                2 -> "Hardcore"
                3 -> "Extreme"
                else -> "Level ${diff.level}"
            }
            addRow("Difficulty", label)
        }

        mainPanel.addComponent(infoGrid)

        // Description as scrollable read-only TextBox
        character.description?.takeIf { it.isNotBlank() }?.let { desc ->
            mainPanel.addComponent(Label("Description:"))
            val descBox = TextBox(TerminalSize(dialogWidth, descHeight), desc, TextBox.Style.MULTI_LINE)
                .setReadOnly(true)
                .setLayoutData(
                    LinearLayout.createLayoutData(LinearLayout.Alignment.FILL)
                )
            mainPanel.addComponent(descBox)
        }

        // Close button
        mainPanel.addComponent(EmptySpace())
        val buttonPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        buttonPanel.addComponent(Button("Close") { close() })
        mainPanel.addComponent(
            buttonPanel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.END))
        )

        component = mainPanel
    }

    companion object {
        fun show(
            gui: WindowBasedTextGUI,
            character: CharacterState,
            scheme: ColorScheme,
            disableShadows: Boolean?,
        ) {
            val terminalSize = gui.screen.terminalSize
            val dialog = PlayerDetailDialog(character, scheme, disableShadows, terminalSize)
            dialog.showDialog(gui)
        }
    }
}
