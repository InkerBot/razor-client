package bot.inker.bc.razor.tui.screen

import bot.inker.bc.razor.event.RazorEvent

class ScreenManager {
    @Volatile
    var currentScreen: Screen? = null

    fun dispatchEvent(event: RazorEvent) {
        currentScreen?.onEvent(event)
    }
}
