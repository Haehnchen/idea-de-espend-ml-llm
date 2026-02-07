# Junie CLI Session Adapter Implementation Guide

## Overview

This document describes the findings from investigating the Junie CLI session storage format and provides a guide for implementing a Junie session adapter similar to the existing Claude, Codex, and OpenCode adapters.

## Junie CLI Session Storage Location

| Location | Path |
|----------|------|
| **Sessions directory** | `~/.junie/sessions/` |
| **Session index** | `~/.junie/sessions/index.jsonl` |
| **Session format** | `session-YYMMDD-HHMMSS-{random}/` |
| **Events file** | `events.jsonl` (JSONL format) |

### Directory Structure

```
~/.junie/
├── sessions/
│   ├── index.jsonl                              # Session index with metadata
│   ├── session-260206-154351-1p1g/              # Example session directory
│   │   ├── events.jsonl                         # Main event log (JSONL)
│   │   └── task-260206-154517-axev/             # Task subdirectory
│   │       └── .matterhorn/                     # Agent state data
│   ├── session-260206-155513-7js9/
│   │   └── events.jsonl
│   └── ...
├── history.jsonl                                # Global history
├── settings.json
└── logs/
```

## Session Index Format (`index.jsonl`)

The `index.jsonl` file contains one JSON object per line with session metadata:

```json
{"sessionId":"session-260206-154351-1p1g","createdAt":1770389031770,"updatedAt":1770389123237,"taskName":"Debug AMP Code Adapter Session and Project Filter","status":null}
```

**Fields:**
- `sessionId` - Unique session identifier
- `createdAt` - Unix timestamp (milliseconds) of session creation
- `updatedAt` - Unix timestamp (milliseconds) of last update
- `taskName` - The task/title of the session
- `status` - Session status (usually `null` for active sessions)

## Event Log Format (`events.jsonl`)

The `events.jsonl` file contains JSONL events with various types:

### Event Types Distribution

```
606 SessionA2uxEvent        # Agent/LLM events
  1 UserPromptEvent         # User prompt submission
  1 TaskState               # Task state changes
  1 SendToAgentEvent        # Send to agent trigger
```

### Event Examples

**UserPromptEvent:**
```json
{"kind":"UserPromptEvent","prompt":"T-019c299d-0a2e-777e-858b-6b44fb265cd6 i this is session and every project for from the amp code adatapter please check what is wring..."}
```

**SessionA2uxEvent (status update):**
```json
{"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentCurrentStatusUpdatedEvent","status":"Sending LLM request"}}}
```

**SessionA2uxEvent (LLM metadata):**
```json
{"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"LlmResponseMetadataEvent","modelUsage":[{"model":"claude-haiku-4-5-20251001","cost":0.002861,"inputTokens":2126,"cacheInputTokens":0,"cacheCreateTokens":0,"outputTokens":147,"time":0}]}}}
```

### SessionA2uxEvent Subtypes

| Subtype | Count | Description |
|---------|-------|-------------|
| `AgentCurrentStatusUpdatedEvent` | 370 | Status updates (e.g., "Sending LLM request") |
| `AgentStateUpdatedEvent` | 64 | Agent state changes |
| `LlmResponseMetadataEvent` | 62 | LLM token usage/cost metadata |
| `TerminalBlockUpdatedEvent` | 49 | Terminal output blocks |
| `ViewFilesBlockUpdatedEvent` | 30 | File viewing blocks |
| `ToolBlockUpdatedEvent` | 24 | Tool execution blocks |
| `FileChangesBlockUpdatedEvent` | 3 | File change notifications |
| `ResultBlockUpdatedEvent` | 2 | Result blocks |
| `AgentTaskNameUpdatedEvent` | 1 | Task name updates |
| `AgentPatchCreatedEvent` | 1 | Patch creation events |

## Implementation Guide

### Step 1: Create Package Structure

```
src/main/kotlin/de/espend/ml/llm/session/adapter/junie/
├── JunieSessionFinder.kt      # Standalone file finder
├── JunieSessionParser.kt      # Standalone JSONL parser
└── JunieSessionAdapter.kt     # IntelliJ integration layer
```

