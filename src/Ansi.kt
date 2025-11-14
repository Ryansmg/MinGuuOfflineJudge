
object Ansi {
    const val RESET = "\u001B[0m"
    const val RED = "\u001B[31m"
    const val ORANGE = "\u001B[38;5;208m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val CYAN = "\u001B[36m"
    const val PURPLE = "\u001B[35m"
    const val GRAY = "\u001B[90m"

    const val CLEAR_LINE = "\u001B[2K"
    const val CLEAR_DISPLAY = "\u001B[2J"
    const val CURSOR_UL = "\u001B[H"
    const val CURSOR_UP = "\u001B[1A"
    const val CURSOR_DOWN = "\u001B[1B"
    const val CURSOR_START = "\u001B[1000D"  // 커서를 줄 맨 앞으로

    const val BOLD = "\u001B[1m"
    const val UNDERLINE = "\u001B[4m"
    const val BLINK = "\u001B[5m"
    const val REVERSED = "\u001B[7m"
    const val INVISIBLE = "\u001B[8m"
    const val STRIKETHROUGH = "\u001B[9m"
    const val UNDERLINE_OFF = "\u001B[24m"
    const val BLINK_OFF = "\u001B[25m"
    const val REVERSED_OFF = "\u001B[27m"
    const val INVISIBLE_OFF = "\u001B[28m"

    val ALL = listOf(
        RESET, RED, ORANGE, GREEN, YELLOW, BLUE, CYAN, PURPLE, GRAY,
        CLEAR_LINE, CLEAR_DISPLAY, CURSOR_UL, CURSOR_UP, CURSOR_DOWN, CURSOR_START,
        BOLD, UNDERLINE, BLINK, REVERSED, INVISIBLE, STRIKETHROUGH, UNDERLINE_OFF, BLINK_OFF, REVERSED_OFF, INVISIBLE_OFF
    )
}

object Emoji {
    const val WARNING = "⚠\uFE0F"
    const val CROSS_MARK = "❌"

    val WIDTH_1: List<String> = listOf()

    val WIDTH_2 = listOf(
        WARNING, CROSS_MARK
    )
}


fun clearDisplay() { print(Ansi.CLEAR_DISPLAY + Ansi.CURSOR_UL) }
fun redPrintln(msg: String) { println(Ansi.RED + msg + Ansi.RESET) }
fun orangePrintln(msg: String) { println(Ansi.ORANGE + msg + Ansi.RESET) }
fun greenPrintln(msg: String) { println(Ansi.GREEN + msg + Ansi.RESET) }
fun cyanPrintln(msg: String) { println(Ansi.CYAN + msg + Ansi.RESET) }
fun yellowPrintln(msg: String) { println(Ansi.YELLOW + msg + Ansi.RESET) }
fun bluePrintln(msg: String) { println(Ansi.BLUE + msg + Ansi.RESET) }
fun purplePrintln(msg: String) { println(Ansi.PURPLE + msg + Ansi.RESET) }
fun redPrint(msg: String) { print(Ansi.RED + msg + Ansi.RESET) }
fun greenPrint(msg: String) { print(Ansi.GREEN + msg + Ansi.RESET) }
fun cyanPrint(msg: String) { print(Ansi.CYAN + msg + Ansi.RESET) }
fun yellowPrint(msg: String) { print(Ansi.YELLOW + msg + Ansi.RESET) }
fun bluePrint(msg: String) { print(Ansi.BLUE + msg + Ansi.RESET) }
fun clearCurLine() { print(Ansi.CLEAR_LINE) }
fun clearPrevLine() { print(Ansi.CURSOR_UP + Ansi.CLEAR_LINE) }
