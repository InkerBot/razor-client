package bot.inker.bc.razor.telegram

import bot.inker.bc.razor.app.LoggerOutputStream
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory
import java.io.PrintStream

fun main(args: Array<String>) {
    System.setOut(PrintStream(LoggerOutputStream(LogManager.getLogger("stdout"), Level.INFO), true))
    System.setErr(PrintStream(LoggerOutputStream(LogManager.getLogger("stderr"), Level.ERROR), true))

    val logger = LoggerFactory.getLogger("bot.inker.bc.razor.telegram.Main")
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        logger.error("Uncaught exception in thread {}", thread.name, throwable)
    }

    val app = TelegramApplication()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received")
        app.stop()
    })

    app.start()

    Thread.currentThread().join()
}
