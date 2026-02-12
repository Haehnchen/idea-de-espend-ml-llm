@file:JvmName("SessionHtmlDumper")

package de.espend.ml.llm.session.cli

import de.espend.ml.llm.session.SessionDetail
import de.espend.ml.llm.session.adapter.amp.AmpSessionFinder
import de.espend.ml.llm.session.adapter.amp.AmpSessionParser
import de.espend.ml.llm.session.adapter.claude.ClaudeSessionFinder
import de.espend.ml.llm.session.adapter.claude.ClaudeSessionParser
import de.espend.ml.llm.session.adapter.codex.CodexSessionFinder
import de.espend.ml.llm.session.adapter.codex.CodexSessionParser
import de.espend.ml.llm.session.adapter.droid.DroidSessionFinder
import de.espend.ml.llm.session.adapter.droid.DroidSessionParser
import de.espend.ml.llm.session.adapter.gemini.GeminiSessionFinder
import de.espend.ml.llm.session.adapter.gemini.GeminiSessionParser
import de.espend.ml.llm.session.adapter.junie.JunieSessionFinder
import de.espend.ml.llm.session.adapter.junie.JunieSessionParser
import de.espend.ml.llm.session.adapter.kilocode.KiloSessionFinder
import de.espend.ml.llm.session.adapter.kilocode.KiloSessionParser
import de.espend.ml.llm.session.adapter.opencode.OpenCodeSessionFinder
import de.espend.ml.llm.session.adapter.opencode.OpenCodeSessionParser
import de.espend.ml.llm.session.view.SessionDetailView
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Standalone CLI tool to dump session HTML.
 * Reuses the same SessionDetailView as the IDE plugin.
 *
 * Usage:
 *   ./gradlew dumpSession --args="--id=SESSION_ID"
 *   ./gradlew dumpSession --args="--list"
 *   ./gradlew dumpSession --args="--help"
 */
fun main(args: Array<String>) {
    val params = parseArgs(args)

    when {
        params["help"] == "true" || args.isEmpty() -> printUsage()
        params["list"] == "true" -> listSessions()
        else -> dumpSession(params)
    }
}

private fun dumpSession(params: Map<String, String>) {
    val sessionId = params["id"]
    val outputPath = params["output"]

    if (sessionId == null) {
        println("Error: --id is required")
        printUsage()
        return
    }

    // Try to find session in both providers
    val result = findAndParseSession(sessionId)

    if (result == null) {
        println("Error: Session file not found for id=$sessionId")
        return
    }

    val (provider, sessionDetail, fileLocation) = result
    println("Found session file ($provider): $fileLocation")

    // Use existing SessionDetailView (standalone mode)
    val detailView = SessionDetailView()
    val html = detailView.generateSessionDetail(sessionId, sessionDetail)

    val outputFile = if (outputPath != null) {
        File(outputPath)
    } else {
        File("session-$provider-${sessionId.take(8)}.html")
    }

    outputFile.writeText(html)
    println("✓ HTML dumped to: ${outputFile.absolutePath}")
}

/**
 * Tries to find and parse a session by ID across all providers.
 */
private fun findAndParseSession(sessionId: String): Triple<String, SessionDetail, String>? {
    // Try Claude first
    val claudeFile = ClaudeSessionFinder.findSessionFile(sessionId)
    if (claudeFile != null) {
        val detail = ClaudeSessionParser.parseFile(claudeFile)
        if (detail != null) {
            return Triple("claude", detail, claudeFile.absolutePath)
        }
    }

    // Try OpenCode
    val openCodeFile = OpenCodeSessionFinder.findSessionFile(sessionId)
    if (openCodeFile != null) {
        val detail = OpenCodeSessionParser.parseSession(sessionId)
        if (detail != null) {
            return Triple("opencode", detail, openCodeFile.toString())
        }
    }

    // Try Codex
    val codexFile = CodexSessionFinder.findSessionFile(sessionId)
    if (codexFile != null) {
        val detail = CodexSessionParser.parseFile(codexFile)
        if (detail != null) {
            return Triple("codex", detail, codexFile.absolutePath)
        }
    }

    // Try Amp
    val ampFile = AmpSessionFinder.findSessionFile(sessionId)
    if (ampFile != null) {
        val detail = AmpSessionParser.parseFile(ampFile)
        if (detail != null) {
            return Triple("amp", detail, ampFile.toString())
        }
    }

    // Try Junie
    val junieFile = JunieSessionFinder.findSessionFile(sessionId)
    if (junieFile != null) {
        val detail = JunieSessionParser.parseFile(junieFile, sessionId)
        if (detail != null) {
            return Triple("junie", detail, junieFile.toString())
        }
    }

    // Try Droid
    val droidFile = DroidSessionFinder.findSessionFile(sessionId)
    if (droidFile != null) {
        val detail = DroidSessionParser.parseFile(droidFile)
        if (detail != null) {
            return Triple("droid", detail, droidFile.absolutePath)
        }
    }

    // Try Gemini
    val geminiFile = GeminiSessionFinder.findSessionFile(sessionId)
    if (geminiFile != null) {
        val detail = GeminiSessionParser.parseFile(geminiFile)
        if (detail != null) {
            return Triple("gemini", detail, geminiFile.absolutePath)
        }
    }

    // Try Kilo Code
    val kiloFile = KiloSessionFinder.findSessionFile(sessionId)
    if (kiloFile != null) {
        val detail = KiloSessionParser.parseSession(kiloFile.absolutePath, sessionId)
        if (detail != null) {
            return Triple("kilocode", detail, kiloFile.absolutePath)
        }
    }

    return null
}

