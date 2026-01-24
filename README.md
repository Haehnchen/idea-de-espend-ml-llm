# Agent Provider Plugin for IntelliJ IDEA

[![JetBrains Plugins](https://img.shields.io/badge/JetBrains-Plugin-blue)](https://plugins.jetbrains.com/)
[![IntelliJ Platform](https://img.shields.io/badge/Platform-IntelliJ%20IDEA-orange)](https://www.jetbrains.com/idea/)

Configure and use multiple AI providers (Claude CLI, Gemini, OpenCode, OpenRouter, and more) as chat Agent in IntelliJ IDEA's AI Assistant chat.

## Features

- **Multiple Provider Support**: Configure and switch between different AI providers through a simple settings UI
- **Anthropic-Compatible API**: Works with any provider that implements the Anthropic API
- **Built-in CLI Support**: Native support for Claude CLI, Gemini CLI, and OpenCode CLI
- **Seamless Integration**: Works directly with IntelliJ IDEA's AI Assistant chat

## Supported Providers

| Provider | Description | Register |
|----------|-------------|----------|
| **Claude CLI** | Uses Claude Code's built-in Anthropic integration | - |
| **Anthropic Compatible** | Any Anthropic-like API (via `@zed-industries/claude-code-acp`) | - |
| **Gemini** | Google's Gemini CLI | - |
| **OpenCode** | The OpenCode CLI | - |
| **Z.AI** | Z.AI via Anthropic Compatible API | [Register](https://z.ai/subscribe?ic=BCLQG4VJIO) |
| **MiniMax** | MiniMax via Anthropic Compatible API | - |
| **OpenRouter** | OpenRouter via Anthropic Compatible API | - |
| **Mimo** | Mimo via Anthropic Compatible API | - |
| **Moonshot** | Moonshot via Anthropic Compatible API | - |

## Installation

1. In IntelliJ IDEA, go to **File → Settings → Plugins**
2. Click the **Marketplace** tab
3. Search for **Agent Providers**
4. Click **Install**
5. Go to **Settings → AI Assistant → Agent Providers**
6. Configure API keys and base URLs as needed
7. Click **Apply** to register the agents

## Requirements

- IntelliJ IDEA (or any JetBrains IDE with AI Assistant support)
- AI Assistant plugin must be installed
- Node.js with npm (for CLI-based providers)
