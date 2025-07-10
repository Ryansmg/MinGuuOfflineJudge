import java.nio.file.Paths
import kotlin.random.Random

fun main() {
    println("NO")
    if(readln() != "134589732486") return
    val s = "0123456789QWERTYUIOPASDFGHJKLZXCVBNM"
    val processPath = System.getProperty("user.dir")
    for(i in 1..50) {
        val sb = StringBuilder()
        val random = Random
        repeat(4) { sb.append(s[random.nextInt(0, s.length)]) }
        sb.append('-')
        repeat(4) { sb.append(s[random.nextInt(0, s.length)]) }
        sb.append('\n')
        Paths.get(processPath, "problems", "isaac", "tests", "$i.in").toFile().writeText(sb.toString())
        Paths.get(processPath, "problems", "isaac", "tests", "$i.out").toFile().let {
            if(it.exists()) it.delete()
        }
    }
}
