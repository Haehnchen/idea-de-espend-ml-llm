package de.espend.ml.llm.session.adapter.opencode

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.ToolInputFormatter
import de.espend.ml.llm.session.util.ToolOutputFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.nio.file.Paths
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import java.nio.file.Path as JavaPath

/**
 * Project info stored by OpenCode.
 */
@Serializable
data class OpenCodeProjectInfo(
    val id: String,
    val worktree: String,
    val vcs: String? = null
)

/**
 * Session data stored by OpenCode.
 */
@Serializable
data class OpenCodeSessionData(
    val id: String,
    val slug: String,
    val projectID: String,
    val directory: String,
    val title: String,
    val time: OpenCodeTime,
    val summary: OpenCodeSummary? = null
)

/**
 * Timestamps for OpenCode session.
 */
@Serializable
data class OpenCodeTime(
    val created: Long,
    val updated: Long
)

/**
 * Summary statistics for OpenCode session.
 */
@Serializable
data class OpenCodeSummary(
    val additions: Int,
    val deletions: Int,
    val files: Int
)

/**
 * Message data stored by OpenCode.
 */
@Serializable
data class OpenCodeMessageData(
    val id: String,
    val role: String,
    val time: OpenCodeMessageTime? = null,
    val model: OpenCodeModelInfo? = null,
    val error: OpenCodeMessageError? = null
)

/**
 * Timestamps for OpenCode message.
 */
@Serializable
data class OpenCodeMessageTime(
    val created: Long? = null,
    val completed: Long? = null
)

/**
 * Model info stored in OpenCode messages.
 */
@Serializable
data class OpenCodeModelInfo(
    val providerID: String? = null,
    val modelID: String? = null
)

/**
 * Error data stored in OpenCode messages.
 */
@Serializable
data class OpenCodeMessageError(
    val name: String? = null,
    val data: OpenCodeErrorData? = null
)

/**
 * Detailed error data.
 */
@Serializable
data class OpenCodeErrorData(
    val message: String? = null,
    val statusCode: Int? = null,
    val isRetryable: Boolean? = null,
    val responseBody: String? = null,
    val metadata: OpenCodeErrorMetadata? = null
)

/**
 * Error metadata.
 */
@Serializable
data class OpenCodeErrorMetadata(
    val url: String? = null
)

/**
 * Tool state stored in OpenCode tool parts.
 */
@Serializable
data class OpenCodeToolState(
    val status: String? = null,
    val input: JsonElement? = null,
    val output: JsonElement? = null,
    val error: JsonElement? = null,
    val title: String? = null
)

/**
 * Part data stored by OpenCode.
 */
@Serializable
data class OpenCodePartData(
    val id: String,
    val type: String,
    val text: String? = null,
    val tool: String? = null,
    val callID: String? = null,
    val time: OpenCodePartTime? = null,
    val state: OpenCodeToolState? = null
)

/**
 * Timestamps for OpenCode part.
 */
@Serializable
data class OpenCodePartTime(
    val start: Long? = null,
    val end: Long? = null
)

/**
 * Raw message data loaded from disk before parsing into Message objects.
 */
data class RawOpenCodeMessage(
    val messageData: OpenCodeMessageData?,
    val parts: List<OpenCodePartData>,
    val rawContent: String,
    val filePath: String,
    val parseError: Exception? = null
)

