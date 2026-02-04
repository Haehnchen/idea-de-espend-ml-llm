package de.espend.ml.llm.session.model

/**
 * Represents message content with display type information.
 * The display type determines how the content is rendered (escaped text, code block, markdown HTML, etc.)
 */
sealed class MessageContent {
    /** Plain text content (HTML escaped). */
    data class Text(val text: String) : MessageContent()

    /** Code content (displayed in a code block). */
    data class Code(val code: String, val language: String? = null) : MessageContent()

    /** Markdown content (rendered as HTML). */
    data class Markdown(val markdown: String) : MessageContent()

    /** Raw JSON content (displayed as code). */
    data class Json(val json: String) : MessageContent()
}

/**
 * Base sealed class for all message types.
 * Each message has a timestamp.
 */
sealed class ParsedMessage {
    abstract val timestamp: String

    /**
     * User message containing text or tool results.
     */
    data class User(
        override val timestamp: String,
        val content: List<MessageContent>
    ) : ParsedMessage()

    /**
     * Assistant text response message (pure text, no tool use).
     */
    data class AssistantText(
        override val timestamp: String,
        val content: List<MessageContent>
    ) : ParsedMessage()

    /**
     * Assistant thinking/reasoning message.
     */
    data class AssistantThinking(
        override val timestamp: String,
        val thinking: String
    ) : ParsedMessage()

    /**
     * Tool use (invocation) message.
     * Contains the tool invocation and optionally its results.
     */
    data class ToolUse(
        override val timestamp: String,
        val toolName: String,
        val toolCallId: String? = null,
        val input: Map<String, String>,
        val results: List<ToolResult> = emptyList()
    ) : ParsedMessage() {
        /**
         * Returns true if this tool use has associated results.
         */
        fun hasResults(): Boolean = results.isNotEmpty()
    }

    /**
     * Tool result message.
     */
    data class ToolResult(
        override val timestamp: String,
        val toolName: String? = null,
        val toolCallId: String? = null,
        val output: List<MessageContent>,
        val isError: Boolean = false
    ) : ParsedMessage()

    /**
     * Display style for info messages.
     */
    enum class InfoStyle {
        /** Default/neutral style (grey). */
        DEFAULT,
        /** Error style (red). */
        ERROR
    }

    /**
     * Generic info message with configurable title, subtitle, content, and visual style.
     *
     * @property title Main title/label for the message (e.g., "system", "error", "summary", "queue", "command")
     * @property subtitle Optional secondary label (e.g., error name, operation type, command name)
     * @property content The message content to display
     * @property style Visual style (DEFAULT=grey, ERROR=red)
     */
    data class Info(
        override val timestamp: String,
        val title: String,
        val subtitle: String? = null,
        val content: MessageContent? = null,
        val style: InfoStyle = InfoStyle.DEFAULT
    ) : ParsedMessage()
}