### Step 2: Implement `JunieSessionFinder.kt`

```kotlin
package de.espend.ml.llm.session.adapter.junie

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Represents a Junie session.
 */
data class JunieSession(
    val sessionId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val taskName: String?,
    val status: String?
)

/**
 * Standalone utility to find Junie session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object JunieSessionFinder {

    private const val JUNIE_DIR = ".junie"
    private const val SESSIONS_DIR = "sessions"
    private const val INDEX_FILE = "index.jsonl"

    /**
     * Gets the Junie sessions directory.
     */
    fun getJunieSessionsDir(): Path? {
        val homeDir = System.getProperty("user.home")
        val sessionsDir = Paths.get(homeDir, JUNIE_DIR, SESSIONS_DIR)
        return if (sessionsDir.exists()) sessionsDir else null
    }

    /**
     * Finds a session directory by ID.
     */
    fun findSessionDir(sessionId: String): Path? {
        val sessionsDir = getJunieSessionsDir() ?: return null
        val sessionDir = sessionsDir.resolve(sessionId)
        return if (sessionDir.exists()) sessionDir else null
    }

    /**
     * Finds the events.jsonl file for a session.
     */
    fun findSessionEventsFile(sessionId: String): Path? {
        val sessionDir = findSessionDir(sessionId) ?: return null
        val eventsFile = sessionDir.resolve("events.jsonl")
        return if (eventsFile.exists()) eventsFile else null
    }

    /**
     * Lists all sessions from the index.jsonl file.
     */
    fun listSessions(): List<JunieSession> {
        val sessionsDir = getJunieSessionsDir() ?: return emptyList()
        val indexFile = sessionsDir.resolve(INDEX_FILE)
        if (!indexFile.exists()) return emptyList()

        return try {
            indexFile.toFile().readLines()
                .mapNotNull { line -> parseIndexLine(line) }
                .sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses a single line from index.jsonl.
     */
    private fun parseIndexLine(line: String): JunieSession? {
        // Parse JSON: {"sessionId":"...","createdAt":...,"updatedAt":...,"taskName":"...","status":null}
        val sessionId = extractJsonField(line, "sessionId") ?: return null
        val createdAt = extractJsonField(line, "createdAt")?.toLongOrNull() ?: 0L
        val updatedAt = extractJsonField(line, "updatedAt")?.toLongOrNull() ?: 0L
        val taskName = extractJsonField(line, "taskName")
        val status = extractJsonField(line, "status")

        return JunieSession(
            sessionId = sessionId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            taskName = taskName,
            status = status
        )
    }

    /**
     * Simple JSON field extraction (avoids kotlinx.serialization dependency for finder).
     */
    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = """"$fieldName"\s*:\s*"([^"]*)"""".toRegex()
        val match = pattern.find(json)
        if (match != null) return match.groupValues[1]

        // Try numeric/null value
        val numPattern = """"$fieldName"\s*:\s*([^,}\s]*)""".toRegex()
        val numMatch = numPattern.find(json)
        return numMatch?.groupValues?.get(1)?.takeIf { it != "null" }
    }
}
```

### Step 3: Implement `JunieSessionParser.kt`

