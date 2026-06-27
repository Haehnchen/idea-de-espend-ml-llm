package de.espend.ml.llm.difftastic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

@Serializable
data class DifftasticFileDiff(
    @SerialName("language")
    val language: String? = null,
    @SerialName("path")
    val path: String? = null,
    @SerialName("status")
    val status: String? = null,
    @SerialName("aligned_lines")
    val alignedLines: List<List<Int?>> = emptyList(),
    @SerialName("chunks")
    val chunks: List<List<DifftasticLineDiff>> = emptyList(),
)

@Serializable
data class DifftasticLineDiff(
    @SerialName("lhs")
    val lhs: DifftasticSideDiff? = null,
    @SerialName("rhs")
    val rhs: DifftasticSideDiff? = null,
)

@Serializable
data class DifftasticSideDiff(
    @SerialName("line_number")
    val lineNumber: Int,
    @SerialName("changes")
    val changes: List<DifftasticChange> = emptyList(),
)

@Serializable
data class DifftasticChange(
    @SerialName("start")
    val start: Int,
    @SerialName("end")
    val end: Int,
    @SerialName("content")
    val content: String = "",
    @SerialName("highlight")
    val highlight: String? = null,
)

object DifftasticJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(output: String): DifftasticFileDiff {
        val element = json.parseToJsonElement(output.trim())
        return when (element) {
            is JsonObject -> json.decodeFromJsonElement(element)
            is JsonArray -> json.decodeFromJsonElement(element.jsonArray.first())
            else -> error("Unsupported difftastic JSON root: ${element::class.simpleName}")
        }
    }
}
