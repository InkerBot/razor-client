package bot.inker.bc.razor.tui.screen

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.protocol.chat.ChatMessageType
import bot.inker.bc.razor.tui.TuiApplication
import bot.inker.bc.razor.tui.command.CommandExecutor
import bot.inker.bc.razor.tui.command.CommandParser
import bot.inker.bc.razor.tui.format.ChatFormatter
import bot.inker.bc.razor.tui.widget.ChatLogPanel
import bot.inker.bc.razor.tui.widget.InputBar
import bot.inker.bc.razor.tui.widget.MemberListPanel
import bot.inker.bc.razor.tui.widget.PlayerDetailDialog
import bot.inker.bc.razor.tui.widget.StatusBar
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.input.MouseAction
import com.googlecode.lanterna.input.MouseActionType

class ChatRoomScreen(private val app: TuiApplication) : Screen {
    private lateinit var window: BasicWindow
    private lateinit var titleLabel: Label
    private lateinit var chatLog: ChatLogPanel
    private lateinit var memberList: MemberListPanel
    private lateinit var statusBar: StatusBar
    private lateinit var inputBar: InputBar
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var chatFormatter: ChatFormatter

    override fun createWindow(): BasicWindow {
        val scheme = app.config.colorScheme
        chatFormatter = ChatFormatter(scheme)

        // Initialize chatLog early so handleInput can reference it
        chatLog = ChatLogPanel()

        commandExecutor = CommandExecutor(app, chatFormatter) { msg -> chatLog.addMessage(msg) }

        window = object : BasicWindow("Chat Room") {
            override fun handleInput(keyStroke: KeyStroke): Boolean {
                if (keyStroke is MouseAction) {
                    when (keyStroke.actionType) {
                        MouseActionType.SCROLL_UP -> {
                            chatLog.scrollUp(3)
                            return true
                        }

                        MouseActionType.SCROLL_DOWN -> {
                            chatLog.scrollDown(3)
                            return true
                        }

                        else -> {}
                    }
                }
                when (keyStroke.keyType) {
                    KeyType.PAGE_UP -> {
                        chatLog.scrollUp(chatLog.size?.rows ?: 20)
                        return true
                    }

                    KeyType.PAGE_DOWN -> {
                        chatLog.scrollDown(chatLog.size?.rows ?: 20)
                        return true
                    }

                    KeyType.HOME -> if (keyStroke.isCtrlDown) {
                        chatLog.scrollToTop()
                        return true
                    }

                    KeyType.END -> if (keyStroke.isCtrlDown) {
                        chatLog.scrollToBottom()
                        return true
                    }

                    KeyType.TAB -> {
                        if (focusedInteractable === memberList) {
                            inputBar.focus()
                        } else {
                            memberList.takeFocus()
                        }
                        return true
                    }

                    else -> {}
                }
                return super.handleInput(keyStroke)
            }
        }
        val hints = mutableListOf<Window.Hint>(Window.Hint.EXPANDED)
        if (app.config.disableShadows != false) {
            hints.add(Window.Hint.NO_POST_RENDERING)
        }
        window.setHints(hints)

        val mainPanel = Panel(BorderLayout())

        // Title bar
        titleLabel = Label("Room")
        mainPanel.addComponent(titleLabel, BorderLayout.Location.TOP)

        // Center: member list + chat log
        val centerPanel = Panel(LinearLayout(Direction.HORIZONTAL))

        memberList = MemberListPanel(scheme) { character ->
            PlayerDetailDialog.show(app.gui, character, scheme, app.config.disableShadows)
        }
        centerPanel.addComponent(
            memberList.withBorder(Borders.singleLine("Members"))
                .setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.FILL))
        )

        centerPanel.addComponent(
            chatLog.withBorder(Borders.singleLine("Chat"))
                .setLayoutData(
                    LinearLayout.createLayoutData(
                        LinearLayout.Alignment.FILL,
                        LinearLayout.GrowPolicy.CAN_GROW
                    )
                )
        )

        mainPanel.addComponent(centerPanel, BorderLayout.Location.CENTER)

        // Bottom: input + status
        val bottomPanel = Panel(LinearLayout(Direction.VERTICAL))

        inputBar = InputBar { text ->
            val cmd = CommandParser.parse(text)
            commandExecutor.execute(cmd)
        }
        bottomPanel.addComponent(inputBar.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.FILL)))

        statusBar = StatusBar(scheme)
        bottomPanel.addComponent(statusBar.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.FILL)))

        mainPanel.addComponent(bottomPanel, BorderLayout.Location.BOTTOM)

        window.component = mainPanel
        return window
    }

    override fun onEnter() {
        refreshState()
        inputBar.focus()
    }

    private fun refreshState() {
        val client = app.client ?: return
        val room = client.room.state ?: return
        val player = client.player

        titleLabel.text = "Room: ${room.name} (${room.characters.size}/${room.limit})"
        memberList.updateMembers(room.characters, room.admin)

        statusBar.connectionStatus = "Connected"
        statusBar.playerInfo = if (player != null) "${player.displayName} (#${player.memberNumber})" else ""
        statusBar.roomInfo = room.name
    }

    override fun onEvent(event: RazorEvent) {
        when (event) {
            is RazorEvent.ChatMessage -> {
                val msg = event.message
                if (msg.type == ChatMessageType.HIDDEN || msg.type == ChatMessageType.STATUS) return

                val client = app.client
                val characters = client?.room?.characters
                val senderName = characters
                    ?.find { it.memberNumber == msg.sender }?.displayName
                    ?: "#${msg.sender}"
                val targetName = msg.target?.let { targetNum ->
                    characters?.find { it.memberNumber == targetNum }?.displayName
                        ?: "#$targetNum"
                }
                val resolvedContent = app.messageResolver.resolve(msg) { memberNumber ->
                    characters?.find { it.memberNumber == memberNumber }
                }
                val formatted = chatFormatter.format(msg, senderName, targetName, resolvedContent)
                chatLog.addMessage(formatted)
            }

            is RazorEvent.MemberJoined -> {
                refreshState()
            }

            is RazorEvent.MemberLeft -> {
                refreshState()
            }

            is RazorEvent.RoomUpdated -> {
                refreshState()
            }

            is RazorEvent.RoomLeft -> {
                app.navigateToLobby()
            }

            is RazorEvent.CharacterUpdated -> {
                refreshState()
            }

            is RazorEvent.PlayersReordered -> {
                refreshState()
            }

            is RazorEvent.Reconnecting -> {
                statusBar.connectionStatus = "Reconnecting (#${event.attemptNumber})..."
            }

            is RazorEvent.Connected -> {
                statusBar.connectionStatus = "Connected"
            }

            is RazorEvent.Disconnected -> {
                app.navigateToLogin("Disconnected: ${event.reason ?: "Unknown"}")
            }

            is RazorEvent.ForceDisconnect -> {
                app.navigateToLogin("Disconnected: ${event.reason}")
            }

            is RazorEvent.BeepReceived -> {
                val beep = event.beep
                val fromName = beep.memberName ?: "#${beep.memberNumber}"
                val msg = if (beep.message != null) {
                    "Beep from $fromName: ${beep.message}"
                } else {
                    "Beep from $fromName"
                }
                chatLog.addMessage(chatFormatter.systemMessage(msg))
            }

            is RazorEvent.ServerInfo -> {
                // optionally update online count somewhere
            }

            else -> {}
        }
    }
}
