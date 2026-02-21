package bot.inker.bc.razor.tui.widget

import bot.inker.bc.razor.tui.format.FormattedMessage
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.AbstractComponent
import com.googlecode.lanterna.gui2.ComponentRenderer
import com.googlecode.lanterna.gui2.TextGUIGraphics

class ChatLogPanel(private val maxMessages: Int = 500) : AbstractComponent<ChatLogPanel>() {
    private val messages = ArrayDeque<FormattedMessage>(maxMessages)
    private var scrollOffset = 0 // visual line offset
    private var pinToBottom = true

    fun addMessage(message: FormattedMessage) {
        if (messages.size >= maxMessages) {
            messages.removeFirst()
        }
        messages.addLast(message)
        if (pinToBottom) {
            scrollToBottomInternal()
        }
        invalidate()
    }

    fun clear() {
        messages.clear()
        scrollOffset = 0
        pinToBottom = true
        invalidate()
    }

    private fun scrollToBottomInternal() {
        val visibleRows = size?.rows ?: 20
        val cols = size?.columns ?: 60
        val totalLines = totalVisualLines(cols)
        scrollOffset = maxOf(0, totalLines - visibleRows)
    }

    fun scrollUp(lines: Int = 1) {
        scrollOffset = maxOf(0, scrollOffset - lines)
        pinToBottom = false
        invalidate()
    }

    fun scrollDown(lines: Int = 1) {
        val visibleRows = size?.rows ?: 20
        val cols = size?.columns ?: 60
        val totalLines = totalVisualLines(cols)
        val maxOffset = maxOf(0, totalLines - visibleRows)
        scrollOffset = minOf(maxOffset, scrollOffset + lines)
        if (scrollOffset >= maxOffset) {
            pinToBottom = true
        }
        invalidate()
    }

    fun scrollToTop() {
        scrollOffset = 0
        pinToBottom = false
        invalidate()
    }

    fun scrollToBottom() {
        scrollToBottomInternal()
        pinToBottom = true
        invalidate()
    }

    private fun totalVisualLines(cols: Int): Int {
        if (cols <= 0) return messages.size
        return messages.sumOf { wrapLineCount(it.text, cols) }
    }

    override fun createDefaultRenderer(): ComponentRenderer<ChatLogPanel> {
        return object : ComponentRenderer<ChatLogPanel> {
            override fun getPreferredSize(component: ChatLogPanel): TerminalSize {
                return TerminalSize(20, 5)
            }

            override fun drawComponent(graphics: TextGUIGraphics, component: ChatLogPanel) {
                graphics.fill(' ')
                val rows = graphics.size.rows
                val cols = graphics.size.columns

                // Build all visual lines with their colors
                val visualLines = buildVisualLines(component.messages, cols)

                val startIdx = component.scrollOffset.coerceIn(0, maxOf(0, visualLines.size - 1))
                val endIdx = minOf(startIdx + rows, visualLines.size)

                for (i in startIdx until endIdx) {
                    val (text, color) = visualLines[i]
                    val row = i - startIdx
                    graphics.setForegroundColor(color)
                    graphics.putString(0, row, text)
                }
                graphics.setForegroundColor(TextColor.ANSI.DEFAULT)
            }
        }
    }

    companion object {
        private fun wrapLineCount(text: String, cols: Int): Int {
            if (cols <= 0) return maxOf(1, text.count { it == '\n' } + 1)
            return text.split('\n').sumOf { line ->
                if (line.isEmpty()) 1 else (line.length + cols - 1) / cols
            }
        }

        private fun buildVisualLines(
            messages: Collection<FormattedMessage>,
            cols: Int,
        ): List<Pair<String, TextColor>> {
            val result = mutableListOf<Pair<String, TextColor>>()
            for (msg in messages) {
                for (line in msg.text.split('\n')) {
                    if (cols <= 0 || line.length <= cols) {
                        result.add(line to msg.color)
                    } else {
                        var start = 0
                        while (start < line.length) {
                            val end = minOf(start + cols, line.length)
                            result.add(line.substring(start, end) to msg.color)
                            start = end
                        }
                    }
                }
            }
            return result
        }
    }
}
