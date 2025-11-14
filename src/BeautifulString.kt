import org.jline.utils.WCWidth
import java.time.format.DateTimeFormatter

val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

fun strWidth(str: String): Int {
    var s = str
    for(c in Ansi.ALL) s = s.replace(c, "")
    for(e in Emoji.WIDTH_1) s = s.replace(e, "a")
    for(e in Emoji.WIDTH_2) s = s.replace(e, "가")
    var w = 0
    for(c in s) w += maxOf(WCWidth.wcwidth(c.code), 0)
    return w
}

fun padBack(str: String, width: Int): String {
    val w = strWidth(str)
    if(w >= width) return str
    return str + " ".repeat(width - w)
}

fun padFront(str: String, width: Int): String {
    val w = strWidth(str)
    if(w >= width) return str
    return " ".repeat(width - w) + str
}

fun centerStrLine(line: String, width: Int): String {
    val w = strWidth(line)
    if(w >= width) return line
    val left = (width - w) / 2
    return " ".repeat(left) + line + " ".repeat(width - left - w)
}

fun centerStr(str: String, width: Int): String {
    val b = StringBuilder()
    val lines = str.split("\n")
    for(line in lines) {
        b.append(centerStrLine(line, width)).append("\n")
    }
    return b.toString().removeSuffix("\n")
}

fun centerPaddedStr(str: String): String {
    val s = str.trim()
    return centerStr(s, strWidth(str))
}

fun wrapInABox(str: String, colorStr: String = ""): String {
    val list = str.replace("\r\n", "\n").split("\n")
    val wList = MutableList(list.size) {0}
    var maxW = 0
    for(i in 0..<list.size) {
        wList[i] = strWidth(list[i])
        maxW = maxOf(maxW, wList[i])
    }
    val result = StringBuilder()
    result.append(colorStr).append('╭').append("─".repeat(maxW + 2)).append("╮${Ansi.RESET}\n")
    for(i in 0..<list.size) {
        result.append(colorStr).append("│${Ansi.RESET} ").append(list[i]).append(" ".repeat(maxW - wList[i])).append("$colorStr │${Ansi.RESET}\n")
    }
    result.append(colorStr).append('╰').append("─".repeat(maxW + 2)).append("╯${Ansi.RESET}")
    return result.toString()
}

fun formatTable(table: List<List<String>>): List<List<String>> {
    if(table.isEmpty()) return table
    val rows = table.size
    val columns = table[0].size
    val result = MutableList(rows) { MutableList(columns) { "" } }
    for(c in 0..<columns) {
        var maxWidth = 0
        for(r in 0..<rows) if(table[r].size > c)
            maxWidth = maxOf(maxWidth, strWidth(table[r][c]))

        for(r in 0..<rows) if(table[r].size > c)
            result[r][c] = padBack(table[r][c], maxWidth)
    }
    return result
}

fun tableToStr(table: List<List<String>>, separator: String = " | ", separateRows: Boolean = false) : String {
    if(table.isEmpty()) return ""
    val b = StringBuilder()
    var totWidth = 0
    for(col in table[0]) totWidth += strWidth(col)
    totWidth += strWidth(separator) * (table[0].size - 1)
    table.forEachIndexed { i, row ->
        if(i != 0) b.append("\n")
        if(i != 0 && separateRows)
            b.append("─".repeat(totWidth)).append("\n")

        b.append(row.joinToString(separator))
    }
    return b.toString()
}
