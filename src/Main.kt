import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.time.measureTimedValue
import kotlinx.coroutines.*
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDateTime
import kotlin.time.measureTime

var probJson: JsonObject = JsonObject()

lateinit var processPath: String

fun getMemoryUsageKbWindows(pid: Long): Long? = runCatching {
    ProcessBuilder("tasklist", "/FI", "\"PID eq $pid\"", "/FO", "CSV", "/NH")
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readLines()
        .firstOrNull()
        ?.split("\",\"")
        ?.map { it.trim('"') }
        ?.getOrNull(4)
        ?.replace(",", "")
        ?.replace(" K", "")
        ?.toLongOrNull()
}.getOrNull()

data class RunResult(
    var exitCode: Int? = null,
    var time: Long = 0,
    // 0 -> ac, 1 -> wa, 2 -> tle, 3 -> mle, 4 -> re
    var result: Int = -1,
    var memory: Long = 0
)

suspend fun runWithLimits(
    process: Process,
    timeLimitMs: Long,
    memoryLimitKb: Long
): RunResult = coroutineScope {

    val pid = process.pid()

    var mle = false
    var re = false

    var mxm = -1L

    val memMonitor = launch(Dispatchers.IO) {
        while (process.isAlive) {
            val mem = getMemoryUsageKbWindows(pid)
            mxm = maxOf(mxm, mem ?: -2)
            if (mem != null && mem > memoryLimitKb) {
                process.destroyForcibly()
                mle = true
                break
            }
            delay(4)
        }
    }

    val errMonitor = launch(Dispatchers.IO) {
        while (process.isAlive) {
            if (Paths.get(processPath, "judge", "err").toFile().readText().isNotEmpty()) {
                process.destroyForcibly()
                re = true
                break
            }
            delay(100)
        }
    }

    val (exitCode, elapsed) = measureTimedValue {
        process.waitFor(timeLimitMs, TimeUnit.MILLISECONDS)
        if(process.isAlive) {
            process.destroyForcibly()
            return@measureTimedValue 0
        }
        process.exitValue()
    }

    if (Paths.get(processPath, "judge", "err").toFile().readText().isNotEmpty()) {
        re = true
    }

    val k = if(mle) 3
        else if(re) 4
        else 0

    memMonitor.cancelAndJoin()
    errMonitor.cancelAndJoin()
    RunResult(exitCode, elapsed.inWholeMilliseconds, k, mxm)
}

fun compileSubmission(): Int {
    val inputFile = Paths.get(processPath, "user", "main.cpp").absolutePathString()
    val outputFile = Paths.get(processPath, "judge", "moj_main.exe").absolutePathString()

    val (compileExitCode, _) = measureTimedValue {
//        runProcess("g++", inputFile, "-o", outputFile, "-O2", "-Wall", "-lm", "-static", "-std=gnu++26")
        ProcessBuilder(listOf("g++", inputFile, "-o", outputFile, "-O2", "-Wall", "-lm", "-static", "-std=gnu++26"))
            .redirectOutput(Paths.get(processPath, "user", "compileMessage.txt").toFile())
            .redirectError(Paths.get(processPath, "user", "compileMessage.txt").toFile())
            .start().waitFor()
    }

    return compileExitCode
}

fun compileChecker(problemId: String): Int {
    if(Paths.get(processPath, "problems", problemId, "files", "check.exe").toFile().exists()) return 0

    return ProcessBuilder(
        listOf(
            "g++",
            Paths.get(processPath, "problems", problemId, "files", "check.cpp").absolutePathString(),
            "-o",
            Paths.get(processPath, "problems", problemId, "files", "check.exe").absolutePathString(),
            "-O2",
            "-std=gnu++20",
            "-lm"
        )
    ).start().waitFor()
}


fun compileSolution(problemId: String): Int {
    if(Paths.get(processPath, "problems", problemId, "files", "solution.exe").toFile().exists()) return 0

    return ProcessBuilder(
        listOf(
            "g++",
            Paths.get(processPath, "problems", problemId, "files", "solution.cpp").absolutePathString(),
            "-o",
            Paths.get(processPath, "problems", problemId, "files", "solution.exe").absolutePathString(),
            "-O2",
            "-Wall",
            "-lm",
            "-static",
            "-std=gnu++26"
        )
    ).start().waitFor()
}

