# AGENTS.md

This file provides guidance when working with code in this repository.

### Project Overview

This is an **IntelliJ Platform plugin** that extends the built-in AI Assistant with multiple AI provider support. It acts as a bridge between IntelliJ's AI chat interface and various AI providers through an Anthropic-compatible API layer and others.

**Plugin ID**: `de.espend.ml.llm`

**Key Dependencies**: Requires the AI Assistant plugin (`com.intellij.ml.llm`)

### Running Tests

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "de.espend.ml.llm"

# Run tests matching a pattern
./gradlew test --tests "*ProviderTest"
```

### Building and Publishing

```bash
# Verify plugin structure and compatibility
./gradlew verifyPlugin

# Build plugin
./gradlew buildPlugin

# Publish to marketplace
IJ_TOKEN=yourtoken ./gradlew clean buildPlugin publishPlugin

# Update Gradle wrapper
./gradlew wrapper
```

### Development Notes

- **Language**: Kotlin 2.1.20 with JVM 21 target
- **Build System**: Gradle with IntelliJ Platform Plugin 2.10.2
- **Compatibility**: IntelliJ IDEA 2025.3.2+ (since-build 253)
- **No external libraries**: Uses only IntelliJ Platform and standard Kotlin APIs

### Adding a New Provider

1. Add provider constant to `ProviderConfig.kt`
2. Create `ProviderInfo` entry with metadata (label, icon, base URL, models)
3. Update `AgentSettingsConfigurable.kt` to add UI panel for the provider
4. Add executable detection logic in `CommandPathUtils.kt` if CLI-based

#### Icon Requirements

- **Location**: `src/main/resources/icons/{provider-name}.png`
- **Size**: 32x32 pixels
- **Format**: PNG with transparency support
- **Add to PluginIcons.kt**: `val PROVIDER_NAME: Icon = IconLoader.getIcon("/icons/provider-name.png", PluginIcons::class.java)`

#### Path Detection for CLI-based Providers

For CLI-based providers (like Gemini, OpenCode, Cursor), add a detection function to `CommandPathUtils.kt`:

**Search order**: PATH → `/usr/bin` → `$HOME/bin` → `$HOME/.local/bin` → provider-specific directory (e.g., `$HOME/.gemini/bin`)

### Session Explorer

The plugin includes a **Session Explorer** tool for browsing and viewing chat sessions from external AI tools (Claude Code, OpenCode, Codex). This is useful for debugging, reviewing conversations, and understanding AI interactions.

**Location**: `src/main/kotlin/de/espend/ml/llm/session/`

#### Architecture

```
session/
├── adapter/
│   ├── claude/
│   │   ├── ClaudeSessionFinder.kt    # Standalone file finder
│   │   └── ClaudeSessionParser.kt    # Standalone JSONL parser
│   ├── codex/
│   │   ├── CodexSessionFinder.kt     # Standalone file finder (JetBrains AI)
│   │   └── CodexSessionParser.kt     # Standalone JSONL parser
│   ├── opencode/
│   │   ├── OpenCodeSessionFinder.kt  # Standalone file finder
│   │   └── OpenCodeSessionParser.kt  # Standalone JSON parser
│   ├── ClaudeSessionAdapter.kt       # IntelliJ integration
│   ├── CodexSessionAdapter.kt        # IntelliJ integration
│   └── OpenCodeSessionAdapter.kt     # IntelliJ integration
├── cli/
│   └── SessionHtmlDumperCli.kt       # Standalone CLI tool
├── view/
│   ├── SessionListView.kt            # Session list UI
│   └── SessionDetailView.kt           # Session detail HTML view
└── util/
    ├── HtmlBuilder.kt                 # HTML generation
    ├── JsHandlers.kt                  # JavaScript handlers
    └── ThemeColors.kt                 # Theme utilities
```

#### Key Design Principles

1. **Standalone Parsers**: All parsing logic is in `adapter/claude/`, `adapter/codex/`, and `adapter/opencode/` with no IntelliJ dependencies
2. **Reusability**: The same parsers are used by the IDE plugin and the CLI tool
3. **Testability**: Standalone code can be tested without IntelliJ test infrastructure

#### Running Tests

```bash
# Run all session-related tests
./gradlew test --tests "*Session*"

# Run specific adapter tests
./gradlew test --tests "*ClaudeSessionAdapterTest*"
./gradlew test --tests "*CodexSessionAdapterTest*"
./gradlew test --tests "*OpenCodeSessionAdapterTest*"
```

#### CLI Tool (Integration Testing)

A standalone CLI tool is available for dumping session HTML without running IntelliJ. This is useful for:

- Debugging session parsing issues
- Testing HTML rendering
- Integration testing without IDE overhead
- Generating session exports

```bash
# List all available sessions (Claude + Codex + OpenCode)
./gradlew dumpSession --args="--list"

# Dump a specific session to HTML
./gradlew dumpSession --args="--id=<session-id>"

# Specify output file
./gradlew dumpSession --args="--id=<session-id> --output=session.html"

# Show help
./gradlew dumpSession --args="--help"
```

**Examples:**
```bash
# Claude Code session
./gradlew dumpSession --args="--id=7b278ef6-073a-4de5-a789-2f9fd5500a45"

# Codex session (JetBrains AI Assistant)
./gradlew dumpSession --args="--id=019bfa0e-ec98-70e0-8575-5132b767abff"

# OpenCode session
./gradlew dumpSession --args="--id=ses_401025e79ffeZYWrUQDpBmW7Cp"
```

#### Adding Support for a New AI Tool

To add session viewing for a new AI tool (e.g., Cursor, Gemini):

1. **Create adapter package**: `src/main/kotlin/de/espend/ml/llm/session/adapter/{toolname}/`

2. **Create `SessionFinder.kt`**: Standalone utility to find session files
   - Must implement `findSessionFile(sessionId: String)` returning `File?` or `Path?`
   - Must implement `listSessions()` returning list of available sessions
   - No IntelliJ dependencies

3. **Create `SessionParser.kt`**: Standalone parser for session data
   - Must implement `parseSession(sessionId: String)` returning `SessionDetail?`
   - Converts tool-specific format to common `Message` and `SessionMetadata` types
   - No IntelliJ dependencies

4. **Create `{ToolName}SessionAdapter.kt`**: IntelliJ integration layer
   - Uses the standalone finder/parser
   - Adds Project/Logger integration
   - Implements IDE-specific features

5. **Update CLI**: Add the new tool to `SessionHtmlDumperCli.kt`
   - Import finder/parser
   - Add to `findAndParseSession()` function
   - Add to `listSessions()` output

## Decompiler Tools

For analyzing bundled plugins like "AI Assistant plugin" (ml.llm) / "com.intellij.ml.llm" you MUST use **vineflower** and NOT **Fernflower** from IntelliJ (less quality):

**vineflower**

- **GitHub:** https://github.com/Vineflower/vineflower
- **Download:** https://repo1.maven.org/maven2/org/vineflower/vineflower/1.11.2/vineflower-1.11.2.jar
- **Local copy:** `decompiled/vineflower.jar`
- **Usage:** `java -jar vineflower.jar input.jar output/`

**Bundled Plugin JARs (for decompilation):**
- **Location:** `~/.gradle/caches/[gradle-version]/transforms/*/transformed/com.jetbrains.[plugin]-[intellij-version]/[plugin]/lib/[plugin].jar`
- **Example:** `~/.gradle/caches/9.3.0/transforms/*/transformed/com.jetbrains.twig-253.28294.322/twig/lib/twig.jar`