```kotlin
package de.espend.ml.llm.session.adapter.junie

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionMetadata
import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path

/**
 * Standalone parser for Junie session files.
 * No IntelliJ dependencies - can be used from CLI or tests.
 */
object JunieSessionParser {

    private val JSON = Json { ignoreUnknownKeys = true }

    /**
     * Parses a Junie session by ID and returns SessionDetail.
     */
    fun parseSession(sessionId: String): SessionDetail? {
        val eventsFile = JunieSessionFinder.findSessionEventsFile(sessionId) ?: return null
        return parseFile(eventsFile.toFile(), sessionId)
    }

    /**
     * Parses a Junie events.jsonl file.
     */
    fun parseFile(file: java.io.File, sessionId: String): SessionDetail? {
        if (!file.exists()) return null

        return try {
            val content = file.readText()
            val (messages, metadata) = parseContent(content)

            // Get task name from index for title
            val title = getSessionTitle(sessionId)

            SessionDetail(
                sessionId = sessionId,
                title = title,
                messages = messages,
                metadata = metadata
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gets the session title from index.jsonl.
     */
    private fun getSessionTitle(sessionId: String): String {
        val sessions = JunieSessionFinder.listSessions()
        return sessions.find { it.sessionId == sessionId }?.taskName ?: "Untitled"
    }

    /**
     * Parses Junie events.jsonl content.
     */
    fun parseContent(content: String): Pair<List<ParsedMessage>, SessionMetadata?> {
        val messages = mutableListOf<ParsedMessage>()
        val lines = content.lines()

        var created: String? = null
        var modified: String? = null
        val modelCounts = mutableMapOf<String, Int>()
        var messageCount = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("{")) continue

            try {
                val json = JSON.parseToJsonElement(trimmed).jsonObject
                val kind = json["kind"]?.jsonPrimitive?.content

                when (kind) {
                    "UserPromptEvent" -> {
                        val prompt = json["prompt"]?.jsonPrimitive?.content
                        if (prompt != null) {
                            messages.add(ParsedMessage.User(
                                timestamp = getCurrentTimestamp(),
                                content = listOf(MessageContent.Text(prompt))
                            ))
                            messageCount++
                        }
                    }
                    "SessionA2uxEvent" -> {
                        parseSessionA2uxEvent(json, messages, modelCounts)
                        messageCount++
                    }
                }
            } catch (_: Exception) {
                // Skip unparseable lines
            }
        }

        val metadata = SessionMetadata(
            created = created,
            modified = modified,
            messageCount = messageCount,
            models = modelCounts.entries.sortedByDescending { it.value }.map { it.key to it.value }
        )

        return Pair(messages, metadata)
    }

    /**
     * Parses SessionA2uxEvent messages.
     */
    private fun parseSessionA2uxEvent(
        json: kotlinx.serialization.json.JsonObject,
        messages: MutableList<ParsedMessage>,
        modelCounts: MutableMap<String, Int>
    ) {
        val event = json["event"]?.jsonObject ?: return
        val agentEvent = event["agentEvent"]?.jsonObject ?: return
        val agentEventKind = agentEvent["kind"]?.jsonPrimitive?.content

        when (agentEventKind) {
            "AgentCurrentStatusUpdatedEvent" -> {
                val status = agentEvent["status"]?.jsonPrimitive?.content
                if (status != null) {
                    messages.add(ParsedMessage.Info(
                        timestamp = getCurrentTimestamp(),
                        title = "status",
                        content = MessageContent.Text(status)
                    ))
                }
            }
            "LlmResponseMetadataEvent" -> {
                val modelUsage = agentEvent["modelUsage"]?.jsonArray
                modelUsage?.forEach { usage ->
                    val model = usage.jsonObject["model"]?.jsonPrimitive?.content
                    if (model != null) {
                        modelCounts[model] = (modelCounts[model] ?: 0) + 1
                    }
                }
            }
            "ToolBlockUpdatedEvent", "TerminalBlockUpdatedEvent", "ViewFilesBlockUpdatedEvent" -> {
                // Parse tool/terminal/file view events as info messages
                messages.add(ParsedMessage.Info(
                    timestamp = getCurrentTimestamp(),
                    title = agentEventKind.removeSuffix("Event")
                ))
            }
        }
    }

    private fun getCurrentTimestamp(): String {
        return java.time.Instant.now().toString()
    }
}
```

### Step 4: Implement `JunieSessionAdapter.kt`

