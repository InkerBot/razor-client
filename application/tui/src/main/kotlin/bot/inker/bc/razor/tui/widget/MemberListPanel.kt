package bot.inker.bc.razor.tui.widget

import bot.inker.bc.razor.state.CharacterState
import bot.inker.bc.razor.tui.ColorScheme
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TerminalTextUtils
import com.googlecode.lanterna.gui2.AbstractListBox
import com.googlecode.lanterna.gui2.ActionListBox
import com.googlecode.lanterna.gui2.TextGUIGraphics

class MemberListPanel(
    private val scheme: ColorScheme,
    private val onSelect: (CharacterState) -> Unit,
) : ActionListBox(TerminalSize(20, 5)) {

    private var members: List<CharacterState> = emptyList()
    private var adminList: List<Int> = emptyList()

    init {
        setListItemRenderer(MemberListItemRenderer())
    }

    fun updateMembers(members: List<CharacterState>, admins: List<Int>) {
        val previousMemberNumber = selectedMemberNumber()
        this.members = members
        this.adminList = admins

        clearItems()
        for ((idx, member) in members.withIndex()) {
            val capturedIdx = idx
            addItem(member.displayName) { onSelect(this.members[capturedIdx]) }
        }

        // Restore selection by member number
        if (previousMemberNumber != null) {
            val newIndex = members.indexOfFirst { it.memberNumber == previousMemberNumber }
            if (newIndex >= 0) {
                selectedIndex = newIndex
            }
        }
    }

    private fun selectedMemberNumber(): Int? {
        val idx = selectedIndex
        return if (idx in members.indices) members[idx].memberNumber else null
    }

    private inner class MemberListItemRenderer : AbstractListBox.ListItemRenderer<Runnable, ActionListBox>() {
        override fun drawItem(
            graphics: TextGUIGraphics,
            listBox: ActionListBox,
            index: Int,
            item: Runnable,
            selected: Boolean,
            focused: Boolean,
        ) {
            val member = if (index in members.indices) members[index] else null
            val isAdmin = member != null && member.memberNumber in adminList

            if (selected && focused) {
                val themeDefinition = listBox.theme.getDefinition(AbstractListBox::class.java)
                graphics.applyThemeStyle(themeDefinition.selected)
            } else if (isAdmin) {
                graphics.foregroundColor = ColorScheme.resolve(scheme.memberListAdmin)
                graphics.backgroundColor = com.googlecode.lanterna.TextColor.ANSI.DEFAULT
            } else {
                graphics.foregroundColor = ColorScheme.resolve(scheme.memberListDefault)
                graphics.backgroundColor = com.googlecode.lanterna.TextColor.ANSI.DEFAULT
            }

            val prefix = if (isAdmin) "* " else "  "
            val displayName = member?.displayName ?: item.toString()
            var label = "$prefix$displayName"
            label = TerminalTextUtils.fitString(label, graphics.size.columns)
            while (TerminalTextUtils.getColumnWidth(label) < graphics.size.columns) {
                label += " "
            }
            graphics.putString(0, 0, label)
        }
    }
}
