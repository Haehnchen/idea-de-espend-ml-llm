Changelog
=========

# 0.9.2
* Remove `untilBuild` restriction in `ideaVersion` configuration (Daniel Espendiller)

# 0.9.1
* Add node bridge to strip gateway call: Problem: IntelliJ 253+ sends `auth._meta.gateway=true` (ml-llm-253.32098.66) to ALL ACP agents. claude-agent-acp responds by advertising a "gateway" auth method (Daniel Espendiller)

# 0.9.0
* Add AI Provider Usage status bar widget and associated settings integration (Daniel Espendiller)
* change for Gemini CLI provider binary, including installation link and UI description (Daniel Espendiller)
* Add support for Kilo Code provider, including CLI path resolution, configuration, and installation links (Daniel Espendiller)
* Migrate `OpenCodeSessionParser` and `OpenCodeSessionFinder` to use SQLite database instead of file-based storage for session data, improving performance and scalability. (Daniel Espendiller)
* Truncate long provider labels in `ProviderUsagePanel` to improve UI readability (Daniel Espendiller)
* Migrate `KiloSessionFinder` and `KiloSessionParser` to use SQLite database instead of file-based storage for session data (Daniel Espendiller)
* Improve error handling in `ProviderUsageService` (Daniel Espendiller)
* Enhance status bar widget with drop-up menu for account toggling and settings access (Daniel Espendiller)
* Refactor `ProviderUsageService` to add per-account caching, cache listeners, and enhanced concurrency handling (Daniel Espendiller)

# 0.8.1
* Add AI Provider Usage status bar widget and associated settings integration (Daniel Espendiller)</li>

# 0.8.0
* Add RTK token savings stats panel to usage popup (Daniel Espendiller)
* Add support for Ollama usage provider (Daniel Espendiller)

# 0.7.4
* Implement caching mechanism for usage data and improve UI handling of checkbox listeners (Daniel Espendiller)
* Support cancellation for commit message generation and improve UI handling (Daniel Espendiller)
* Refactor panel data handling by introducing `AccountPanelInfo` for improved layout flexibility in usage providers. (Daniel Espendiller)
 
# 0.7.3
* Add info string formatting to usage providers (Daniel Espendiller)
* Simplify Codex manual mode to load tokens from file (Daniel Espendiller)
* Remove the separate subtitle label and include it in the percentage label to reduce vertical space. (Daniel Espendiller)

# 0.7.2
* Handle full balance in Ampcode subtitle formatting (Daniel Espendiller)
* Update Cursor provider to use built-in ACP (Daniel Espendiller)
* Replace `claude-code-acp` with `claude-agent-acp` across the project. Add reusable `PackageActionLink` UI component for npm package links. (Daniel Espendiller)
* Optimize claude usage form user flow (Daniel Espendiller)

# 0.7.1
* Improve `parseUsageBody` to handle missing or null utilization fields by adding "not started" entries for five-hour and seven-day windows. (Daniel Espendiller)
* Add Junie provider to usage service (Daniel Espendiller)

# 0.7.0
* Add provider usage tracking toolbar for Codex, Claude, Zai, Amp (Daniel Espendiller)

# 0.6.0
* Add commit message generator using AI provider integration (Daniel Espendiller)
* Model Selector: Enable refresh button management and refactor button initialization (Daniel Espendiller)
* Add Agent Providers action to AI Assistant agent dropdown (Daniel Espendiller)

# 0.5.2
* Update IntelliJ IDEA Ultimate version to 2025.3.3 (Daniel Espendiller)

# 0.5.1
* Fix usages again AgentServerConfig parameters (Daniel Espendiller)

# 0.5.0
* Add McpToolset support for AI session search and retrieval (Daniel Espendiller)
* Add support for Droid, Gemini, and Kilo Code session adapters. (Daniel Espendiller)
* Add Junie session adapter (Daniel Espendiller)

# 0.4.1
* remove not allowed features for extending chat windows with own providers (Daniel Espendiller)

# 0.4.0
* Add AMP session support (Daniel Espendiller)
* Skipping `isMeta`, `local-command-stdout`, and command messages in Claude session. (Daniel Espendiller)

# 0.3.0
* Add Session Browser for viewing Claude Code, OpenCode and Codex sessions (Daniel Espendiller)
* fix api changes (Daniel Espendiller)

## 0.2.1
* Register AgentStartupActivity for post-startup execution in plugin.xml (Daniel Espendiller)

## 0.2.0
* Add support for auto-registering profiles in providers for core AI feature activation. (Daniel Espendiller)
* Add activation panel with clickable link for AI model configuration and `ActiveModelSetter` utility for easier model management (Daniel Espendiller)
* Add core AI features setup, activation instructions, and integrate `AgentProvidersAIProvider` into plugin settings. (Daniel Espendiller)
* Add AIHubMix provider support (Daniel Espendiller)
* Add Nano-GPT provider support (Daniel Espendiller)
* Add Requesty.ai provider support (Daniel Espendiller)
* Add Factory.ai Droid provider support (Daniel Espendiller)
* Add Cursor provider support (Daniel Espendiller)
 
## 0.1.0
* first release
