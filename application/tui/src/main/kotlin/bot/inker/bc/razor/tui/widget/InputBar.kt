package bot.inker.bc.razor.tui.widget

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.*
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType

class InputBar(private val onSubmit: (String) -> Unit) : Panel(LinearLayout(Direction.HORIZONTAL)) {
    private val textBox: TextBox

    init {
        addComponent(Label("> "))
        textBox = object : TextBox(TerminalSize(10, 1), "", Style.MULTI_LINE) {
            private fun updateHeight() {
                val width = size?.columns ?: 10
                setPreferredSize(TerminalSize(width, lineCount.coerceIn(1, 5)))
            }

            override fun handleKeyStroke(keyStroke: KeyStroke): Interactable.Result {
                if (keyStroke.keyType == KeyType.Enter) {
                    val caretLine = caretPosition.row
                    val line = getLine(caretLine)
                    if (line.endsWith("\\")) {
                        val allLines = (0 until lineCount).map { i ->
                            if (i == caretLine) line.substring(0, line.length - 1) else getLine(i)
                        }
                        setText(allLines.joinToString("\n"))
                        setCaretPosition(caretLine, allLines[caretLine].length)
                        val result = super.handleKeyStroke(keyStroke)
                        updateHeight()
                        return result
                    }
                    submit()
                    return Interactable.Result.HANDLED
                }
                return super.handleKeyStroke(keyStroke)
            }
        }
        addComponent(
            textBox.setLayoutData(
                LinearLayout.createLayoutData(LinearLayout.Alignment.Fill, LinearLayout.GrowPolicy.CanGrow)
            )
        )
    }

    fun submit() {
        val text = textBox.text.trim()
        if (text.isNotEmpty()) {
            onSubmit(text)
            textBox.text = ""
            textBox.setCaretPosition(0, 0)
            val width = textBox.size?.columns ?: 10
            textBox.setPreferredSize(TerminalSize(width, 1))
        }
    }

    fun focus() {
        textBox.takeFocus()
    }
}
