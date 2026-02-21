package bot.inker.bc.razor.tui.screen

import bot.inker.bc.razor.event.RazorEvent
import com.googlecode.lanterna.gui2.BasicWindow

interface Screen {
    fun createWindow(): BasicWindow
    fun onEnter() {}
    fun onLeave() {}
    fun onEvent(event: RazorEvent) {}
}
