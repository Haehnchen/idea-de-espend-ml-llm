package de.espend.ml.llm.session.adapter.amp

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class AmpSessionFinderTest {

    private val fixturesDir = Paths.get("src/test/kotlin/de/espend/ml/llm/session/adapter/fixtures/amp")

    @Test
    fun `AmpSession data class should hold values correctly`() {
        val session = AmpSession(
            sessionId = "T-test-1234",
            created = 1234567890L,
            messageCount = 5,
            firstPrompt = "Test prompt",
            cwd = "/home/test"
        )

        assertEquals("T-test-1234", session.sessionId)
        assertEquals(1234567890L, session.created)
        assertEquals(5, session.messageCount)
        assertEquals("Test prompt", session.firstPrompt)
        assertEquals("/home/test", session.cwd)
    }

    @Test
    fun `listSessions from fixtures should parse sessions`() {
        val result = AmpSessionFinder.listSessions(fixturesDir)

        assertNotNull("Should return a list", result)
        assertTrue("Should have at least one session", result.isNotEmpty())

        val session = result.find { it.sessionId == "T-019c2505-6bed-73de-8e27-51899656b47b" }
        assertNotNull("Session should be found", session)
        assertEquals("Should have 3 messages", 3, session?.messageCount)
        assertEquals("/home/user/project", session?.cwd)
        assertTrue("firstPrompt should contain the user message", session?.firstPrompt?.contains("theme") == true)
    }

    @Test
    fun `listSessions should parse multiple fixtures`() {
        val result = AmpSessionFinder.listSessions(fixturesDir)

        // Session ID should come from filename, not JSON id field
        val session = result.find { it.sessionId == "T-array-format-test" }
        assertNotNull("Second fixture should be found", session)
        assertEquals("Should have 5 messages", 5, session?.messageCount)
        assertEquals("/home/user/project", session?.cwd)
        assertTrue("firstPrompt should contain user message", session?.firstPrompt?.contains("edit") == true)
    }

    @Test
    fun `session ID should come from filename not JSON id field`() {
        val result = AmpSessionFinder.listSessions(fixturesDir)

        // The array format fixture has id="T-different-id-in-json" in JSON
        // but filename is "T-array-format-test.json"
        val sessionByFilename = result.find { it.sessionId == "T-array-format-test" }
        val sessionByJsonId = result.find { it.sessionId == "T-different-id-in-json" }

        assertNotNull("Session should be found by filename", sessionByFilename)
        assertNull("Session should NOT be found by JSON id field", sessionByJsonId)
    }

    @Test
    fun `listSessions should extract cwd from env initial trees uri`() {
        val tempDir = createTempDirectory()
        try {
            // Create a test session file with env.initial.trees
            val sessionJson = """
            {
              "v": 2514,
              "id": "T-test-env-cwd",
              "created": 1770147638256,
              "messages": [
                {
                  "role": "user",
                  "messageId": 0,
                  "content": [{"type": "text", "text": "Test prompt"}]
                }
              ],
              "env": {
                "initial": {
                  "trees": [
                    {
                      "displayName": "my-project",
                      "uri": "file:///home/daniel/projects/my-project",
                      "repository": {
                        "type": "git",
                        "url": "https://github.com/test/test",
                        "ref": "refs/heads/main",
                        "sha": "abc123"
                      }
                    }
                  ],
                  "platform": {
                    "os": "linux",
                    "osVersion": "Ubuntu 25.10",
                    "cpuArchitecture": "x64",
                    "webBrowser": false,
                    "client": "JetBrains",
                    "clientVersion": "0.0.1",
                    "clientType": "cli"
                  }
                }
              }
            }
            """.trimIndent()

            val sessionFile = tempDir.resolve("T-test-env-cwd.json")
            sessionFile.writeText(sessionJson)

            val result = AmpSessionFinder.listSessions(tempDir)
            val session = result.find { it.sessionId == "T-test-env-cwd" }

            assertNotNull("Session should be found", session)
            assertEquals("/home/daniel/projects/my-project", session?.cwd)
            assertEquals("Test prompt", session?.firstPrompt)
            assertEquals(1, session?.messageCount)

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `listSessions should count messages correctly`() {
        val tempDir = createTempDirectory()
        try {
            val sessionJson = """
            {
              "v": 2514,
              "id": "T-test-session",
              "created": 1770147638256,
              "messages": [
                {"role": "user", "messageId": 0, "content": [{"type": "text", "text": "First"}]},
                {"role": "assistant", "messageId": 1, "content": [{"type": "text", "text": "Response"}]},
                {"role": "user", "messageId": 2, "content": [{"type": "text", "text": "Second"}]}
              ]
            }
            """.trimIndent()

            val sessionFile = tempDir.resolve("T-test-session.json")
            sessionFile.writeText(sessionJson)

            val result = AmpSessionFinder.listSessions(tempDir)
            val session = result.find { it.sessionId == "T-test-session" }

            assertNotNull("Session should be found", session)
            assertEquals("Should count 3 messages", 3, session?.messageCount)
            assertEquals("First", session?.firstPrompt)

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