val submissionHistory = MutableList(0) { ProbJudgeResult() }

fun formatProbIOFiles(problemId: String) {
    Paths.get(processPath, "problems", problemId, "tests").toFile().listFiles { file ->
        file.isFile && file.name == file.nameWithoutExtension
    }!!.forEach {
        it.renameTo(File(it.absolutePath + ".in"))
    }

    Paths.get(processPath, "problems", problemId, "tests").toFile().listFiles { file ->
        file.isFile && file.name.endsWith(".a")
    }!!.forEach {
        it.renameTo(File(it.absolutePath.replace(".a", ".out")))
    }

    Paths.get(processPath, "problems", problemId, "tests").toFile().listFiles { file ->
        file.isFile && file.name.endsWith(".ans")
    }!!.forEach {
        it.renameTo(File(it.absolutePath.replace(".ans", ".out")))
    }
}

suspend fun runSingleFile(prob: ProbInfo, file: File): JudgeResult {
    val additionalTime = 1000L + UserSettings.timeBonusAdd

    val problemId = prob.id

    val exeFile = Paths.get(processPath, "judge", "moj_main.exe").absolutePathString()

    val judgeErr = Paths.get(processPath, "judge", "err").toFile().also {
        if (!it.exists()) it.createNewFile()
    }

    val judgeOut = Paths.get(processPath, "judge", "out").toFile().also {
        if (!it.exists()) it.createNewFile()
    }

    val solutionCpp = Paths.get(processPath, "problems", problemId, "files", "solution.cpp").toFile()
    val solutionExe = Paths.get(processPath, "problems", problemId, "files", "solution.exe").toFile()
    val solutionExeExists = solutionExe.exists()


    val checkerOut = Paths.get(processPath, "judge", "chkOut").toFile().also {
        if (!it.exists()) it.createNewFile()
        else it.writeText("")
    }

    val checkerErr = Paths.get(processPath, "judge", "chkErr").toFile().also {
        if (!it.exists()) it.createNewFile()
        else it.writeText("")
    }

    val checkerPath = Paths.get(processPath, "problems", problemId, "files", "check.exe").absolutePathString()

    val logFile = Paths.get(processPath, "judge", "log.txt").toFile()
    judgeErr.writeText("")
    judgeOut.writeText("")

    val ansPath = Paths.get(processPath, "problems", problemId, "tests", file.nameWithoutExtension + ".out")
    val ansFile = ansPath.toFile()


    if (!ansFile.exists()) {
        if (!solutionCpp.exists() && !solutionExeExists) {
            redPrintln("정답 파일과 정해 코드가 둘 다 없어 채점을 진행할 수 없습니다!")
            logFile.appendText("정답 파일과 정해 코드가 둘 다 없음\n")
            return JudgeResult(prob, JudgeResult.Result.FAILED, 0, null, null, LocalDateTime.now())
        }
        if (!solutionExeExists) {
            logFile.appendText("Compiling main correct solution..\n")

            val dur = measureTime {
                if (compileSolution(problemId) != 0) {
                    redPrintln("정해 컴파일이 실패했습니다.")
                    logFile.appendText("Failed to compile mcs\n")
                    return JudgeResult(prob, JudgeResult.Result.FAILED, 0, null, null, LocalDateTime.now())
                }
            }

            logFile.appendText("Main correct solution compiled in ${dur.inWholeMilliseconds} ms\n")
        }

        val outP = ProcessBuilder(listOf(solutionExe.absolutePath))
            .redirectInput(file)
            .redirectOutput(ansFile)
            .start()

        val dur = measureTime {
            outP.waitFor()
        }
        logFile.appendText("Output file ${ansFile.name} generated in ${dur.inWholeMilliseconds} ms\n")
    }

    val process = ProcessBuilder(exeFile)
        .redirectError(judgeErr)
        .redirectInput(file)
        .redirectOutput(judgeOut).start()

    val (exitCode, time, result, mem) = runWithLimits(
        process, prob.timeLimit * UserSettings.timeBonusMultiply + additionalTime, prob.memoryLimit
    )

    if (result == 3) {
        logFile.appendText("MLE at ${file.name} | $time ms | ${if (mem == -1L) "--" else mem.toString()} KB\n")
        return JudgeResult(prob,
            JudgeResult.Result.MEMORY_LIMIT_EXCEEDED,
            null,
            time,
            if(mem == -1L) null else mem,
            LocalDateTime.now())
    }

    if ((exitCode != null && exitCode != 0) || result == 4) {
        logFile.appendText("RE at ${file.name} | $time ms | ${if (mem == -1L) "--" else mem.toString()} KB\n")
        return JudgeResult(prob,
            JudgeResult.Result.RUNTIME_ERROR,
            null,
            time,
            if(mem == -1L) null else mem,
            LocalDateTime.now()
        )
    }

    if (exitCode == null || time >= prob.timeLimit) {
        logFile.appendText("TLE at ${file.name} | $time ms | ${if (mem == -1L) "--" else mem.toString()} KB\n")
        return JudgeResult(prob,
            JudgeResult.Result.TIME_LIMIT_EXCEEDED,
            null,
            time,
            if(mem == -1L) null else mem,
            LocalDateTime.now()
        )
    }

    checkerOut.writeText("")
    checkerErr.writeText("")

    val chkCode = ProcessBuilder(
        listOf(
            checkerPath,
            file.absolutePath,
            judgeOut.absolutePath,
            ansPath.absolutePathString(),
        )
    ).redirectError(checkerErr).redirectOutput(checkerOut).start().waitFor()

    if (chkCode != 0) {
        logFile.appendText("WA at ${file.name} | $time ms | ${if (mem == -1L) "--" else mem.toString()} KB\n")
        return JudgeResult(prob,
            JudgeResult.Result.WRONG_ANSWER,
            null,
            time,
            if(mem == -1L) null else mem,
            LocalDateTime.now()
        )
    }

    logFile.appendText("AC at ${file.name} | $time ms | ${if (mem == -1L) "--" else mem.toString()} KB\n")

    return JudgeResult(prob,
        JudgeResult.Result.ACCEPTED,
        exitCode,
        time,
        if(mem == -1L) null else mem,
        LocalDateTime.now()
    )
}

