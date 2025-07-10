class ProbJudgeResult(
    val prob: ProbInfo,
    val normalResult: JudgeResult?,
    val subtaskResult: SubtaskProbJudgeResult? = null
)