package de.espend.ml.llm.session.adapter.claude

import org.junit.Assert.*
import org.junit.Test

class ClaudeSessionFinderTest {

    @Test
    fun `projectPathToClaudeDir should convert absolute path to Claude format`() {
        val result = ClaudeSessionFinder.projectPathToClaudeDir("/home/user/project")
        assertEquals("Should convert slashes to dashes and keep leading dash", "-home-user-project", result)
    }

    @Test
    fun `projectPathToClaudeDir should handle nested paths`() {
        val result = ClaudeSessionFinder.projectPathToClaudeDir("/home/user/my-projects/some-project")
        assertEquals("Should preserve existing dashes in path", "-home-user-my-projects-some-project", result)
    }

    @Test
    fun `projectPathToClaudeDir should handle Windows paths with colons`() {
        val result = ClaudeSessionFinder.projectPathToClaudeDir("C:/Users/user/project")
        assertEquals("Should remove colon from Windows drive letter", "C-Users-user-project", result)
    }

    @Test
    fun `projectPathToClaudeDir should handle real-world example`() {
        val result = ClaudeSessionFinder.projectPathToClaudeDir("/home/daniel/my-projects/idea-de-espend-ml-llm")
        assertEquals("Should match actual Claude directory naming", "-home-daniel-my-projects-idea-de-espend-ml-llm", result)
    }

    @Test
    fun `projectPathToClaudeDir should not remove leading dash`() {
        // Regression test for the bug where removePrefix("-") was incorrectly used
        val result = ClaudeSessionFinder.projectPathToClaudeDir("/home/user/project")
        assertTrue("Should start with a dash", result.startsWith("-"))
    }

    @Test
    fun `getClaudeProjectsDir should return correct path`() {
        val result = ClaudeSessionFinder.getClaudeProjectsDir()
        val homeDir = System.getProperty("user.home")
        assertEquals("Should be under home directory/.claude/projects", "$homeDir/.claude/projects", result.absolutePath)
    }
}
