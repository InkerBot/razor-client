package bot.inker.bc.razor.tui

import com.googlecode.lanterna.TextColor

data class ColorScheme(
    // ChatFormatter colors
    val chatMessage: String = "DEFAULT",
    val whisperMessage: String = "MAGENTA",
    val emoteMessage: String = "YELLOW",
    val actionMessage: String = "WHITE_BRIGHT",
    val activityMessage: String = "WHITE_BRIGHT",
    val serverMessage: String = "RED",
    val statusMessage: String = "WHITE_BRIGHT",
    val hiddenMessage: String = "WHITE_BRIGHT",
    val localMessage: String = "CYAN",
    val systemMessage: String = "GREEN",
    val errorMessage: String = "RED",
    val helpMessage: String = "CYAN",
    // MemberListPanel colors
    val memberListHeader: String = "CYAN",
    val memberListSeparator: String = "WHITE_BRIGHT",
    val memberListAdmin: String = "YELLOW",
    val memberListDefault: String = "DEFAULT",
    // StatusBar colors
    val statusBarBackground: String = "BLUE",
    val statusBarForeground: String = "WHITE",
) {
    companion object {
        fun resolve(name: String?): TextColor.ANSI {
            if (name.isNullOrBlank()) return TextColor.ANSI.DEFAULT
            return try {
                TextColor.ANSI.valueOf(name.uppercase())
            } catch (_: IllegalArgumentException) {
                TextColor.ANSI.DEFAULT
            }
        }

        fun default(): ColorScheme = ColorScheme()

        fun dark(): ColorScheme = ColorScheme(
            chatMessage = "WHITE",
            statusBarBackground = "BLACK_BRIGHT",
            statusBarForeground = "WHITE_BRIGHT",
        )

        fun light(): ColorScheme = ColorScheme(
            chatMessage = "BLACK",
            whisperMessage = "MAGENTA",
            emoteMessage = "YELLOW",
            statusBarBackground = "CYAN",
            statusBarForeground = "BLACK",
        )
    }
}
