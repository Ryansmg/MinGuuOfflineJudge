import java.time.LocalDateTime

class JudgeResult(
    var prob: ProbInfo,
    var result: Result,
    var percent: Int?,
    var timeMs: Long?,
    var memoryKb: Long?,
    var judgeTime: LocalDateTime
) {
    constructor(prob: ProbInfo) : this(
        prob,
        Result.FAILED,
        null,
        null,
        null,
        LocalDateTime.now()
    )

    enum class Result {
        ACCEPTED,
        WRONG_ANSWER,
        COMPILE_ERROR,
        RUNTIME_ERROR,
        TIME_LIMIT_EXCEEDED,
        MEMORY_LIMIT_EXCEEDED,
        FAILED;

        override fun toString(): String {
            return when (this) {
                ACCEPTED -> Ansi.GREEN + Ansi.BOLD + "맞았습니다!!" + Ansi.RESET
                WRONG_ANSWER -> Ansi.RED + "틀렸습니다" + Ansi.RESET
                COMPILE_ERROR -> Ansi.PURPLE + "컴파일 에러" + Ansi.RESET
                RUNTIME_ERROR -> Ansi.PURPLE + "런타임 에러" + Ansi.RESET
                TIME_LIMIT_EXCEEDED -> Ansi.ORANGE + "시간 초과" + Ansi.RESET
                MEMORY_LIMIT_EXCEEDED -> Ansi.ORANGE + "메모리 초과" + Ansi.RESET
                FAILED -> "채점 실패"
            }
        }
    }

    fun defaultPrint(showPercentage: Boolean = true): String {
        if(result == Result.ACCEPTED || !showPercentage) return "$result | ${timeMs?.toString() ?: "--"} ms | ${memoryKb?.toString() ?: "--"} KB"
        return "$result (${percent?.toString() ?: "0"}%) | ${timeMs?.toString() ?: "--"} ms | ${memoryKb?.toString() ?: "--"} KB"
    }

    fun defaultPrintWithoutResult(): String {
        return "${timeMs?.toString() ?: "--"} ms | ${memoryKb?.toString() ?: "--"} KB"
    }
}