fun runSubtasks(problemId: String, showPercentage: Boolean = true) = runBlocking {
    formatProbIOFiles(problemId)

    val prob = ProbInfo(
        id = problemId,
        title = probJson.get("title").asString,
        timeLimit = probJson.get("timeLimit").asLong,
        memoryLimit = probJson.get("memoryLimitMb").asLong * 1024L,
        probType = ProbInfo.Type.valueOf(probJson.get("probType")?.asString?.uppercase() ?: "NORMAL"),
        json = probJson
    )

    val files = Paths.get(processPath, "problems", problemId, "tests").toFile().listFiles { file ->
        file.isFile && file.name.endsWith(".in")
    }!!

    var testCount = 0
    var subtaskCnt = 0

    val subtasks = probJson.get("subtasks").asJsonArray
    subtasks.forEachIndexed { i, subtask ->
        val number = subtask.asJsonObject.get("number").asInt
        testCount += files.filter { f -> f.name.startsWith("subtask$number-") }.size
        subtaskCnt++
    }

    if(testCount == 0) {
        clearPrevLine()
        redPrintln("$problemId 문제의 테스트 케이스가 없습니다.")
        return@runBlocking
    }

    val logFile = Paths.get(processPath, "judge", "log.txt").toFile()
    logFile.appendText("\n\nJudging problem $problemId.\n\n")

    var prevPercentage = 0
    clearPrevLine()
    if(showPercentage) orangePrintln("채점 중 (0%)")
    else orangePrintln("채점 중")

    val subtaskResult = MutableList(subtaskCnt) { JudgeResult(prob, JudgeResult.Result.FAILED, null, null, null, LocalDateTime.now()) }

    var ranTests = 0

    val finishedSubtasks = mutableSetOf<Int>()
    val subtaskNumberToIdx = mutableMapOf<Int, Int>()
    val subtaskInfos = mutableListOf<SubtaskInfo>()

    subtasks.forEachIndexed { i, subtask ->
        val number = subtask.asJsonObject.get("number").asInt
        subtaskNumberToIdx.put(number, i)

        var depList: MutableList<Int>? = null
        if(subtask.asJsonObject.has("dependency")) {
            depList = mutableListOf()
            subtask.asJsonObject.get("dependency").asJsonArray.forEach { dependency ->
                depList.add(dependency.asInt)
            }
        }

        subtaskInfos.addLast(SubtaskInfo(
            number,
            subtask.asJsonObject.get("point").asInt,
            subtask.asJsonObject.get("description").asString,
            depList
        ))
    }

    subtasks.forEachIndexed { i, subtask ->
        if(subtasks[i].asJsonObject.has("dependency")) {
            subtasks[i].asJsonObject.get("dependency").asJsonArray.forEach { dependency ->
                if(!finishedSubtasks.contains(dependency.asInt)) {
                    clearPrevLine()
                    redPrintln("$problemId 문제의 서브태스크 위계가 잘못되었습니다.")
                    return@runBlocking
                }
                if(subtaskResult[subtaskNumberToIdx[dependency.asInt]!!].result != JudgeResult.Result.ACCEPTED) {
                    subtaskResult[i] = subtaskResult[subtaskNumberToIdx[dependency.asInt]!!]
                    return@forEachIndexed
                }
            }
        }

        val number: Int = subtask.asJsonObject.get("number").asInt
        var judgeEnded = false

        files.filter { f -> f.name.startsWith("subtask$number-") }.forEach { file ->
            if(judgeEnded) return@forEach
            ranTests++
            val result = runSingleFile(prob, file)
            if(subtaskResult[i].timeMs == null ||
                (result.timeMs != null && subtaskResult[i].timeMs!! < result.timeMs!!)) {
                subtaskResult[i].timeMs = result.timeMs
            }
            if(subtaskResult[i].memoryKb == null ||
                (result.memoryKb != null && subtaskResult[i].memoryKb!! < result.memoryKb!!)) {
                subtaskResult[i].memoryKb = result.memoryKb
            }
            if(result.result != JudgeResult.Result.ACCEPTED) {
                subtaskResult[i].result = result.result
                judgeEnded = true
            }
            val p = ranTests * 100 / testCount
            if(p != prevPercentage && showPercentage) {
                prevPercentage = p
                clearPrevLine()
                orangePrintln("채점 중 (${ranTests * 100 / testCount}%)")
            }
        }
        if(!judgeEnded) subtaskResult[i].result = JudgeResult.Result.ACCEPTED

        subtaskResult[i].judgeTime = LocalDateTime.now()
        finishedSubtasks.add(number)
    }

    val result = SubtaskProbJudgeResult(prob,
        subtaskInfos,
        0,
        JudgeResult(prob, JudgeResult.Result.ACCEPTED, ranTests * 100 / testCount, null, null, LocalDateTime.now()),
        subtaskResult.run {
            val ret = MutableList(0) { 0 to JudgeResult(prob) }
            subtasks.forEachIndexed { i, subtask ->
                ret.addLast(subtask.asJsonObject.get("number").asInt to this[i])
            }
            ret.toList()
        })

    var fullPoint = 0

    subtaskResult.forEachIndexed { i, it ->
        if(result.allResult.result == JudgeResult.Result.ACCEPTED) result.allResult.result = it.result
        val p = subtasks[i].asJsonObject.get("point").asInt
        if(it.result == JudgeResult.Result.ACCEPTED) {
            result.point += p
        }
        fullPoint += p

        if(result.allResult.timeMs == null ||
            (it.timeMs != null && result.allResult.timeMs!! < it.timeMs!!)) {
            result.allResult.timeMs = it.timeMs
        }
        if(result.allResult.memoryKb == null ||
            (it.memoryKb != null && result.allResult.memoryKb!! < it.memoryKb!!)) {
            result.allResult.memoryKb = it.memoryKb
        }
    }

    clearPrevLine()
    var pointStr = ""
    pointStr += when (result.point) {
        fullPoint -> Ansi.GREEN + Ansi.BOLD + result.point.toString() + "점" + Ansi.RESET
        0 -> Ansi.RED + "틀렸습니다" + Ansi.RESET
        else -> Ansi.YELLOW + Ansi.BOLD + result.point.toString() + "점" + Ansi.RESET
    }

    println("$pointStr | " + result.allResult.defaultPrintWithoutResult())
    val list = MutableList(0) { List(0) { "" } }
    result.subtaskResult.forEach { (i, res) ->
        val p = subtasks[subtaskNumberToIdx[i]!!].asJsonObject.get("point").asInt
        list.addLast("  $i: ${p}점 | ${res.defaultPrint(false, showTime = false)}".split(" | "))
    }
    println(tableToStr(formatTable(list)))
    submissionHistory.addLast(
        ProbJudgeResult(
            prob, null, result
        )
    )
}

