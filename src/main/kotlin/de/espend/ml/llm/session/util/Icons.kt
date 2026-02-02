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

    fun arrowDown(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M233.4 406.6c12.5 12.5 32.8 12.5 45.3 0l192-192c12.5 12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L256 338.7 86.6 169.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l192 192z"/>""",
        size = size,
        cssClass = cssClass
    )

    fun chevronDown(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M233.4 406.6c12.5 12.5 32.8 12.5 45.3 0l192-192c12.5 12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L256 338.7 86.6 169.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l192 192z"/>""",
        size = size,
        cssClass = cssClass
    )

    fun chevronUp(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M233.4 105.4c12.5-12.5 32.8-12.5 45.3 0l192 192c12.5 12.5 12.5 32.8 0 45.3s-32.8 12.5-45.3 0L256 173.3 86.6 342.6c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3l192-192z"/>""",
        size = size,
        cssClass = cssClass
    )

    // Token indicator icons
    fun circleArrowDown(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M256 0a256 256 0 1 0 0 512A256 256 0 1 0 256 0zM127 281c-9.4-9.4-9.4-24.6 0-33.9s24.6-9.4 33.9 0l71 71L232 136c0-13.3 10.7-24 24-24s24 10.7 24 24l0 182.1 71-71c9.4-9.4 24.6-9.4 33.9 0s9.4 24.6 0 33.9L273 393c-9.4 9.4-24.6 9.4-33.9 0L127 281z"/>""",
        size = size,
        cssClass = cssClass
    )

    fun circleArrowUp(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM385 231c9.4 9.4 9.4 24.6 0 33.9s-24.6 9.4-33.9 0l-71-71L280 376c0 13.3-10.7 24-24 24s-24-10.7-24-24l0-182.1-71 71c-9.4 9.4-24.6 9.4-33.9 0s-9.4-24.6 0-33.9L239 119c9.4-9.4 24.6-9.4 33.9 0L385 231z"/>""",
        size = size,
        cssClass = cssClass
    )

    fun rotate(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M105.1 202.6c7.7-21.8 20.2-42.3 37.8-59.8c62.5-62.5 163.8-62.5 226.3 0L386.3 160 352 160c-17.7 0-32 14.3-32 32s14.3 32 32 32l111.5 0c0 0 0 0 0 0l.4 0c17.7 0 32-14.3 32-32l0-112c0-17.7-14.3-32-32-32s-32 14.3-32 32l0 35.2L414.4 97.6c-87.5-87.5-229.3-87.5-316.8 0C73.2 122 55.6 150.7 44.8 181.4c-5.9 16.7 2.9 34.9 19.5 40.8s34.9-2.9 40.8-19.5zM39 289.3c-5 1.5-9.8 4.2-13.7 8.2c-4 4-6.7 8.8-8.1 14c-.3 1.2-.6 2.5-.8 3.8c-.3 1.7-.4 3.4-.4 5.1L16 432c0 17.7 14.3 32 32 32s32-14.3 32-32l0-35.1 17.6 17.5c0 0 0 0 0 0c87.5 87.4 229.3 87.4 316.7 0c24.4-24.4 42.1-53.1 52.9-83.8c5.9-16.7-2.9-34.9-19.5-40.8s-34.9 2.9-40.8 19.5c-7.7 21.8-20.2 42.3-37.8 59.8c-62.5 62.5-163.8 62.5-226.3 0l-.1-.1L125.6 352l34.4 0c17.7 0 32-14.3 32-32s-14.3-32-32-32L48.4 288c-1.6 0-3.2 .1-4.8 .3s-3.1 .5-4.6 1z"/>""",
        size = size,
        cssClass = cssClass
    )

    fun circlePlus(size: Int = 12, cssClass: String = ""): String = svg(
        path = """<path d="M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM232 344l0-64-64 0c-13.3 0-24-10.7-24-24s10.7-24 24-24l64 0 0-64c0-13.3 10.7-24 24-24s24 10.7 24 24l0 64 64 0c13.3 0 24 10.7 24 24s-10.7 24-24 24l-64 0 0 64c0 13.3-10.7 24-24 24s-24-10.7-24-24z"/>""",
        size = size,
        cssClass = cssClass
    )
}
