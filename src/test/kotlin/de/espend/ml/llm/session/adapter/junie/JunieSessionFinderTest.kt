package de.espend.ml.llm.session.adapter.junie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.deleteIfExists

class JunieSessionFinderTest {

    @Test
    fun `parseIndexLine should read explicit project directory`() {
        val session = JunieSessionFinder.parseIndexLine(
            """{"sessionId":"session-1","createdAt":1700000000000,"updatedAt":1700000100000,"projectDir":"/home/user/project","taskName":"Test task"}"""
        )

        assertEquals("session-1", session?.sessionId)
        assertEquals("/home/user/project", session?.projectDir)
        assertEquals("Test task", session?.taskName)
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
