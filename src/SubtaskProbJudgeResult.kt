
class SubtaskProbJudgeResult(
    val prob: ProbInfo,
    var point: Int,
    val allResult: JudgeResult,
    val subtaskResult: List<Pair<Int, JudgeResult>>,
)