fun runSingle(problemId: String, showPercentage: Boolean = true) = runBlocking {
    formatProbIOFiles(problemId)

    val files = Paths.get(processPath, "problems", problemId, "tests").toFile().listFiles { file ->
        file.isFile && file.name.endsWith(".in")
    }

    val prob = ProbInfo(probJson)

    val testCount = files?.size ?: 0
    if(testCount == 0) {
        clearPrevLine()
        redPrintln("$problemId 문제의 테스트 케이스가 없습니다.")
        return@runBlocking
    }

    clearPrevLine()
    if(showPercentage) orangePrintln("채점 중 (0%)")
    else orangePrintln("채점 중")

    var mxt = 0L
    var mxm = -1L
    var prevPercent = 0

    val logFile = Paths.get(processPath, "judge", "log.txt").toFile()
    logFile.appendText("\n\nJudging problem $problemId.\n\n")

    files!!.forEachIndexed { i, file ->
        val res = runSingleFile(prob, file)

        mxt = maxOf(mxt, res.timeMs ?: -1)
        mxm = maxOf(mxm, res.memoryKb ?: -1)

        val percent = ((i + 1) * 100) / testCount

        if(res.result != JudgeResult.Result.ACCEPTED) {
            clearPrevLine()
            res.percent = percent
            println(res.defaultPrint(showPercentage = showPercentage))
            submissionHistory.addLast(ProbJudgeResult(prob, JudgeResult(
                prob,
                res.result,
                percent,
                mxt,
                if(mxm == -1L) null else mxm,
                LocalDateTime.now()
            )))
            return@runBlocking
        }

        if(prevPercent != percent && showPercentage) {
            clearPrevLine()
            orangePrintln("채점 중 ($percent%)")
            prevPercent = percent
        }
    }

    clearPrevLine()
    val result = JudgeResult(
        prob, JudgeResult.Result.ACCEPTED, 100, mxt, if(mxm == -1L) null else mxm, LocalDateTime.now()
    )
    println(result.defaultPrint(showPercentage = showPercentage))
    submissionHistory.addLast(ProbJudgeResult(prob, result))
    logFile.appendText("Accepted $problemId\n")
}

