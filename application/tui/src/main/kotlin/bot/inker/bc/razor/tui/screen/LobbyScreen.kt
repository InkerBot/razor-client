package bot.inker.bc.razor.tui.screen

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.protocol.room.ChatRoomSearchRequest
import bot.inker.bc.razor.protocol.room.ChatRoomSearchResult
import bot.inker.bc.razor.protocol.room.RoomJoinResult
import bot.inker.bc.razor.tui.TuiApplication
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton
import com.googlecode.lanterna.gui2.table.Table
import org.slf4j.LoggerFactory

class LobbyScreen(private val app: TuiApplication) : Screen {
    private val logger = LoggerFactory.getLogger(LobbyScreen::class.java)
    private lateinit var window: BasicWindow
    private lateinit var searchInput: TextBox
    private lateinit var spaceComboBox: ComboBox<String>
    private lateinit var roomTable: Table<String>
    private lateinit var statusLabel: Label
    private var rooms: List<ChatRoomSearchResult> = emptyList()
    private var searching = false

    private val spaceEntries = listOf(
        "Classical" to "",
        "Mixed" to "X",
        "Male" to "M",
        "Asylum" to "Asylum",
    )

    override fun createWindow(): BasicWindow {
        window = BasicWindow("Razor Client - Lobby")
        val hints = mutableListOf<Window.Hint>(Window.Hint.EXPANDED)
        if (app.config.disableShadows != false) {
            hints.add(Window.Hint.NO_POST_RENDERING)
        }
        window.setHints(hints)

        val mainPanel = Panel(LinearLayout(Direction.VERTICAL))

        val playerState = app.client?.player
        if (playerState != null) {
            mainPanel.addComponent(Label("Logged in as: ${playerState.displayName} (#${playerState.memberNumber})"))
            mainPanel.addComponent(EmptySpace())
        }

        val searchPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        searchPanel.addComponent(Label("Search:"))
        searchInput = TextBox(TerminalSize(20, 1), "")
        searchPanel.addComponent(searchInput)
        searchPanel.addComponent(Label("Space:"))
        spaceComboBox = ComboBox<String>()
        for ((label, _) in spaceEntries) {
            spaceComboBox.addItem(label)
        }
        spaceComboBox.isReadOnly = true
        spaceComboBox.selectedIndex = 0
        searchPanel.addComponent(spaceComboBox)
        searchPanel.addComponent(Button("Search") { onSearch() })
        searchPanel.addComponent(Button("Refresh") { onSearch() })
        mainPanel.addComponent(searchPanel)

        mainPanel.addComponent(EmptySpace())

        roomTable = Table<String>("Name", "Players", "Creator", "Language", "Locked", "Description")
        roomTable.visibleRows = 20
        roomTable.setSelectAction { onJoinSelected() }
        mainPanel.addComponent(roomTable.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.FILL)))

        mainPanel.addComponent(EmptySpace())

        statusLabel = Label("")
        mainPanel.addComponent(statusLabel)

        val buttonPanel = Panel(LinearLayout(Direction.HORIZONTAL))
        buttonPanel.addComponent(Button("Join Selected") { onJoinSelected() })
        buttonPanel.addComponent(Button("Disconnect") { app.disconnect() })
        buttonPanel.addComponent(Button("Quit") { app.quit() })
        mainPanel.addComponent(buttonPanel)

        window.component = mainPanel
        return window
    }

    override fun onEnter() {
        onSearch()
    }

    private fun onSearch() {
        val client = app.client ?: return
        searching = true
        statusLabel.text = "Searching..."

        val query = searchInput.text.trim()
        val idx = spaceComboBox.selectedIndex
        val space = if (idx in spaceEntries.indices) spaceEntries[idx].second else ""
        client.room.search(ChatRoomSearchRequest(query = query, space = space)).thenAccept { results ->
            app.gui.guiThread.invokeLater {
                searching = false
                rooms = results
                roomTable.tableModel.clear()
                for (room in results) {
                    roomTable.tableModel.addRow(
                        room.name,
                        "${room.memberCount ?: 0}/${room.memberLimit ?: 0}",
                        room.creator ?: "",
                        room.language ?: "",
                        if (room.locked == true) "Y" else "",
                        (room.description ?: "").take(40),
                    )
                }
                statusLabel.text = "${results.size} rooms found"
            }
        }.exceptionally { ex ->
            logger.error("Room search failed", ex)
            app.gui.guiThread.invokeLater {
                searching = false
                statusLabel.text = "Search failed: ${ex.cause?.message ?: ex.message}"
            }
            null
        }
    }

    private fun onJoinSelected() {
        val selectedRow = roomTable.selectedRow
        if (selectedRow < 0 || selectedRow >= rooms.size) return

        val room = rooms[selectedRow]
        val client = app.client ?: return

        statusLabel.text = "Joining ${room.name}..."
        client.room.join(room.name).thenAccept { result ->
            app.gui.guiThread.invokeLater {
                when (result) {
                    is RoomJoinResult.Success -> {
                        // Navigation happens via RoomJoined event
                    }

                    is RoomJoinResult.Error -> {
                        statusLabel.text = ""
                        MessageDialogBuilder()
                            .setTitle("Join Failed")
                            .setText(result.message)
                            .addButton(MessageDialogButton.OK)
                            .build()
                            .showDialog(app.gui)
                    }
                }
            }
        }.exceptionally { ex ->
            logger.error("Room join failed", ex)
            app.gui.guiThread.invokeLater {
                statusLabel.text = "Join error: ${ex.cause?.message ?: ex.message}"
            }
            null
        }
    }

    override fun onEvent(event: RazorEvent) {
        when (event) {
            is RazorEvent.RoomJoined -> {
                app.navigateToChatRoom()
            }

            is RazorEvent.ServerInfo -> {
                if (!searching) {
                    statusLabel.text = "Online: ${event.onlinePlayers} players"
                }
            }

            is RazorEvent.Disconnected -> {
                app.navigateToLogin("Disconnected: ${event.reason ?: "Unknown"}")
            }

            is RazorEvent.ForceDisconnect -> {
                app.navigateToLogin("Disconnected: ${event.reason}")
            }

            else -> {}
        }
    }
}