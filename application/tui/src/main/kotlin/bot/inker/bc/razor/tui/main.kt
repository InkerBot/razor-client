package bot.inker.bc.razor.tui

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory
import java.io.PrintStream

fun main(args: Array<String>) {
    val originalOut = System.out
    val originalIn = System.`in`

    System.setOut(PrintStream(LoggerOutputStream(LogManager.getLogger("stdout"), Level.INFO), true))
    System.setErr(PrintStream(LoggerOutputStream(LogManager.getLogger("stderr"), Level.ERROR), true))

    val logger = LoggerFactory.getLogger("bot.inker.bc.razor.tui.Main")
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error("Uncaught exception in thread {}", thread.name, throwable)
    }

    val app = TuiApplication(originalOut, originalIn)
    app.start()
}
