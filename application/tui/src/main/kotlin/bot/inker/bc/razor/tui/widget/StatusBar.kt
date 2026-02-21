package bot.inker.bc.razor.tui.widget

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractComponent
import com.googlecode.lanterna.gui2.ComponentRenderer
import com.googlecode.lanterna.gui2.TextGUIGraphics

class StatusBar : AbstractComponent<StatusBar>() {
    var connectionStatus: String = "Connected"
        set(value) { field = value; invalidate() }
    var playerInfo: String = ""
        set(value) { field = value; invalidate() }
    var roomInfo: String = ""
        set(value) { field = value; invalidate() }

    override fun createDefaultRenderer(): ComponentRenderer<StatusBar> {
        return object : ComponentRenderer<StatusBar> {
            override fun getPreferredSize(component: StatusBar): TerminalSize {
                return TerminalSize(80, 1)
            }

            override fun drawComponent(graphics: TextGUIGraphics, component: StatusBar) {
                val cols = graphics.size.columns
                graphics.setBackgroundColor(TextColor.ANSI.BLUE)
                graphics.setForegroundColor(TextColor.ANSI.WHITE)
                graphics.fill(' ')

                val parts = mutableListOf<String>()
                if (component.connectionStatus.isNotEmpty()) parts.add(component.connectionStatus)
                if (component.playerInfo.isNotEmpty()) parts.add(component.playerInfo)
                if (component.roomInfo.isNotEmpty()) parts.add(component.roomInfo)
                val text = parts.joinToString(" | ")
                graphics.putString(1, 0, text.take(cols - 2))

                graphics.backgroundColor = TextColor.ANSI.DEFAULT
                graphics.foregroundColor = TextColor.ANSI.DEFAULT
            }
        }
    }
}
