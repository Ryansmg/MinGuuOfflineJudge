import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Paths

class ProbInfo(
    /**
     * ms
     */
    val timeLimit: Long,
    /**
     * kb
     */
    val memoryLimit: Long,
    val id: String,
    val title: String,
    val probType: Type,
    val json: JsonObject
) {
    constructor() : this(0, 0, "", "", Type.DUMMY, JsonObject())
    constructor(json: JsonObject): this(
        json["timeLimit"].asLong,
        json["memoryLimitMb"].asLong * 1024,
        json["id"].asString,
        json["title"].asString,
        if(json["probType"]?.isJsonNull ?: true) Type.NORMAL else Type.valueOf(json["probType"].asString.uppercase()),
        json
    )
    constructor(id: String): this(
        JsonParser.parseString(
            Paths.get(processPath, "problems", id, "info.json").toFile().readText()
        ).asJsonObject
    )
    enum class Type {
        DUMMY,
        NORMAL,
        SUBTASK
    }

    fun fullPoint(): Int {
        if(probType != Type.SUBTASK) return -1
        var p = 0
        json["subtasks"].asJsonArray.map { it.asJsonObject }.forEach {
            p += it["point"].asInt
        }
        return p
    }
}