```kotlin
package de.espend.ml.llm.session.adapter

import com.intellij.openapi.project.Project
import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.SessionListItem
import de.espend.ml.llm.session.SessionProvider
import de.espend.ml.llm.session.adapter.junie.JunieSessionFinder
import de.espend.ml.llm.session.adapter.junie.JunieSessionParser

/**
 * Adapter for reading and parsing Junie session files.
 * Uses JunieSessionParser and JunieSessionFinder for the actual work.
 * This class adds IntelliJ Project integration.
 */
class JunieSessionAdapter(private val project: Project) {

    /**
     * Finds all Junie sessions.
     * Returns all sessions (Junie doesn't store project-specific sessions).
     */
    fun findSessions(): List<SessionListItem> {
        return try {
            val sessions = JunieSessionFinder.listSessions()
            if (sessions.isEmpty()) return emptyList()

            sessions.map { session ->
                SessionListItem(
                    sessionId = session.sessionId,
                    title = session.taskName ?: "Untitled",
                    provider = SessionProvider.JUNIE,
                    updated = session.updatedAt.toString(),
                    created = session.createdAt.toString(),
                    messageCount = 0  // Would need to parse events to count
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Gets detailed session information for a specific session ID.
     */
    fun getSessionDetail(sessionId: String): SessionDetail? {
        return JunieSessionParser.parseSession(sessionId)
    }
}
```

### Step 5: Update `SessionProvider` enum

Add `JUNIE` to the `SessionProvider` enum in `SessionListItem.kt`:

```kotlin
enum class SessionProvider(val displayName: String) {
    CLAUDE("Claude Code"),
    OPENCODE("OpenCode"),
    CODEX("JetBrains AI"),
    AMP("JetBrains AI (AMP)"),
    JUNIE("Junie CLI")
}
```

### Step 6: Update `SessionService.kt`

Add Junie adapter to the service:

```kotlin
@Service(Service.Level.PROJECT)
class SessionService(private val Project: Project) {

    private val claudeAdapter = ClaudeSessionAdapter(project)
    private val openCodeAdapter = OpenCodeSessionAdapter(project)
    private val codexAdapter = CodexSessionAdapter(project)
    private val ampAdapter = AmpSessionAdapter(project)
    private val junieAdapter = JunieSessionAdapter(project)  // Add this

    companion object {
        fun getInstance(project: Project): SessionService = project.service()
    }

    fun getAllSessions(): List<SessionListItem> {
        val executor = Executors.newFixedThreadPool(5)  // Update to 5 threads
        return try {
            val tasks = listOf(
                Callable { claudeAdapter.findSessions() },
                Callable { openCodeAdapter.findSessions() },
                Callable { codexAdapter.findSessions() },
                Callable { ampAdapter.findSessions() },
                Callable { junieAdapter.findSessions() }  // Add this
            )
            // ... rest of the code
        }
    }
}
```

### Step 7: Update CLI tool

Add Junie to `SessionHtmlDumperCli.kt`:

```kotlin
// In findAndParseSession() function, add Junie case
val junieSession = JunieSessionParser.parseSession(sessionId)
if (junieSession != null) {
    return junieSession
}
```

## Key Differences from Other Adapters

| Feature | Claude | Codex | OpenCode | Junie |
|---------|--------|-------|----------|-------|
| **Location** | `~/.claude/projects/*/` | `~/.cache/JetBrains/*/` | `~/.opencode/` | `~/.junie/sessions/` |
| **File format** | JSONL per session | JSONL per session | JSON per session | JSONL per session |
| **Session index** | Scan directories | Scan directories | `conversations.json` | `index.jsonl` |
| **Project filtering** | By project directory | By IDE cache | N/A | N/A (no project filter) |
| **Event types** | `user`, `assistant`, `tool_use` | `chatMessage` | `user`, `assistant` | `UserPromptEvent`, `SessionA2uxEvent` |

## Testing

Create test files in `src/test/kotlin/de/espend/ml/llm/session/adapter/junie/`:

- `JunieSessionFinderTest.kt`
- `JunieSessionParserTest.kt`

## References

- **Existing adapters**: `src/main/kotlin/de/espend/ml/llm/session/adapter/`
- **Session models**: `src/main/kotlin/de/espend/ml/llm/session/model/MessageModels.kt`
- **Session service**: `src/main/kotlin/de/espend/ml/llm/session/SessionService.kt`
