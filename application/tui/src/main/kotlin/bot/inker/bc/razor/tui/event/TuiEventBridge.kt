package bot.inker.bc.razor.tui.event

import bot.inker.bc.razor.RazorClient
import bot.inker.bc.razor.event.Subscription
import bot.inker.bc.razor.tui.screen.ScreenManager
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import org.slf4j.LoggerFactory

class TuiEventBridge(
    private val gui: MultiWindowTextGUI,
    private val screenManager: ScreenManager,
) {
    private val logger = LoggerFactory.getLogger(TuiEventBridge::class.java)
    private var subscription: Subscription? = null

    fun attach(client: RazorClient) {
        detach()
        subscription = client.onAny { event ->
            gui.guiThread.invokeLater {
                try {
                    screenManager.dispatchEvent(event)
                } catch (e: Exception) {
                    logger.error("Error dispatching event: {}", event::class.simpleName, e)
                }
            }
        }
    }

    fun detach() {
        subscription?.cancel()
        subscription = null
    }
}
