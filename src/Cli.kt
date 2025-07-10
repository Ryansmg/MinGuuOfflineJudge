import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.NonBlockingReader
import org.jline.utils.WCWidth
import java.io.PrintWriter
import java.nio.file.Paths
import kotlin.system.exitProcess

val terminal: Terminal = TerminalBuilder.builder()
    .system(true)
    .nativeSignals(true)
    .name("windows")
    .jna(false)
    .jni(true)
    .build()

val reader: NonBlockingReader = terminal.reader()
val writer: PrintWriter = terminal.writer()

data class GetCResult(val name: String, val special: Int = 0, val char: Char = 0.toChar()) {
    companion object {
        const val UNKNOWN = -1
        const val NONE = 0
        const val UP_ARROW = 1
        const val DOWN_ARROW = 2
        const val RIGHT_ARROW = 3
        const val LEFT_ARROW = 4
        const val CRLF = 5
    }
}

fun ignorePrevInput() {
    while(reader.ready()) reader.read()
}

fun getC(): GetCResult {
    val list = MutableList(0) { 0 }
    list.addLast(reader.read())
    while(reader.ready()) {
        list.addLast(reader.read())
    }
    if(list.size == 1)
        return GetCResult(list[0].toChar().toString(), GetCResult.NONE, list[0].toChar())

    return when(list) {
        listOf(27, 79, 65), listOf(27, 91, 65) -> GetCResult("upArr", GetCResult.UP_ARROW)
        listOf(27, 79, 66), listOf(27, 91, 66) -> GetCResult("downArr", GetCResult.DOWN_ARROW)
        listOf(27, 79, 67), listOf(27, 91, 67) -> GetCResult("rightArr", GetCResult.RIGHT_ARROW)
        listOf(27, 79, 68), listOf(27, 91, 68) -> GetCResult("leftArr", GetCResult.LEFT_ARROW)
        listOf('\r'.code, '\n'.code) -> GetCResult("crlf", GetCResult.CRLF, '\n')
        else -> GetCResult("unknown", GetCResult.UNKNOWN)
    }
}

fun cliSelect(t: Int) {
    writer.flush()
    if(t == 0) {
        clearDisplay()
        println("\n채점을 진행할 문제를 선택하세요.")
        val availableProblems = Paths.get(processPath, "problems").toFile().listFiles()!!
        availableProblems.forEachIndexed { i, f ->
            if (f.isDirectory) {
                yellowPrintln("[$i] ${f.name}")
            }
        }

        bluePrint("> ")
        var id = readln()

        if (!Paths.get(processPath, "problems", id).toFile().exists()) {
            try {
                val idx = id.toInt()
                id = availableProblems[idx].name
            } catch (_: Exception) {
            }
        }

        if (!Paths.get(processPath, "problems", id).toFile().exists() || id.isEmpty()) {
            cliMsg.append(wrapInABox("${Emoji.WARNING} 입력한 문제를 찾지 못했습니다.", Ansi.YELLOW))
        } else {
            clearDisplay()
            judge(id)
            ignorePrevInput()
            println("Enter 키를 눌러 메뉴로 돌아가세요.")
            readln()
        }
    }

    if(t == 1) {
        clearDisplay()
        println("=== 전체 제출 기록 ===\n")
        submissionHistory.asReversed().forEach { println(it) }

        ignorePrevInput()
        println("\nEnter 키를 눌러 메뉴로 돌아가세요.")
        readln()
    }

    if(t == 3) {
        clearDisplay()
        println(wrapInABox("""
            정말로 종료하시겠습니까? (Y/N)
        """.trimIndent(), Ansi.RED))
        println()
        print("> ")
        if(readln().lowercase().contains("y")) exitProcess(0)
    }
}

var cliMsg = StringBuilder()

fun strWidth(str: String): Int {
    var s = str
    for(c in Ansi.ALL) s = s.replace(c, "")
    for(e in Emoji.WIDTH_1) s = s.replace(e, "a")
    for(e in Emoji.WIDTH_2) s = s.replace(e, "가")
    var w = 0
    for(c in s) w += maxOf(WCWidth.wcwidth(c.code), 0)
    return w
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

fun cli() {
    while(reader.ready()) reader.read()
    val title = """
        ================================================================
                               MinGuuOfflineJudge
                                by MinGuu (v1.0)
        ================================================================
        
    """.trimIndent()

    var selected = 0
    while(true) {
        writer.print(Ansi.CLEAR_DISPLAY + Ansi.CURSOR_UL)
        writer.println(title)

        if(cliMsg.isNotBlank()) {
            writer.println(cliMsg)
            writer.println()
        }


        writer.println(wrapInABox("""
            ${if (selected == 0) Ansi.CYAN + ">" else " "} [0] 단일 제출${Ansi.RESET}  
            ${if (selected == 1) Ansi.CYAN + ">" else " "} [1] 전체 제출 기록${Ansi.RESET}  
            ${if (selected == 2) Ansi.CYAN + ">" else " "} [2] 종료${Ansi.RESET}  
            ${if (selected == 3) Ansi.CYAN + ">" else " "} [3] 종료${Ansi.RESET}  
        """.trimIndent()))

        if(submissionHistory.isNotEmpty()){
            writer.println("""
                
                # 최근 제출 기록
                
            """.trimIndent())

            var i = submissionHistory.size - 1
            var cnt = 0
            while(i >= 0 && cnt < 10) {
                writer.println(submissionHistory[i])
                i--
                cnt++
            }
        }

        writer.flush()

        val input = getC()
        if(input.special == GetCResult.UP_ARROW)
            selected = maxOf(selected - 1, 0)

        else if(input.special == GetCResult.DOWN_ARROW)
            selected = minOf(selected + 1, 3)

        else if(input.char == '\n') { cliMsg = StringBuilder(); cliSelect(selected); selected = 0 }
        else if(input.char == '\r') { cliMsg = StringBuilder(); cliSelect(selected); selected = 0 }
        else if(input.char == '0') { cliMsg = StringBuilder(); selected = 0; cliSelect(0) }
        else if(input.char == '1') { cliMsg = StringBuilder(); selected = 0; cliSelect(1) }
        else if(input.char == '2') { cliMsg = StringBuilder(); selected = 0; cliSelect(2) }
        else if(input.char == '3') { cliMsg = StringBuilder(); selected = 0; cliSelect(3) }
    }
}
