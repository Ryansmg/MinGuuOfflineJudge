import java.io.IOException

fun disableCoreDumpForMojMain() {
    val baseKey = """HKLM\SOFTWARE\Microsoft\Windows\Windows Error Reporting\LocalDumps\moj_main.exe"""
    val commands = listOf(
        """reg add "$baseKey" /v DumpType /t REG_DWORD /d 0 /f""",
        """reg add "$baseKey" /v DumpCount /t REG_DWORD /d 0 /f"""
    )

    for (cmd in commands) {
        try {
            val process = ProcessBuilder("cmd", "/c", cmd)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                println("명령어 실패: $cmd (종료 코드 $exitCode)")
            }
        } catch (e: IOException) {
            println("명령 실행 실패: $cmd")
            e.printStackTrace()
        }
    }
}

fun main() {
    disableCoreDumpForMojMain()
}