package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import de.espend.ml.llm.session.util.ToolInputFormatter
import de.espend.ml.llm.session.util.ToolOutputFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Paths
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
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
    val time: OpenCodeTime
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
    val input: kotlinx.serialization.json.JsonElement? = null,
    val output: kotlinx.serialization.json.JsonElement? = null,
    val error: kotlinx.serialization.json.JsonElement? = null,
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
 * Contains the message metadata, its parts, and raw content for error recovery.
 */
data class RawOpenCodeMessage(
    val messageData: OpenCodeMessageData?,
    val parts: List<OpenCodePartData>,
    val rawContent: String,
    val filePath: String,
    val parseError: Exception? = null
)

/**
 * Creates a virtual session directory from OpenCode's storage structure.
 * OpenCode stores messages in storage/message/{sessionId}/ and parts in storage/part/{messageId}/.
 * This function returns a path object that resolves "message" and "part" to the correct locations.
 */
fun OpenCodeStorageLayout(storageDir: JavaPath, sessionId: String): JavaPath {
    return object : JavaPath by storageDir {
        override fun resolve(other: String): JavaPath = when (other) {
            "message" -> storageDir.resolve("message").resolve(sessionId)
            "part" -> storageDir.resolve("part")
            else -> storageDir.resolve(other)
        }
        override fun resolve(other: JavaPath): JavaPath = resolve(other.toString())
        override fun relativize(other: JavaPath): JavaPath = storageDir.relativize(other)
        override fun register(
            watcher: java.nio.file.WatchService,
            events: Array<out java.nio.file.WatchEvent.Kind<*>>,
            vararg modifiers: java.nio.file.WatchEvent.Modifier
        ): java.nio.file.WatchKey = storageDir.register(watcher, events, *modifiers)
    }
}

/**
 * Adapter for reading and parsing OpenCode session files.
 */
