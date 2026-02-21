package bot.inker.bc.razor.tui.widget

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.Interactable
import com.googlecode.lanterna.gui2.LinearLayout
import com.googlecode.lanterna.gui2.Panel
import com.googlecode.lanterna.gui2.Direction
import com.googlecode.lanterna.gui2.Label
import com.googlecode.lanterna.gui2.TextBox
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class InputBar(private val onSubmit: (String) -> Unit) : Panel(LinearLayout(Direction.HORIZONTAL)) {
    private val textBox: TextBox

    init {
        addComponent(Label("> "))
        textBox = object : TextBox(TerminalSize(60, 1), "") {
            override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
                if (keyStroke.keyType == KeyType.Enter) {
                    submit()
                    return Interactable.Result.HANDLED
                }
                return super.handleKeyStroke(keyStroke)
            }
        }
        addComponent(textBox.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill)))
    }

    fun submit() {
        val text = textBox.text.trim()
        if (text.isNotEmpty()) {
            onSubmit(text)
            textBox.text = ""
        }
    }

    fun focus() {
        textBox.takeFocus()
    }
}
