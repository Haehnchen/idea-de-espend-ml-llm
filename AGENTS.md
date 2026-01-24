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