class OpenCodeSessionAdapter(private val project: Project) {

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Loads raw message and parts data from a session directory.
         * This separates file I/O from message parsing, enabling fixture-based testing.
         *
         * @param sessionDir The session directory containing 'message' and 'part' subdirectories
         * @return List of raw messages with their parts, sorted by creation time
         */
        fun loadRawMessages(sessionDir: JavaPath): List<RawOpenCodeMessage> {
            val messagesDir = sessionDir.resolve("message")
            val partsDir = sessionDir.resolve("part")

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
         * Loads parts for a message from a given parts directory.
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
                } catch (_: Exception) {
                    // Skip malformed part files
                }
            }

            parts.sortBy { it.time?.start ?: it.time?.end ?: Long.MAX_VALUE }
            return parts
        }

        /**
         * Parses a single RawOpenCodeMessage into a list of ParsedMessage objects.
         * Visible for testing.
         */
        fun parseRawMessage(loaded: RawOpenCodeMessage): List<ParsedMessage> {
            val messageData = loaded.messageData
            val parts = loaded.parts
            val timestamp = formatTimestamp(messageData?.time?.created)

            if (messageData == null) {
                return listOf(ParsedMessage.Info(
                    timestamp = timestamp,
                    title = "error",
                    subtitle = "parse",
                    content = MessageContent.Text("Failed to parse message file: ${loaded.filePath}\n${loaded.rawContent}"),
                    style = ParsedMessage.InfoStyle.ERROR
                ))
            }

            return when (messageData.role) {
                "user" -> parseUserMessage(loaded, parts, timestamp)
                "assistant" -> parseAssistantMessage(loaded, messageData, parts, timestamp)
                else -> listOf(ParsedMessage.Info(
                    timestamp = timestamp,
                    title = messageData.role,
                    content = MessageContent.Json(loaded.rawContent)
                ))
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
            return listOf(ParsedMessage.User(
                timestamp = timestamp,
                content = content
            ))
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
                            partMessages.add(ParsedMessage.AssistantText(
                                timestamp = partTimestamp,
                                content = listOf(MessageContent.Markdown(text))
                            ))
                        }
                    }
                    "reasoning" -> {
                        val text = part.text?.trim()
                        if (!text.isNullOrEmpty()) {
                            partMessages.add(ParsedMessage.AssistantThinking(
                                timestamp = partTimestamp,
                                thinking = text
                            ))
                        }
                    }
                    "tool" -> {
                        partMessages.addAll(parseToolPart(part, partTimestamp))
                    }
                    "step-start", "step-finish" -> {
                        // Metadata parts
                    }
                }
            }

            return if (partMessages.isNotEmpty()) {
                partMessages
            } else {
                // Check if there's an error at the message level
                if (messageData.error != null) {
                    val errorContent = formatErrorContentCompanion(messageData.error, loaded.rawContent)
                    listOf(ParsedMessage.Info(
                        timestamp = timestamp,
                        title = "error",
                        subtitle = messageData.error.name,
                        content = MessageContent.Text(errorContent),
                        style = ParsedMessage.InfoStyle.ERROR
                    ))
                } else {
                    listOf(ParsedMessage.AssistantText(
                        timestamp = timestamp,
                        content = listOf(MessageContent.Code("Assistant message with 0 part(s)\n${loaded.rawContent}"))
                    ))
                }
            }
        }

        private fun formatErrorContentCompanion(error: OpenCodeMessageError?, rawContent: String): String {
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

                results.add(ParsedMessage.ToolResult(
                    timestamp = timestamp,
                    toolCallId = part.callID,
                    output = outputBlocks
                ))
            }

            // Return ToolUse with nested results directly (no need for post-processing)
            return listOf(ParsedMessage.ToolUse(
                timestamp = timestamp,
                toolName = toolName,
                toolCallId = part.callID,
                input = inputMap,
                results = results
            ))
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
    }

    /**
     * Finds all OpenCode sessions for the current project.
     * This includes both project-specific sessions and global sessions that match the project directory.
     * Uses 4 threads for parallel file processing.
     */
    fun findSessions(): List<SessionListItem> {
        val homeDir = System.getProperty("user.home")
        val projectPath = project.basePath ?: return emptyList()

        val storageDir = Paths.get(homeDir, ".local", "share", "opencode", "storage")
        if (!storageDir.exists()) {
            return emptyList()
        }

        val sessionDir = storageDir.resolve("session")
        val projectDir = storageDir.resolve("project")
        val messageDir = storageDir.resolve("message")

        if (!sessionDir.exists() || !projectDir.exists()) {
            return emptyList()
        }

        return try {
            // Find project-specific sessions
            val projectFiles = projectDir.listDirectoryEntries("*.json")
            val matchingProjectIds = projectFiles.mapNotNull { projectFile ->
                try {
                    val content = projectFile.readText()
                    val projectInfo = JSON.decodeFromString<OpenCodeProjectInfo>(content)
                    if (projectInfo.worktree == projectPath) projectInfo.id else null
                } catch (_: Exception) {
                    null
                }
            }

            // Collect all session files to process
            val sessionFiles = mutableListOf<Pair<JavaPath, Boolean>>() // Pair<file, isGlobal>

            // Add project-specific session files
            matchingProjectIds.forEach { projectId ->
                val projectSessionDir = sessionDir.resolve(projectId)
                if (projectSessionDir.exists()) {
                    projectSessionDir.listDirectoryEntries("*.json").forEach { sessionFile ->
                        sessionFiles.add(sessionFile to false)
                    }
                }
            }

            // Add global session files
            val globalSessionDir = sessionDir.resolve("global")
            if (globalSessionDir.exists()) {
                globalSessionDir.listDirectoryEntries("*.json").forEach { sessionFile ->
                    sessionFiles.add(sessionFile to true)
                }
            }

            if (sessionFiles.isEmpty()) return emptyList()

            // Process files in parallel with 4 threads
            val executor = Executors.newFixedThreadPool(4)
            try {
                val tasks = sessionFiles.map { (sessionFile, isGlobal) ->
                    Callable {
                        try {
                            val content = sessionFile.readText()
                            val session = JSON.decodeFromString<OpenCodeSessionData>(content)

                            // For global sessions, filter by directory match
                            if (isGlobal && session.directory != projectPath) {
                                return@Callable null
                            }

                            val msgCount = countMessages(messageDir, session.id)
                            SessionListItem(
                                sessionId = session.id,
                                title = session.title,
                                provider = SessionProvider.OPENCODE,
                                updated = session.time.updated,
                                created = session.time.created,
                                messageCount = if (msgCount > 0) msgCount else null
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }

                executor.invokeAll(tasks).mapNotNull { future ->
                    try {
                        future.get()
                    } catch (_: Exception) {
                        null
                    }
                }
            } finally {
                executor.shutdown()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Counts messages for a session by checking the message directory.
     */
    private fun countMessages(messageDir: JavaPath, sessionId: String): Int {
        val sessionMessageDir = messageDir.resolve(sessionId)
        if (!sessionMessageDir.exists()) {
            return 0
        }
        return try {
            sessionMessageDir.listDirectoryEntries("*.json").size
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Gets detailed session information for a specific session ID.
     * OpenCode stores session data hierarchically:
     * - Session metadata: storage/session/{projectId}/{sessionId}.json
     * - Messages: storage/message/{sessionId}/{messageId}.json
     * - Parts: storage/part/{messageId}/{partId}.json
     */
    fun getSessionDetail(sessionId: String): SessionDetail? {
        val sessionFile = getSessionFile(sessionId)
        if (sessionFile == null || !sessionFile.exists()) {
            return null
        }

        return try {
            val sessionContent = sessionFile.readText()
            val sessionData = JSON.decodeFromString<OpenCodeSessionData>(sessionContent)

            val homeDir = System.getProperty("user.home")
            val storageDir = Paths.get(homeDir, ".local", "share", "opencode", "storage")
            val messageDir = storageDir.resolve("message")

            // Load messages, parts, and collect metadata stats
            val parseResult = loadMessagesWithPartsAndStats(sessionData.id)

            // Count actual message files (same as list view) - not the expanded Message objects
            val messageFileCount = countMessages(messageDir, sessionData.id)

            // Sort models by usage count (descending)
            val sortedModels = parseResult.modelCounts.entries
                .sortedByDescending { it.value }
                .map { it.key to it.value }

            // Extract metadata with model and token info
            val metadata = SessionMetadata(
                cwd = sessionData.directory,
                created = formatTimestamp(sessionData.time.created),
                modified = formatTimestamp(sessionData.time.updated),
                messageCount = messageFileCount,
                models = sortedModels
            )

            SessionDetail(
                sessionId = sessionId,
                title = sessionData.title,
                messages = parseResult.messages,
                metadata = metadata
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets the file path for a specific session ID.
     * Searches through all project session directories.
     */
    fun getSessionFile(sessionId: String): JavaPath? {
        val homeDir = System.getProperty("user.home")
        val storageDir = Paths.get(homeDir, ".local", "share", "opencode", "storage")
        if (!storageDir.exists()) {
            return null
        }

        val sessionDir = storageDir.resolve("session")
        if (!sessionDir.exists()) {
            return null
        }

        // Search through all project directories for the session file
        return try {
            sessionDir.listDirectoryEntries().forEach { projectDir ->
                if (projectDir.isDirectory()) {
                    projectDir.listDirectoryEntries("*.json").forEach { sessionFile ->
                        try {
                            val content = sessionFile.readText()
                            val session = JSON.decodeFromString<OpenCodeSessionData>(content)
                            if (session.id == sessionId) {
                                return sessionFile
                            }
                        } catch (_: Exception) {
                            // Skip malformed session files
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Result of parsing messages with stats.
     */
    private data class MessageParseResult(
        val messages: List<ParsedMessage>,
        val modelCounts: Map<String, Int>
    )

    /**
     * Loads all messages for a session along with their parts and collects stats.
     * NEVER skips a message - if we can't parse it, show raw data.
     */
    private fun loadMessagesWithPartsAndStats(sessionId: String): MessageParseResult {
        val homeDir = System.getProperty("user.home")
        val storageDir = Paths.get(homeDir, ".local", "share", "opencode", "storage")
        val sessionDir = OpenCodeStorageLayout(storageDir, sessionId)

        val loadedMessages = loadRawMessages(sessionDir)

        if (loadedMessages.isEmpty()) {
            return MessageParseResult(emptyList(), emptyMap())
        }

        // Stats tracking
        val modelCounts = mutableMapOf<String, Int>()

        for (loaded in loadedMessages) {
            val messageData = loaded.messageData ?: continue

            // Track model usage from message
            val modelId = messageData.model?.modelID
            if (modelId != null) {
                modelCounts[modelId] = (modelCounts[modelId] ?: 0) + 1
            }
        }

        // Convert to ParsedMessage objects
        val messages = mutableListOf<ParsedMessage>()

        for (loaded in loadedMessages) {
            val messageData = loaded.messageData
            val parts = loaded.parts
            val timestamp = formatTimestamp(messageData?.time?.created)

            // If we couldn't parse the message, show raw data
            if (messageData == null) {
                messages.add(ParsedMessage.Info(
                    timestamp = timestamp,
                    title = "error",
                    subtitle = "parse",
                    content = MessageContent.Text("Failed to parse message file: ${loaded.filePath}\n${loaded.rawContent}"),
                    style = ParsedMessage.InfoStyle.ERROR
                ))
                continue
            }

            when (messageData.role) {
                "user" -> {
                    // Combine text parts for user message
                    val text = combineTextParts(parts)
                    val content = if (text.isNotEmpty()) {
                        listOf(MessageContent.Text(text))
                    } else if (parts.isEmpty()) {
                        // No parts at all - show raw message data
                        listOf(MessageContent.Code(loaded.rawContent))
                    } else {
                        // Has parts but no text - show parts summary
                        listOf(
                            MessageContent.Text("User message with ${parts.size} part(s), no text content"),
                            MessageContent.Code(loaded.rawContent)
                        )
                    }
                    messages.add(ParsedMessage.User(
                        timestamp = timestamp,
                        content = content
                    ))
                }
                "assistant" -> {
                    // Process parts in their natural timestamp order (already sorted in loadParts)
                    val partMessages = mutableListOf<ParsedMessage>()

                    for (part in parts) {
                        val partTimestamp = formatTimestamp(part.time?.start ?: part.time?.end)
                            .ifEmpty { timestamp }

                        when (part.type) {
                            "text" -> {
                                val text = part.text?.trim()
                                if (!text.isNullOrEmpty()) {
                                    partMessages.add(ParsedMessage.AssistantText(
                                        timestamp = partTimestamp,
                                        content = listOf(MessageContent.Markdown(text))
                                    ))
                                }
                            }
                            "reasoning" -> {
                                val text = part.text?.trim()
                                if (!text.isNullOrEmpty()) {
                                    partMessages.add(ParsedMessage.AssistantThinking(
                                        timestamp = partTimestamp,
                                        thinking = text
                                    ))
                                }
                            }
                            "tool" -> {
                                val toolName = part.tool ?: "tool"
                                val state = part.state

                                // Convert input to map
                                val inputMap = ToolInputFormatter.jsonToMap(state?.input)

                                // Build results list directly if the tool has completed or errored
                                val results = mutableListOf<ParsedMessage.ToolResult>()
                                val status = state?.status
                                if (status == "completed" || status == "error") {
                                    val outputElement = if (status == "error") state.error else state.output
                                    val outputBlocks = ToolOutputFormatter.formatToolOutput(outputElement)

                                    results.add(ParsedMessage.ToolResult(
                                        timestamp = partTimestamp,
                                        toolCallId = part.callID,
                                        output = outputBlocks
                                    ))
                                }

                                // Add ToolUse with nested results directly
                                partMessages.add(ParsedMessage.ToolUse(
                                    timestamp = partTimestamp,
                                    toolName = toolName,
                                    toolCallId = part.callID,
                                    input = inputMap,
                                    results = results
                                ))
                            }
                            else -> {
                                // Other part types (step-start, step-finish, etc.) - skip silently
                                // These are metadata parts, not displayable content
                            }
                        }
                    }

                    if (partMessages.isNotEmpty()) {
                        messages.addAll(partMessages)
                    } else {
                        // No parseable parts - check for error or show raw data
                        val errorContent = formatErrorContent(messageData.error, loaded.rawContent)
                        if (messageData.error != null) {
                            messages.add(ParsedMessage.Info(
                                timestamp = timestamp,
                                title = "error",
                                subtitle = messageData.error.name,
                                content = MessageContent.Text(errorContent),
                                style = ParsedMessage.InfoStyle.ERROR
                            ))
                        } else {
                            messages.add(ParsedMessage.AssistantText(
                                timestamp = timestamp,
                                content = listOf(MessageContent.Code(errorContent))
                            ))
                        }
                    }
                }
                else -> {
                    // Unknown role - show raw data
                    messages.add(ParsedMessage.Info(
                        timestamp = timestamp,
                        title = messageData.role,
                        content = MessageContent.Json(loaded.rawContent)
                    ))
                }
            }
        }

        return MessageParseResult(
            messages = messages,
            modelCounts = modelCounts
        )
    }

    /**
     * Combines text parts into a single string.
     */
    private fun combineTextParts(parts: List<OpenCodePartData>): String {
        return parts
            .filter { it.type == "text" && !it.text.isNullOrBlank() }
            .mapNotNull { it.text?.trim() }
            .joinToString("\n\n")
            .trim()
    }

    /**
     * Formats a timestamp from epoch millis to ISO string.
     */
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

    /**
     * Formats error content from a message-level error.
     */
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

}
