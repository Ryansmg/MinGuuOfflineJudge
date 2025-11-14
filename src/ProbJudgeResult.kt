class ProbJudgeResult(
    val prob: ProbInfo,
    val normalResult: JudgeResult?,
    val subtaskResult: SubtaskProbJudgeResult? = null
) {
    constructor() : this(
        ProbInfo(),
        null
    )
    fun display(compact: Boolean = false, showPercentage: Boolean = true): String {
        return when(prob.probType) {
            ProbInfo.Type.DUMMY -> "ERROR: probType is DUMMY"
            ProbInfo.Type.NORMAL -> "${prob.title} | ${normalResult!!.defaultPrint(showPercentage = showPercentage)}"
            ProbInfo.Type.SUBTASK -> if(compact) subtaskResult!!.toStringBrief() else subtaskResult!!.toStringFull()
        }
    }
}