/**
 * Standalone parser for OpenCode session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object OpenCodeSessionParser {

    internal val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parses an OpenCode session and returns SessionDetail.
     */
    fun parseSession(searchId: String): SessionDetail? {
        val sessionData = findSessionData(searchId) ?: return null
        val actualSessionId = sessionData.id
        val result = loadMessagesWithParts(actualSessionId)

        val metadata = SessionMetadata(
            cwd = sessionData.directory,
            created = formatTimestamp(sessionData.time.created),
            modified = formatTimestamp(sessionData.time.updated),
            messageCount = result.messageFileCount,
            models = result.sortedModels
        )

        return SessionDetail(
            sessionId = actualSessionId,
            title = sessionData.title,
            messages = result.messages,
            metadata = metadata
        )
    }

    /**
     * Finds session data by ID.
     */
    fun findSessionData(sessionId: String): OpenCodeSessionData? {
        val storageDir = getStorageDir() ?: return null
        val sessionDir = storageDir.resolve("session")

        // Search through all project directories for the session file
        sessionDir.listDirectoryEntries().forEach { projectDir ->
            if (!projectDir.isDirectory()) return@forEach

            projectDir.listDirectoryEntries("*.json").forEach { sessionFile ->
                try {
                    val content = sessionFile.readText()
                    val session = JSON.decodeFromString<OpenCodeSessionData>(content)
                    if (session.id == sessionId) return session
                } catch (_: Exception) {
                    // Continue searching
                }
            }
        }

        return null
    }

    /**
     * Loads raw messages from a session directory.
     */
    fun loadRawMessages(sessionId: String): List<RawOpenCodeMessage> {
        val storageDir = getStorageDir() ?: return emptyList()
        val messagesDir = storageDir.resolve("message").resolve(sessionId)
        val partsDir = storageDir.resolve("part")

        if (!messagesDir.exists()) {
            return emptyList()
        }

        val loadedMessages = mutableListOf<RawOpenCodeMessage>()

        messagesDir.listDirectoryEntries("*.json").forEach { messageFile ->
            val rawContent = try {
                messageFile.readText()
            } catch (e: Exception) {
                "[Failed to read file: ${e.message}]"
            }

            try {
                val messageData = JSON.decodeFromString<OpenCodeMessageData>(rawContent)
                val parts = loadPartsFromDir(partsDir, messageData.id)
                loadedMessages.add(RawOpenCodeMessage(messageData, parts, rawContent, messageFile.toString()))
            } catch (e: Exception) {
                loadedMessages.add(RawOpenCodeMessage(null, emptyList(), rawContent, messageFile.toString(), e))
            }
        }

        loadedMessages.sortBy { it.messageData?.time?.created ?: Long.MAX_VALUE }
        return loadedMessages
    }

    /**
     * Loads parts for a message from the parts directory.
     */
    private fun loadPartsFromDir(partsDir: JavaPath, messageId: String): List<OpenCodePartData> {
        val messagePartsDir = partsDir.resolve(messageId)

        if (!messagePartsDir.exists()) {
            return emptyList()
        }

        val parts = mutableListOf<OpenCodePartData>()

        messagePartsDir.listDirectoryEntries("*.json").forEach { partFile ->
            try {
                val content = partFile.readText()
                val partData = JSON.decodeFromString<OpenCodePartData>(content)
                parts.add(partData)
            } catch (e: Exception) {
                // Skip failed parts
            }
        }

        parts.sortBy { it.time?.start ?: it.time?.end ?: Long.MAX_VALUE }
        return parts
    }

    /**
     * Loads all messages for a session.
     */
    fun loadMessagesWithParts(sessionId: String): MessageParseResult {
        val loadedMessages = loadRawMessages(sessionId)

        if (loadedMessages.isEmpty()) {
            return MessageParseResult(emptyList(), emptyList(), 0)
        }

        val modelCounts = mutableMapOf<String, Int>()

        for (loaded in loadedMessages) {
            val messageData = loaded.messageData ?: continue

            val modelId = messageData.model?.modelID
            if (modelId != null) {
                modelCounts[modelId] = (modelCounts[modelId] ?: 0) + 1
            }
        }

        // Parse all messages (ToolResults are already nested in ToolUse)
        val messages = mutableListOf<ParsedMessage>()
        for (loaded in loadedMessages) {
            messages.addAll(parseRawMessage(loaded))
        }

        val sortedModels = modelCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        return MessageParseResult(
            messages = messages,
            sortedModels = sortedModels,
            messageFileCount = loadedMessages.size
        )
    }

    /**
     * Parses a single RawOpenCodeMessage into ParsedMessage objects.
     */
    fun parseRawMessage(loaded: RawOpenCodeMessage): List<ParsedMessage> {
        val messageData = loaded.messageData
        val parts = loaded.parts
        val timestamp = formatTimestamp(messageData?.time?.created)

        if (messageData == null) {
            return listOf(
                ParsedMessage.Info(
                    timestamp = timestamp,
                    title = "error",
                    subtitle = "parse",
                    content = MessageContent.Text("Failed to parse message file: ${loaded.filePath}\n${loaded.rawContent}"),
                    style = ParsedMessage.InfoStyle.ERROR
                )
            )
        }

        return when (messageData.role) {
            "user" -> parseUserMessage(loaded, parts, timestamp)
            "assistant" -> parseAssistantMessage(loaded, messageData, parts, timestamp)
            else -> listOf(
                ParsedMessage.Info(
                    timestamp = timestamp,
                    title = messageData.role,
                    content = MessageContent.Json(loaded.rawContent)
                )
            )
        }
    }

    private fun parseUserMessage(
        loaded: RawOpenCodeMessage,
        parts: List<OpenCodePartData>,
        timestamp: String
    ): List<ParsedMessage> {
        val text = combineTextParts(parts)
        val content = if (text.isNotEmpty()) {
            listOf(MessageContent.Text(text))
        } else if (parts.isEmpty()) {
            listOf(MessageContent.Code(loaded.rawContent))
        } else {
            listOf(
                MessageContent.Text("User message with ${parts.size} part(s), no text content"),
                MessageContent.Code(loaded.rawContent)
            )
        }
        return listOf(
            ParsedMessage.User(
                timestamp = timestamp,
                content = content
            )
        )
    }

    private fun parseAssistantMessage(
        loaded: RawOpenCodeMessage,
        messageData: OpenCodeMessageData,
        parts: List<OpenCodePartData>,
        timestamp: String
    ): List<ParsedMessage> {
        val partMessages = mutableListOf<ParsedMessage>()

        for (part in parts) {
            val partTimestamp = formatTimestamp(part.time?.start ?: part.time?.end)
                .ifEmpty { timestamp }

            when (part.type) {
                "text" -> {
                    val text = part.text?.trim()
                    if (!text.isNullOrEmpty()) {
                        partMessages.add(
                            ParsedMessage.AssistantText(
                                timestamp = partTimestamp,
                                content = listOf(MessageContent.Markdown(text))
                            )
                        )
                    }
                }

                "reasoning" -> {
                    val text = part.text?.trim()
                    if (!text.isNullOrEmpty()) {
                        partMessages.add(
                            ParsedMessage.AssistantThinking(
                                timestamp = partTimestamp,
                                thinking = text
                            )
                        )
                    }
                }

                "tool" -> {
                    partMessages.addAll(parseToolPart(part, partTimestamp))
                }

                "step-start", "step-finish" -> {
                    // Metadata parts - skip
                }
            }
        }

        return if (partMessages.isNotEmpty()) {
            partMessages
        } else {
            // Check if there's an error at the message level
            if (messageData.error != null) {
                val errorContent = formatErrorContent(messageData.error, loaded.rawContent)
                listOf(
                    ParsedMessage.Info(
                        timestamp = timestamp,
                        title = "error",
                        subtitle = messageData.error.name,
                        content = MessageContent.Text(errorContent),
                        style = ParsedMessage.InfoStyle.ERROR
                    )
                )
            } else {
                listOf(
                    ParsedMessage.AssistantText(
                        timestamp = timestamp,
                        content = listOf(MessageContent.Code("Assistant message with 0 part(s)\n${loaded.rawContent}"))
                    )
                )
            }
        }
    }

    private fun formatErrorContent(error: OpenCodeMessageError?, rawContent: String): String {
        if (error == null) {
            return "Assistant message with 0 part(s)\n$rawContent"
        }

        val errorMsg = error.data?.message
        if (errorMsg != null) {
            return errorMsg
        }

        // No error message, show raw JSON
        return rawContent
    }

    private fun parseToolPart(part: OpenCodePartData, timestamp: String): List<ParsedMessage> {
        val toolName = part.tool ?: "tool"
        val state = part.state

        val inputMap = ToolInputFormatter.jsonToMap(state?.input)

        // Build results list directly if the tool has completed or errored
        val results = mutableListOf<ParsedMessage.ToolResult>()
        val status = state?.status
        if (status == "completed" || status == "error") {
            val outputElement = if (status == "error") state.error else state.output
            val outputBlocks = ToolOutputFormatter.formatToolOutput(outputElement)

            results.add(
                ParsedMessage.ToolResult(
                    timestamp = timestamp,
                    toolCallId = part.callID,
                    output = outputBlocks,
                    isError = status == "error"
                )
            )
        }

        // Return ToolUse with nested results directly (no need for post-processing)
        return listOf(
            ParsedMessage.ToolUse(
                timestamp = timestamp,
                toolName = toolName,
                toolCallId = part.callID,
                input = inputMap,
                results = results
            )
        )
    }

    private fun combineTextParts(parts: List<OpenCodePartData>): String {
        return parts
            .filter { it.type == "text" && !it.text.isNullOrBlank() }
            .mapNotNull { it.text?.trim() }
            .joinToString("\n\n")
            .trim()
    }

    private fun formatTimestamp(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis == 0L) {
            return ""
        }
        return try {
            val instant = Instant.ofEpochMilli(epochMillis)
            DateTimeFormatter.ISO_INSTANT.format(instant)
        } catch (e: Exception) {
            ""
        }
    }

    private fun getStorageDir(): JavaPath? {
        val homeDir = System.getProperty("user.home")
        val storageDir = Paths.get(homeDir, ".local", "share", "opencode", "storage")
        return if (storageDir.exists()) storageDir else null
    }

    /**
     * Result of parsing messages.
     */
    data class MessageParseResult(
        val messages: List<ParsedMessage>,
        val sortedModels: List<Pair<String, Int>>,
        val messageFileCount: Int
    )
}
