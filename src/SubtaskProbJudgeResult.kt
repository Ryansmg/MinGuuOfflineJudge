
class SubtaskProbJudgeResult(
    val prob: ProbInfo,
    var subtasks: List<SubtaskInfo>,
    var point: Int,
    val allResult: JudgeResult,
    val subtaskResult: List<Pair<Int, JudgeResult>>,
) {
    fun toStringFull(): String {
        val subtaskNumberToIdx = mutableMapOf<Int, Int>()
        subtasks.forEachIndexed {
            idx, subtask -> subtaskNumberToIdx[subtask.number] = idx
        }

        val list = MutableList(0) { List(0) { "" } }
        subtaskResult.forEach { (i, res) ->
            val p = subtasks[subtaskNumberToIdx[i]!!].point
            list.addLast("  $i: ${p}점 | ${res.defaultPrint(false, showTime = false)}".split(" | "))
        }
        return toStringBrief() + "\n" + tableToStr(formatTable(list))
    }

    fun toStringBrief(): String {
        var fullPoint = 0
        val subtaskNumberToIdx = mutableMapOf<Int, Int>()
        subtasks.forEachIndexed {
            idx, subtask -> subtaskNumberToIdx[subtask.number] = idx
            fullPoint += subtask.point
        }
        var pointStr = ""
        pointStr += when (point) {
            fullPoint -> Ansi.GREEN + Ansi.BOLD + point.toString() + "점" + Ansi.RESET
            0 -> Ansi.RED + "틀렸습니다" + Ansi.RESET
            else -> Ansi.YELLOW + Ansi.BOLD + point.toString() + "점" + Ansi.RESET
        }
        return "${prob.title} | $pointStr | " + allResult.defaultPrintWithoutResult()
    }
}
