package bot.inker.bc.razor.tui.widget

import bot.inker.bc.razor.state.CharacterState
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractComponent
import com.googlecode.lanterna.gui2.ComponentRenderer
import com.googlecode.lanterna.gui2.TextGUIGraphics

class MemberListPanel : AbstractComponent<MemberListPanel>() {
    private var members: List<CharacterState> = emptyList()
    private var adminList: List<Int> = emptyList()

    fun updateMembers(members: List<CharacterState>, admins: List<Int>) {
        this.members = members
        this.adminList = admins
        invalidate()
    }

    override fun createDefaultRenderer(): ComponentRenderer<MemberListPanel> {
        return object : ComponentRenderer<MemberListPanel> {
            override fun getPreferredSize(component: MemberListPanel): TerminalSize {
                return TerminalSize(20, 10)
            }

            override fun drawComponent(graphics: TextGUIGraphics, component: MemberListPanel) {
                graphics.fill(' ')
                val cols = graphics.size.columns
                val rows = graphics.size.rows

                val header = "Members (${component.members.size})"
                graphics.setForegroundColor(TextColor.ANSI.CYAN)
                graphics.putString(0, 0, header.take(cols))

                graphics.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT)
                if (cols > 1) {
                    graphics.putString(0, 1, "-".repeat(minOf(cols, header.length)))
                }

                for ((idx, member) in component.members.withIndex()) {
                    val row = idx + 2
                    if (row >= rows) break

                    val isAdmin = member.memberNumber in component.adminList
                    val prefix = if (isAdmin) "* " else "  "
                    val text = "$prefix${member.displayName}"

                    if (isAdmin) {
                        graphics.setForegroundColor(TextColor.ANSI.YELLOW)
                    } else {
                        graphics.setForegroundColor(TextColor.ANSI.DEFAULT)
                    }
                    graphics.putString(0, row, text.take(cols))
                }
                graphics.setForegroundColor(TextColor.ANSI.DEFAULT)
            }
        }
    }
}
