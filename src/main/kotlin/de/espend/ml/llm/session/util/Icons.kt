package de.espend.ml.llm.session.util

/**
 * SVG icons for the session browser.
 * Icons are from Font Awesome (https://fontawesome.com) - Free/Solid set.
 * Using inline SVGs allows theme support via currentColor.
 */
object Icons {

    /**
     * Generates an inline SVG with consistent styling.
     * @param path The SVG path data
     * @param viewBox The viewBox attribute (default: "0 0 512 512" for FA icons)
     * @param cssClass Optional CSS class for additional styling
     * @param size Size in pixels (default: 12)
     */
    private fun svg(
        path: String,
        viewBox: String = "0 0 512 512",
        cssClass: String = "",
        size: Int = 12
    ): String {
        val classAttr = if (cssClass.isNotEmpty()) " class=\"$cssClass\"" else ""
        return """<svg$classAttr xmlns="http://www.w3.org/2000/svg" viewBox="$viewBox" width="$size" height="$size" fill="currentColor">$path</svg>"""
    }

    // Navigation icons
    fun arrowLeft(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M9.4 233.4c-12.5 12.5-12.5 32.8 0 45.3l160 160c12.5 12.5 32.8 12.5 45.3 0s12.5-32.8 0-45.3L109.2 288 480 288c17.7 0 32-14.3 32-32s-14.3-32-32-32l-370.7 0L214.6 118.6c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0l-160 160z"/>""",
        size = size,
        cssClass = cssClass
    )

    fun arrowUp(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M233.4 105.4c12.5-12.5 32.8-12.5 45.3 0l192 192c12.5 12.5 12.5 32.8 0 45.3s-32.8 12.5-45.3 0L256 173.3 86.6 342.6c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3l192-192z"/>""",
        size = size,
        cssClass = cssClass
    )

    fun chevronDown(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M233.4 406.6c12.5 12.5 32.8 12.5 45.3 0l192-192c12.5 12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L256 338.7 86.6 169.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l192 192z"/>""",
        size = size,
        cssClass = cssClass
    )

    // Token indicator icons
}
