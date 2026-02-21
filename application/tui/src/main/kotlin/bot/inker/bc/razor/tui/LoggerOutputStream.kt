package bot.inker.bc.razor.tui

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import java.io.OutputStream

class LoggerOutputStream(
    private val logger: Logger,
    private val level: Level,
) : OutputStream() {
    private val buffer = StringBuilder()

    @Synchronized
    override fun write(b: Int) {
        if (b == '\n'.code) {
            flush()
        } else {
            buffer.append(b.toChar())
        }
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        val str = String(b, off, len, Charsets.UTF_8)
        for (ch in str) {
            if (ch == '\n') {
                flush()
            } else {
                buffer.append(ch)
            }
        }
    }

    @Synchronized
    override fun flush() {
        if (buffer.isNotEmpty()) {
            logger.log(level, buffer.toString())
            buffer.setLength(0)
        }
    }
}