private fun listSessions() {
    println("\n=== Available Sessions ===\n")

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // List Claude sessions
    println("Claude Sessions:")
    val claudeFiles = ClaudeSessionFinder.listSessionFiles()
    if (claudeFiles.isEmpty()) {
        println("  No sessions found")
    } else {
        claudeFiles.sortedByDescending { it.lastModified() }.take(50).forEach { file ->
            val modified = Instant.ofEpochMilli(file.lastModified())
                .atZone(ZoneId.systemDefault())
                .format(formatter)
            println("  [$modified] ${file.nameWithoutExtension}")
        }
    }

    println()

    // List OpenCode sessions
    println("OpenCode Sessions:")
    val openCodeSessions = OpenCodeSessionFinder.listSessions()
    if (openCodeSessions.isEmpty()) {
        println("  No sessions found")
    } else {
        openCodeSessions.take(50).forEach { session ->
            val created = Instant.ofEpochMilli(session.created)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
            println("  [$created] ${session.sessionId} - ${session.title.take(60)}")
        }
        if (openCodeSessions.size > 50) {
            println("  ... and ${openCodeSessions.size - 50} more")
        }
    }

    println()

    // List Codex sessions
    println("Codex Sessions:")
    val codexFiles = CodexSessionFinder.listSessionFiles()
    if (codexFiles.isEmpty()) {
        println("  No sessions found")
    } else {
        codexFiles.take(50).forEach { file ->
            val sessionId = CodexSessionFinder.extractSessionId(file) ?: file.nameWithoutExtension
            val modified = Instant.ofEpochMilli(file.lastModified())
                .atZone(ZoneId.systemDefault())
                .format(formatter)
            println("  [$modified] $sessionId")
        }
        if (codexFiles.size > 50) {
            println("  ... and ${codexFiles.size - 50} more")
        }
    }

    println()

    // List Amp sessions
    println("Amp Sessions:")
    val ampSessions = AmpSessionFinder.listSessions()
    if (ampSessions.isEmpty()) {
        println("  No sessions found")
    } else {
        ampSessions.take(50).forEach { session ->
            val created = Instant.ofEpochMilli(session.created)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
            val title = session.firstPrompt?.take(60) ?: "Untitled"
            println("  [$created] ${session.sessionId} - $title")
        }
        if (ampSessions.size > 50) {
            println("  ... and ${ampSessions.size - 50} more")
        }
    }

    println()

    // List Junie sessions
    println("Junie Sessions:")
    val junieSessions = JunieSessionFinder.listSessions()
    if (junieSessions.isEmpty()) {
        println("  No sessions found")
    } else {
        junieSessions.take(50).forEach { session ->
            val updated = Instant.ofEpochMilli(session.updatedAt)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
            val title = session.taskName ?: "Untitled"
            println("  [$updated] ${session.sessionId} - ${title.take(60)}")
        }
        if (junieSessions.size > 50) {
            println("  ... and ${junieSessions.size - 50} more")
        }
    }
    println()

    // List Droid sessions
    println("Droid Sessions:")
    val droidSessions = DroidSessionFinder.listSessions()
    if (droidSessions.isEmpty()) {
        println("  No sessions found")
    } else {
        droidSessions.take(50).forEach { session ->
            println("  [${session.updated.take(16)}] ${session.sessionId} - ${session.title.take(60)}")
        }
        if (droidSessions.size > 50) {
            println("  ... and ${droidSessions.size - 50} more")
        }
    }

    println()

    // List Gemini sessions
    println("Gemini Sessions:")
    val geminiSessions = GeminiSessionFinder.listSessions()
    if (geminiSessions.isEmpty()) {
        println("  No sessions found")
    } else {
        geminiSessions.take(50).forEach { session ->
            println("  [${session.created.take(16)}] ${session.sessionId} - ${session.projectName}")
        }
        if (geminiSessions.size > 50) {
            println("  ... and ${geminiSessions.size - 50} more")
        }
    }

    println()

    // List Kilo Code sessions
    println("Kilo Code Sessions:")
    val kiloSessions = KiloSessionFinder.listSessionFiles()
    if (kiloSessions.isEmpty()) {
        println("  No sessions found")
    } else {
        kiloSessions.take(50).forEach { session ->
            println("  ${session.taskId} (session: ${session.sessionId}) - ${session.projectPath}")
        }
        if (kiloSessions.size > 50) {
            println("  ... and ${kiloSessions.size - 50} more")
        }
    }
    println()
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val params = mutableMapOf<String, String>()
    for (arg in args) {
        when {
            arg == "--help" || arg == "-h" -> params["help"] = "true"
            arg == "--list" || arg == "-l" -> params["list"] = "true"
            arg.startsWith("--") -> {
                val parts = arg.removePrefix("--").split("=", limit = 2)
                if (parts.size == 2) params[parts[0]] = parts[1]
            }
        }
    }
    return params
}

private fun printUsage() {
    println("""
        Session HTML Dumper - Standalone CLI

        Usage:
          ./gradlew dumpSession --args="--id=<session-id> [options]"

        Options:
          --id=<session-id>   The session ID to dump (required)
          --output=<file>     Output file path
          --list              List available sessions
          --help              Show this help

        Examples:
          ./gradlew dumpSession --args="--list"
          ./gradlew dumpSession --args="--id=abc123"
          ./gradlew dumpSession --args="--id=xyz789 --output=my-session.html"
    """.trimIndent())
}