fun judge(problemId: String, showPercentage: Boolean = true) {
    probJson = JsonParser.parseString(
        Paths.get(processPath, "problems", problemId, "info.json").toFile().readText(Charset.forName("utf-8"))
    ).asJsonObject

    val probType = probJson.get("probType")?.asString ?: "normal"

    println()
    if(probType == "normal") {
        println("${probJson.get("title").asString}")
        println("시간 제한: ${probJson.get("timeLimit").asLong/1000.0} 초 | 메모리 제한: ${probJson.get("memoryLimitMb").asLong} MB")
        println()
    }
    if(probType == "subtask") {
        println("${probJson.get("title").asString} ${Ansi.GRAY}[서브태스크]${Ansi.RESET}")
        println("시간 제한: ${probJson.get("timeLimit").asLong/1000.0} 초 | 메모리 제한: ${probJson.get("memoryLimitMb").asLong} MB")
        println()

        val table = MutableList(0) { MutableList(0) { "" } }
        for(subtask in probJson.get("subtasks").asJsonArray.map { it.asJsonObject }) {
            table.addLast(mutableListOf(
                "  ${subtask.get("number").asInt}",
                subtask.get("point").asInt.toString() + "점",
                subtask.get("description").asString
            ))
        }
        println(wrapInABox(
            "${Ansi.CYAN}서브태스크${Ansi.RESET}\n" +
            tableToStr(formatTable(table)),
            Ansi.CYAN)
        )
        println()
    }

    yellowPrintln("채점 준비 중")

    if(compileChecker(problemId) != 0) {
        redPrintln("체커 컴파일이 실패했습니다.")
        return
    }

    val code = compileSubmission()
    if(code != 0) {
        clearPrevLine()
        purplePrintln("컴파일 에러")
        return
    }

    when (probType) {
        "normal" -> runSingle(problemId, showPercentage)
        "subtask" -> runSubtasks(problemId, showPercentage)
        else -> redPrintln("잘못된 문제 타입입니다.")
    }
    println()
}

fun main() = runBlocking {
    processPath = System.getProperty("user.dir")
    cli()
}
