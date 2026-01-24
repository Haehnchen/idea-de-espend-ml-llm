# Agent Provider Plugin for IntelliJ IDEA

[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/Haehnchen/idea-php-symfony2-https://zread.ai/Haehnchen/idea-de-espend-ml-llm)
[![Version](http://phpstorm.espend.de/badge/29900/version)](https://plugins.jetbrains.com/plugin/7219)
[![Downloads](http://phpstorm.espend.de/badge/29900/downloads)](https://plugins.jetbrains.com/plugin/7219)
[![Downloads last month](http://phpstorm.espend.de/badge/29900/last-month)](https://plugins.jetbrains.com/plugin/7219)

Configure and use multiple AI providers (Claude CLI, Gemini, OpenCode, Cursor, Factory.ai, OpenRouter, and more) as chat Agent in IntelliJ IDEA's AI Assistant chat.

| Key                  | Value                                      |
|----------------------|--------------------------------------------|
| Plugin Url           | https://plugins.jetbrains.com/plugin/29900 |
| ID                   | de.espend.ml.llm                           |

## Features

- **Multiple Provider Support**: Configure and switch between different AI providers through a simple settings UI
- **Anthropic-Compatible API**: Works with any provider that implements the Anthropic API
- **Built-in CLI Support**: Native support for Claude CLI, Gemini CLI, OpenCode CLI, and Cursor CLI
- **Seamless Integration**: Works directly with IntelliJ IDEA's AI Assistant chat

## Supported Providers

| Provider | Description | Register |
|----------|-------------|----------|
| **Claude CLI** | Uses Claude Code's built-in Anthropic integration | - |
| **Anthropic Compatible** | Any Anthropic-like API (via `@zed-industries/claude-code-acp`) | - |
| **Gemini** | Google's Gemini CLI | - |
| **OpenCode** | The OpenCode CLI | - |
| **Cursor** | The Cursor Agent CLI (via `@blowmage/cursor-agent-acp`) | - |
| **Factory.ai** | Factory.ai Droid CLI (via `curl -fsSL https://app.factory.ai/cli | sh`) | - |
| **Z.AI** | Z.AI via Anthropic Compatible API | [Register](https://z.ai/subscribe?ic=BCLQG4VJIO) |
| **MiniMax** | MiniMax via Anthropic Compatible API | - |
| **OpenRouter** | OpenRouter via Anthropic Compatible API | - |
| **Mimo** | Mimo via Anthropic Compatible API | - |
| **Moonshot** | Moonshot via Anthropic Compatible API | - |
| **Requesty.ai** | Requesty.ai via Anthropic Compatible API | - |
| **Nano-GPT** | Nano-GPT via Anthropic Compatible API | - |

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


## Images

![Agent](docs/agent.webp)

![Agent Chat Model](docs/agent_chat_model.webp)

![Settings](docs/settings.webp)

![Gemini](docs/gemini.webp)

