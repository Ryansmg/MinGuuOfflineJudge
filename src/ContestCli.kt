import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.Charset
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlin.time.Duration

class ContestInfo(
    var id: String,
    var title: String,
    var duration: Long, // seconds
    var alphabetNumbering: Boolean,
    var penalty: Int,
    var penaltyAccumulateCalc: Boolean,
    var ignoreCompileError: Boolean,
    var defaultPoint: Int, // 섭테 문제가 아닐 때 부여할 점수
    var problems: List<String>
) {
    constructor(json: JsonObject): this(
        json.get("id").asString,
        json.get("title").asString,
        json.get("duration").asLong,
        json.get("numbering").asString == "A",
        json.get("penalty").asInt,
        json.get("penaltyCalc").asString == "accumulate",
        json.get("ignoreCompileError").asBoolean,
        json.get("defaultPoint").asInt,
        json.get("problems").asJsonArray.map { it.asString }
    )
}

fun contestCli(id: String) {
    val contest = ContestInfo(JsonParser.parseString(
            Paths.get(processPath, "contests", "$id.json").toFile().readText(Charset.forName("utf-8"))
        ).asJsonObject)
    val titleWidth = maxOf(strWidth(contest.title) + 8, 66)
    val startTime = LocalDateTime.now()

    val endTime = LocalDateTime.ofEpochSecond(
        startTime.toEpochSecond(ZoneOffset.UTC) + contest.duration,
        0, ZoneOffset.UTC)

    var point = 0
    var penalty = 0
    val probPoint = mutableListOf<Int>()
    val probFullPoint = mutableListOf<Int>()
    val probTimePenalty = mutableListOf<Int>()
    val probSubmitPenalty = mutableListOf<Int>()
    val submitCount = mutableListOf<Int>()
    val probs = mutableListOf<ProbInfo>()
    val history = mutableListOf<ProbJudgeResult>()

    contest.problems.forEach { id ->
        probPoint.addLast(0)
        probTimePenalty.addLast(0)
        probSubmitPenalty.addLast(0)
        submitCount.addLast(0)
        val p = ProbInfo(id)
        if(p.probType == ProbInfo.Type.SUBTASK) probFullPoint.addLast(p.fullPoint())
        else probFullPoint.addLast(contest.defaultPoint)
        probs.addLast(p)
    }

    var selected = 0

    while(true) {
        UserSettings.refresh()
        writer.print(Ansi.CLEAR_DISPLAY + Ansi.CURSOR_UL)

        writer.println(
            """
        ${"=".repeat(titleWidth)}
        ${centerStr(contest.title, titleWidth)}
        ${"=".repeat(titleWidth)}
        ${Ansi.YELLOW + Ansi.BOLD}${padFront(
            startTime.format(formatter) + " ~ " + endTime.format(formatter),
            titleWidth
        )}${Ansi.RESET + Ansi.YELLOW}
        ${padFront(
            "현재 시간: ${LocalDateTime.now().format(formatter)} (${(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - startTime.toEpochSecond(ZoneOffset.UTC))/60}분 / ${(endTime.toEpochSecond(ZoneOffset.UTC) - startTime.toEpochSecond(ZoneOffset.UTC))/60}분)",
            titleWidth
        )}${Ansi.RESET}
        
        ${
                centerStr(
                    "${Ansi.GREEN}${point}${Ansi.RESET} / ${Ansi.RED}${penalty}${Ansi.RESET}",
                    titleWidth
                )
            }
        
    """.trimIndent()
        )

        val lineLimit = 5;
        for(j in 0..<(probs.size+lineLimit-1)/lineLimit) {

            val probList = listOf(mutableListOf(), mutableListOf<String>())
            val idx = minOf(probs.size, j * lineLimit + lineLimit)

            for (i in j*lineLimit..<idx) {
                val probBuilder = StringBuilder()
                if (submitCount[i] == 0) probBuilder.append(Ansi.RESET)
                else if (probPoint[i] == 0) probBuilder.append(Ansi.RED)
                else if (probPoint[i] == probFullPoint[i]) probBuilder.append(Ansi.GREEN + Ansi.BOLD)
                else probBuilder.append(Ansi.YELLOW + Ansi.BOLD)

                if (contest.alphabetNumbering) probBuilder.append('A' + i)
                else probBuilder.append(i + 1)
                probBuilder.append(" [${probPoint[i]}]")

                probBuilder.append(Ansi.RESET)

                probList[0].add(probBuilder.toString())
            }

            for (i in j*lineLimit..<idx) {
                val s = "${
                    if(probPoint[i] == 0) submitCount[i]
                    else probSubmitPenalty[i] / contest.penalty
                } / ${
                    if(probPoint[i] == 0) "--"
                    else probTimePenalty[i].toString()
                }"
                probList[1].add(s)
            }
            val pl2 = formatTable(probList)
            val pl3 = listOf(mutableListOf(), mutableListOf<String>())
            for(i in pl2[0]) pl3[0].add(centerPaddedStr(i))
            for(i in pl2[1]) pl3[1].add(i)

            writer.println(centerStr(tableToStr(pl3, "   "), titleWidth))
            writer.println()
        }


        var ended = false

        if(LocalDateTime.now() < endTime) {
            writer.println(
                centerStr(
                    wrapInABox(
                        "${if (selected == 0) Ansi.CYAN + Ansi.BOLD else ""}제출하기${Ansi.RESET}    " +
                                "${if (selected == 1) Ansi.CYAN + Ansi.BOLD else ""}제출 기록${Ansi.RESET}    " +
                                "${if (selected == 2) Ansi.CYAN + Ansi.BOLD else ""}대회 종료${Ansi.RESET}", Ansi.CYAN
                    ), titleWidth
                )
            )
        }
        else {
            writer.println(
                centerStr(
                    wrapInABox(
                        Ansi.RED + Ansi.BOLD + "대회가 종료되었습니다." + Ansi.RESET, Ansi.RED
                    ), titleWidth
                )
            )
            ended = true
        }
//        writer.println()
//        probs.forEachIndexed { i, prob ->
//            if (contest.alphabetNumbering) writer.print('A' + i)
//            else writer.print(i + 1)
//
//            writer.println(". ${prob.title}")
//        }

        val c = getCWithTimeout(30000L) ?: continue
        if(c.special == GetCResult.LEFT_ARROW) {
            selected = maxOf(selected - 1, 0)
        }
        else if(c.special == GetCResult.RIGHT_ARROW) {
            selected = minOf(selected + 1, 2)
        }
        else if(c.special == GetCResult.CRLF || c.char == '\n' || c.char == '\r') {
            if(ended) {
                clearDisplay()
                println(wrapInABox("""
                    대회를 종료할까요? (Y/N)
                """.trimIndent(), Ansi.RED))
                println()
                print("> ")
                if(readln().lowercase().contains("y")) break
            }
            else if(selected == 0) {
                writer.flush()
                clearDisplay()
                println()
                println("제출할 문제의 번호를 입력하세요:")
                println()
                probs.forEachIndexed { i, prob ->
                    if (contest.alphabetNumbering) writer.print('A' + i)
                    else writer.print(i + 1)

                    writer.println(". ${prob.title}")
                }
                println()
                print("> ")
                val input = readln().uppercase()
                var select = -1
                probs.forEachIndexed { i, prob ->
                    val num = if (contest.alphabetNumbering) ('A' + i).toString()
                    else (i + 1).toString()

                    if(num == input) select = i
                }

                if(select == -1) {
                    yellowPrintln("잘못된 문제 번호입니다.")
                    readln()
                    continue
                }

                clearDisplay()
                judge(probs[select].id, false)

                val result = submissionHistory.last()
                history.addLast(result)

                if((result.prob.probType == ProbInfo.Type.NORMAL &&
                        result.normalResult!!.result == JudgeResult.Result.COMPILE_ERROR) ||
                    (result.prob.probType == ProbInfo.Type.SUBTASK &&
                            result.subtaskResult!!.allResult.result == JudgeResult.Result.COMPILE_ERROR)) {
                    if(contest.ignoreCompileError) submitCount[select]--
                }

                submitCount[select]++
                val p =
                    if(result.prob.probType == ProbInfo.Type.NORMAL) {
                        if(result.normalResult!!.result == JudgeResult.Result.ACCEPTED) contest.defaultPoint
                        else 0
                    } else {
                        result.subtaskResult!!.point
                    }
                val t =
                    if(result.prob.probType == ProbInfo.Type.NORMAL) {
                        result.normalResult!!.judgeTime
                    } else result.subtaskResult!!.allResult.judgeTime

                if(probPoint[select] < p) {
                    probPoint[select] = p
                    probSubmitPenalty[select] = contest.penalty * (submitCount[select] - 1)
                    // 설마 2147483647분을 넘겠어
                    probTimePenalty[select] = ((t.toEpochSecond(ZoneOffset.UTC) - startTime.toEpochSecond(ZoneOffset.UTC)) / 60L).toInt()
                    point = probPoint.sum()
                    penalty = 0
                    for(i in 0..<probs.size) {
                        if(probPoint[i] > 0) penalty += probTimePenalty[i] + probSubmitPenalty[i]
                    }
                }

                println("Enter 키를 눌러 돌아가세요.")
                readln()

            } else if(selected == 1) {
                clearDisplay()
                println("=== 대회 제출 기록 ===\n")
                history.asReversed().forEach { println(it.display(showPercentage = false)); println() }

                ignorePrevInput()
                println("\nEnter 키를 눌러 돌아가세요.")
                readln()
            } else {
                clearDisplay()
                println(wrapInABox("""
                    대회를 종료할까요? (Y/N)
                """.trimIndent(), Ansi.RED))
                println()
                print("> ")
                if(readln().lowercase().contains("y")) break
            }
        }
    }
}
