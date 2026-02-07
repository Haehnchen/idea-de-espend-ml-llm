package de.espend.ml.llm.session.adapter.junie

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

class JunieSessionFinderTest {

    @Test
    fun `extractCwdFromFile should extract cd path from TerminalBlockUpdatedEvent`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","command":"cd /home/daniel/plugins/my-project","status":"IN_PROGRESS"}}}
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertEquals("/home/daniel/plugins/my-project", cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractCwdFromFile should exclude trailing whitespace from cd path`() {
        // The real command is "cd /path && ./gradlew test"
        // The regex should stop before the space before &&
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","command":"cd /home/daniel/plugins/symfony-plugin-3 && ./gradlew test"}}}
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertEquals("/home/daniel/plugins/symfony-plugin-3", cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractCwdFromFile should skip AgentStateUpdatedEvent lines`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentStateUpdatedEvent","blob":"{\"lastAgentState\":{...huge blob...}}"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","command":"cd /home/user/project"}}}
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertEquals("/home/user/project", cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractCwdFromFile should return null when no cd command found`() {
        val content = """
            {"kind":"UserPromptEvent","prompt":"Hello"}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"ResultBlockUpdatedEvent","result":"Done"}}}
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertNull(cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractCwdFromFile should return null for empty file`() {
        val file = createTempFile("")
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertNull(cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractCwdFromFile should return null when only AgentStateUpdatedEvent present`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentStateUpdatedEvent","blob":"{\"lastAgentState\":{...}}"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"AgentStateUpdatedEvent","blob":"{\"lastAgentState\":{...}}"}}}
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertNull(cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractCwdFromFile should find first cd command and stop`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","command":"cd /first/path"}}}
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","command":"cd /second/path"}}}
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertEquals("/first/path", cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractCwdFromFile should handle cd with semicolon chaining`() {
        val content = """
            {"kind":"SessionA2uxEvent","event":{"state":"IN_PROGRESS","agentEvent":{"kind":"TerminalBlockUpdatedEvent","command":"cd /home/user/project; ls -la"}}}
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val cwd = JunieSessionFinder.extractCwdFromFile(file)
            assertEquals("/home/user/project", cwd)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractProjectDirFromFile should extract project directory from worker file`() {
        val content = """
            ### PROJECT STRUCTURE
            Project root directory: /home/daniel/plugins/my-project
            Below are the files and directories in the project's root directory.
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val projectDir = JunieSessionFinder.extractProjectDirFromFile(file)
            assertEquals("/home/daniel/plugins/my-project", projectDir)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    @Test
    fun `extractProjectDirFromFile should return null when no project directory found`() {
        val content = """
            Some other content
            without project directory info
        """.trimIndent()

        val file = createTempFile(content)
        try {
            val projectDir = JunieSessionFinder.extractProjectDirFromFile(file)
            assertNull(projectDir)
        } finally {
            file.toPath().deleteIfExists()
        }
    }

    private fun createTempFile(content: String): File {
        val tempFile = Files.createTempFile("junie-test", ".jsonl").toFile()
        tempFile.writeText(content)
        return tempFile
    }
}
