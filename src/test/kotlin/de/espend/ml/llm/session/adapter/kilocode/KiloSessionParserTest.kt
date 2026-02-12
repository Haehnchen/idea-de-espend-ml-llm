package de.espend.ml.llm.session.adapter.kilocode

import de.espend.ml.llm.session.model.MessageContent
import de.espend.ml.llm.session.model.ParsedMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Paths

class KiloSessionParserTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures/kilocode")
    private val JSON = Json { ignoreUnknownKeys = true }

    @Test
    fun `parseContent should parse user text message`() {
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000000, "type": "say", "say": "text", "text": "Hello, how can I help?"}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())
        val metadata = JsonObject(emptyMap())

        val (messages, _) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(1, messages.size)
        assertTrue(messages[0] is ParsedMessage.User)
        val user = messages[0] as ParsedMessage.User
        assertTrue(user.content[0] is MessageContent.Text)
        assertEquals("Hello, how can I help?", (user.content[0] as MessageContent.Text).text)
    }

    @Test
    fun `parseContent should parse reasoning message`() {
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000100, "type": "say", "say": "reasoning", "text": "Let me think about this..."}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())
        val metadata = JsonObject(emptyMap())

        val (messages, _) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(1, messages.size)
        assertTrue(messages[0] is ParsedMessage.AssistantThinking)
        assertEquals("Let me think about this...", (messages[0] as ParsedMessage.AssistantThinking).thinking)
    }

    @Test
    fun `parseContent should parse tool use message`() {
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000400, "type": "ask", "ask": "tool", "text": "{\"tool\": \"readFile\", \"path\": \"/test/file.txt\"}"}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())
        val metadata = JsonObject(emptyMap())

        val (messages, _) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(1, messages.size)
        assertTrue(messages[0] is ParsedMessage.ToolUse)
        val toolUse = messages[0] as ParsedMessage.ToolUse
        assertEquals("readFile", toolUse.toolName)
        assertEquals("/test/file.txt", toolUse.input["path"])
    }

    @Test
    fun `parseContent should parse followup message`() {
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000600, "type": "ask", "ask": "followup", "text": "Would you like me to continue?"}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())
        val metadata = JsonObject(emptyMap())

        val (messages, _) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(1, messages.size)
        assertTrue(messages[0] is ParsedMessage.Info)
        assertEquals("followup", (messages[0] as ParsedMessage.Info).title)
    }

    @Test
    fun `parseContent should parse error message`() {
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000700, "type": "say", "say": "error", "text": "Something went wrong"}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())
        val metadata = JsonObject(emptyMap())

        val (messages, _) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(1, messages.size)
        assertTrue(messages[0] is ParsedMessage.Info)
        val info = messages[0] as ParsedMessage.Info
        assertEquals("error", info.title)
        assertEquals(ParsedMessage.InfoStyle.ERROR, info.style)
    }

    @Test
    fun `extractTitle should extract title from first user message`() {
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000000, "type": "say", "say": "text", "text": "This is my question"},
            {"ts": 1700000000100, "type": "say", "say": "reasoning", "text": "Thinking..."}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())

        val title = KiloSessionParser.extractTitle(uiMessages, apiHistory)

        assertEquals("This is my question", title)
    }

    @Test
    fun `extractTitle should truncate long titles`() {
        val longText = "a".repeat(150)
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000000, "type": "say", "say": "text", "text": "$longText"}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())

        val title = KiloSessionParser.extractTitle(uiMessages, apiHistory)

        assertEquals("a".repeat(100) + "...", title)
    }

    @Test
    fun `extractTitle should extract title from API history if no UI messages`() {
        val uiMessages = JsonArray(emptyList())
        val apiHistory = JSON.parseToJsonElement("""[
            {"role": "user", "content": [{"type": "text", "text": "API question"}]}
        ]""").jsonArray

        val title = KiloSessionParser.extractTitle(uiMessages, apiHistory)

        assertEquals("API question", title)
    }

    @Test
    fun `parseContent should extract workspace from metadata cwd`() {
        val uiMessages = JsonArray(emptyList())
        val apiHistory = JsonArray(emptyList())
        val metadata = JSON.parseToJsonElement("""{"cwd": "/home/user/myproject"}""").jsonObject

        val (_, sessionMetadata) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals("/home/user/myproject", sessionMetadata.cwd)
    }

    @Test
    fun `parseContent should extract workspace from files_in_context`() {
        val uiMessages = JsonArray(emptyList())
        val apiHistory = JsonArray(emptyList())
        val metadata = JSON.parseToJsonElement("""{"files_in_context": [{"path": "/home/user/myproject/src/file.ts"}]}""").jsonObject

        val (_, sessionMetadata) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals("/home/user/myproject/src", sessionMetadata.cwd)
    }

    @Test
    fun `parseContent should track timestamps`() {
        val uiMessages = JSON.parseToJsonElement("""[
            {"ts": 1700000000000, "type": "say", "say": "text", "text": "First"},
            {"ts": 1700000100000, "type": "say", "say": "text", "text": "Last"}
        ]""").jsonArray
        val apiHistory = JsonArray(emptyList())
        val metadata = JsonObject(emptyMap())

        val (_, sessionMetadata) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(java.time.Instant.ofEpochMilli(1700000000000).toString(), sessionMetadata.created)
        assertEquals(java.time.Instant.ofEpochMilli(1700000100000).toString(), sessionMetadata.modified)
    }

    @Test
    fun `parseContent should extract model from API history`() {
        val uiMessages = JsonArray(emptyList())
        val apiHistory = JSON.parseToJsonElement("""[
            {"role": "user", "content": [{"type": "text", "text": "<environment_details>\n<model>minimax/minimax-m2.1:free</model>\n</environment_details>"}]}
        ]""").jsonArray
        val metadata = JsonObject(emptyMap())

        val (_, sessionMetadata) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(1, sessionMetadata.models.size)
        assertEquals("minimax/minimax-m2.1:free", sessionMetadata.models[0].first)
        assertEquals(1, sessionMetadata.models[0].second)
    }

    @Test
    fun `parseContent should handle API history without model tag`() {
        val uiMessages = JsonArray(emptyList())
        val apiHistory = JSON.parseToJsonElement("""[
            {"role": "user", "content": [{"type": "text", "text": "<environment_details>\n</environment_details>"}]}
        ]""").jsonArray
        val metadata = JsonObject(emptyMap())

        val (_, sessionMetadata) = KiloSessionParser.parseContent(uiMessages, apiHistory, metadata, "/test/task")

        assertEquals(0, sessionMetadata.models.size)
    }

    @Test
    fun `parseSession should return null for non-existent task path`() {
        val result = KiloSessionParser.parseSession("/nonexistent/path", "test-session")
        assertNull(result)
    }

    @Test
    fun `parseSession should parse complete session from files`() {
        val taskDir = File.createTempFile("kilo-test-task-", "").also {
            it.delete()
            it.mkdirs()
        }

        try {
            File(taskDir, "ui_messages.json").writeText("""[
                {"ts": 1700000000000, "type": "say", "say": "text", "text": "Test question"},
                {"ts": 1700000000100, "type": "say", "say": "reasoning", "text": "Let me analyze"}
            ]""")

            File(taskDir, "api_conversation_history.json").writeText("""[
                {"role": "user", "content": [{"type": "text", "text": "Test question"}]}
            ]""")

            File(taskDir, "task_metadata.json").writeText("""{"cwd": "/home/user/project"}""")

            val session = KiloSessionParser.parseSession(taskDir.absolutePath, "test-session-123")

            assertNotNull(session)
            assertEquals("test-session-123", session!!.sessionId)
            assertEquals("Test question", session.title)
            assertEquals(2, session.messages.size)
            assertEquals("/home/user/project", session.metadata?.cwd)
        } finally {
            taskDir.deleteRecursively()
        }
    }

    @Test
    fun `should parse fixture files`() {
        val uiMessagesPath = fixturesDir.resolve("ui_messages.json")
        if (!uiMessagesPath.toFile().exists()) return

        val uiMessages = JSON.parseToJsonElement(uiMessagesPath.toFile().readText()).jsonArray
        val apiHistory = JsonArray(emptyList())
        val metadataPath = fixturesDir.resolve("task_metadata.json")
        val metadata = if (metadataPath.toFile().exists()) {
            JSON.parseToJsonElement(metadataPath.toFile().readText()).jsonObject
        } else {
            JsonObject(emptyMap())
        }

        val (messages, sessionMetadata) = KiloSessionParser.parseContent(
            uiMessages, apiHistory, metadata, "/test/task"
        )

        assertTrue(messages.isNotEmpty())
        assertNotNull(sessionMetadata)
    }
}
