package de.espend.ml.llm.usage.provider

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.usage.AccountPanelInfo
import de.espend.ml.llm.usage.ProviderInfo
import de.espend.ml.llm.usage.UsageAccountConfig
import de.espend.ml.llm.usage.UsageAccountState
import de.espend.ml.llm.usage.UsageEntry
import de.espend.ml.llm.usage.UsageFetchResult
import de.espend.ml.llm.usage.UsageFormatUtils
import de.espend.ml.llm.usage.UsageProvider
import de.espend.ml.llm.usage.ui.UsageFormPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JPanel

/**
 * Usage provider for OpenCode Go.
 *
 * Fetches the OpenCode Go workspace page with the browser auth cookie and reads the
 * hydrated usage windows:
 *  - 5h
 *  - Week
 *  - Month
 */
class OpenCodeGoUsageProvider : UsageProvider {

    override val providerInfo = ProviderInfo(PROVIDER_ID, PROVIDER_NAME, PluginIcons.OPENCODE_GO)
    override fun getAccountPanelInfo(account: UsageAccountConfig): AccountPanelInfo = AccountPanelInfo.progressbar(3)
    override val configClass = OpenCodeGoUsageAccountConfig::class

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun openForm(panel: UsageFormPanel, initial: UsageAccountConfig): () -> Unit {
        val config = initial as? OpenCodeGoUsageAccountConfig
            ?: throw RuntimeException("Expected OpenCodeGoUsageAccountConfig but got ${initial::class.simpleName}")

        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        val hintColor = UIUtil.getContextHelpForeground()

        val cookieArea = JBTextArea(config.cookieHeader, 4, 44).apply {
            font = smallFont
            lineWrap = true
            wrapStyleWord = false
            emptyText.text = "Cookie: auth=...; oc_locale=...  or  auth=..."
        }
        val cookieScrollPane = JBScrollPane(cookieArea).apply {
            border = JBUI.Borders.customLine(UIUtil.getBoundsColor())
            preferredSize = Dimension(JBUI.scale(420), JBUI.scale(92))
            minimumSize = Dimension(JBUI.scale(260), JBUI.scale(82))
        }

        val cookieRow = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Browser Cookie header or auth cookie:").apply { font = smallFont }, BorderLayout.NORTH)
            add(cookieScrollPane, BorderLayout.CENTER)
        }

        val hintRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JBLabel("Paste either the full Cookie header from ").apply {
                font = smallFont
                foreground = hintColor
            })
            add(ActionLink("opencode.ai") { BrowserUtil.browse("https://opencode.ai") }.apply {
                font = smallFont
            })
            add(JBLabel("; it must include auth=... or __Host-auth=...").apply {
                font = smallFont
                foreground = hintColor
            })
        }

        val requiredCookieHint = JBLabel("Only the auth cookie is stored; oc_locale and other cookies are ignored.").apply {
            font = smallFont
            foreground = hintColor
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val box = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(cookieRow)
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(hintRow)
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(requiredCookieHint)
        }

        panel.providerPanel.add(box, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        })

        return {
            config.cookieHeader = normalizeCookieHeader(cookieArea.text.trim()).orEmpty()
        }
    }

    override suspend fun fetchUsage(account: UsageAccountConfig): UsageFetchResult {
        val config = account as? OpenCodeGoUsageAccountConfig
            ?: return UsageFetchResult.error("Invalid OpenCode Go account config")

        val cookieHeader = normalizeCookieHeader(config.cookieHeader)
            ?: return UsageFetchResult.error("No OpenCode Go auth cookie configured")

        return try {
            val workspaceIds = fetchWorkspaceIds(cookieHeader)

            for (workspaceId in workspaceIds) {
                fetchWorkspaceUsageOrNull(cookieHeader, workspaceId)?.let { return it }
            }

            UsageFetchResult.error("No OpenCode Go usage data found in ${workspaceIds.size} workspaces")
        } catch (e: Exception) {
            UsageFetchResult.error("Error: ${e.message?.take(100)}")
        }
    }

    private suspend fun fetchWorkspaceUsageOrNull(cookieHeader: String, workspaceId: String): UsageFetchResult? {
        val page = fetchUsagePage(cookieHeader, workspaceId)
        if (page.isBlank()) throw RuntimeException("Empty response from OpenCode Go")
        if (looksSignedOut(page)) throw RuntimeException("OpenCode Go cookie is invalid or expired")

        return parseUsageEntriesOrNull(page)?.let { UsageFetchResult.success(it) }
    }

    private suspend fun fetchWorkspaceIds(cookieHeader: String): List<String> {
        val text = fetchServerText(
            serverId = WORKSPACES_SERVER_ID,
            args = null,
            method = "GET",
            referer = BASE_URL,
            cookieHeader = cookieHeader
        )
        if (looksSignedOut(text)) {
            throw RuntimeException("OpenCode Go cookie is invalid or expired")
        }

        val ids = parseWorkspaceIds(text).ifEmpty { parseWorkspaceIdsFromJson(text) }
        if (ids.isNotEmpty()) return ids

        val fallback = fetchServerText(
            serverId = WORKSPACES_SERVER_ID,
            args = "[]",
            method = "POST",
            referer = BASE_URL,
            cookieHeader = cookieHeader
        )
        if (looksSignedOut(fallback)) {
            throw RuntimeException("OpenCode Go cookie is invalid or expired")
        }

        return parseWorkspaceIds(fallback).ifEmpty { parseWorkspaceIdsFromJson(fallback) }.ifEmpty {
            throw RuntimeException("Could not find OpenCode Go workspace id")
        }
    }

    private suspend fun fetchUsagePage(cookieHeader: String, workspaceId: String): String {
        return withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/workspace/$workspaceId/go"))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookieHeader)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> response.body() ?: ""
                401, 403 -> throw RuntimeException("OpenCode Go cookie is invalid or expired")
                else -> throw RuntimeException("HTTP ${response.statusCode()} from OpenCode Go")
            }
        }
    }

    private suspend fun fetchServerText(
        serverId: String,
        args: String?,
        method: String,
        referer: String,
        cookieHeader: String
    ): String {
        return withContext(Dispatchers.IO) {
            val uri = if (method == "GET") {
                buildServerUri(serverId, args)
            } else {
                URI.create(SERVER_URL)
            }

            val builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", cookieHeader)
                .header("X-Server-Id", serverId)
                .header("X-Server-Instance", "server-fn:${UUID.randomUUID()}")
                .header("User-Agent", USER_AGENT)
                .header("Origin", BASE_URL)
                .header("Referer", referer)
                .header("Accept", "text/javascript, application/json;q=0.9, */*;q=0.8")

            val request = if (method == "POST") {
                builder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(args ?: "[]"))
                    .build()
            } else {
                builder.GET().build()
            }

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> response.body() ?: ""
                401, 403 -> throw RuntimeException("OpenCode Go cookie is invalid or expired")
                else -> throw RuntimeException("HTTP ${response.statusCode()} from OpenCode Go")
            }
        }
    }

    internal fun parseResponseText(text: String, now: Instant = Instant.now()): UsageFetchResult {
        if (text.isBlank()) return UsageFetchResult.error("Empty response from OpenCode Go")
        if (looksSignedOut(text)) return UsageFetchResult.error("OpenCode Go cookie is invalid or expired")

        val entries = parseUsageEntriesOrNull(text, now)
            ?: return UsageFetchResult.error("No OpenCode Go usage data found")

        return UsageFetchResult.success(entries)
    }

    private fun parseUsageEntriesOrNull(text: String, now: Instant = Instant.now()): List<UsageEntry>? {
        val windows = parseJsonUsage(text, now) ?: parseSerovalUsage(text)
        if (windows == null || windows.isEmpty()) return null

        return listOf(
            toEntry(WINDOW_5H_LABEL, windows.fiveHour),
            toEntry(WINDOW_WEEK_LABEL, windows.week),
            toEntry(WINDOW_MONTH_LABEL, windows.month)
        )
    }

    private fun parseSerovalUsage(text: String): UsageWindows? {
        val fiveHour = parseSerovalWindow("rollingUsage", text)
        val week = parseSerovalWindow("weeklyUsage", text)
        val month = parseSerovalWindow("monthlyUsage", text)
        return UsageWindows(fiveHour, week, month).takeUnless { it.isEmpty() }
    }

    private fun parseSerovalWindow(name: String, text: String): UsageWindow? {
        val percent = Regex("""$name[^}]*?usagePercent\s*:\s*([-+]?[0-9]+(?:\.[0-9]+)?)""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull()
        val resetInSec = Regex("""$name[^}]*?resetInSec\s*:\s*([0-9]+)""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

        if (percent == null && resetInSec == null) return null
        return UsageWindow(
            percent = normalizePercent(percent ?: 0.0),
            resetInSeconds = resetInSec
        )
    }

    private fun parseJsonUsage(text: String, now: Instant): UsageWindows? {
        val root = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return null
        return parseJsonElementUsage(root, now) ?: parseUsageFromCandidates(root, now)
    }

    private fun parseJsonElementUsage(element: JsonElement, now: Instant): UsageWindows? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject

        parseUsageDictionary(obj, now)?.let { return it }

        for (key in listOf("data", "result", "usage", "billing", "payload")) {
            val child = obj.getObjectOrNull(key) ?: continue
            parseUsageDictionary(child, now)?.let { return it }
        }

        return parseUsageNested(obj, now, depth = 0)
    }

    private fun parseUsageDictionary(obj: JsonObject, now: Instant): UsageWindows? {
        obj.getObjectOrNull("usage")?.let { usage ->
            parseUsageDictionary(usage, now)?.let { return it }
        }

        val fiveHour = firstObject(
            obj,
            "rollingUsage",
            "rolling",
            "rolling_usage",
            "rollingWindow",
            "rolling_window",
            "fiveHour",
            "five_hour"
        )?.let { parseWindow(it, now) }
        val week = firstObject(
            obj,
            "weeklyUsage",
            "weekly",
            "weekly_usage",
            "weeklyWindow",
            "weekly_window"
        )?.let { parseWindow(it, now) }
        val month = firstObject(
            obj,
            "monthlyUsage",
            "monthly",
            "monthly_usage",
            "monthlyWindow",
            "monthly_window"
        )?.let { parseWindow(it, now) }

        return UsageWindows(fiveHour, week, month).takeUnless { it.isEmpty() }
    }

    private fun parseUsageNested(obj: JsonObject, now: Instant, depth: Int): UsageWindows? {
        if (depth > 3) return null

        var fiveHour: UsageWindow? = null
        var week: UsageWindow? = null
        var month: UsageWindow? = null

        for ((key, value) in obj.entrySet()) {
            if (!value.isJsonObject) continue
            val lower = key.lowercase()
            val parsed = parseWindow(value.asJsonObject, now)
            when {
                parsed != null && isFiveHourKey(lower) -> fiveHour = parsed
                parsed != null && isWeekKey(lower) -> week = parsed
                parsed != null && isMonthKey(lower) -> month = parsed
            }
        }

        UsageWindows(fiveHour, week, month).takeUnless { it.isEmpty() }?.let { return it }

        for ((_, value) in obj.entrySet()) {
            if (!value.isJsonObject) continue
            parseUsageNested(value.asJsonObject, now, depth + 1)?.let { return it }
        }

        return null
    }

    private fun parseUsageFromCandidates(element: JsonElement, now: Instant): UsageWindows? {
        val candidates = mutableListOf<WindowCandidate>()
        collectWindowCandidates(element, now, emptyList(), candidates)
        if (candidates.isEmpty()) return null

        val fiveHourCandidates = candidates.filter { isFiveHourKey(it.pathLower) }
        val weekCandidates = candidates.filter { isWeekKey(it.pathLower) }
        val monthCandidates = candidates.filter { isMonthKey(it.pathLower) }

        val fiveHour = pickCandidate(
            candidates = fiveHourCandidates.ifEmpty { candidates },
            pickShorter = true
        )
        val month = pickCandidate(
            candidates = monthCandidates.filter { it.id != fiveHour?.id },
            pickShorter = false
        )
        val week = pickCandidate(
            candidates = weekCandidates.filter { it.id != fiveHour?.id && it.id != month?.id }
                .ifEmpty {
                    candidates.filter { it.id != fiveHour?.id && it.id != month?.id }
                },
            pickShorter = false
        )

        return UsageWindows(
            fiveHour = fiveHour?.window,
            week = week?.window,
            month = month?.window
        ).takeUnless { it.isEmpty() }
    }

    private fun collectWindowCandidates(
        element: JsonElement,
        now: Instant,
        path: List<String>,
        out: MutableList<WindowCandidate>
    ) {
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                parseWindow(obj, now)?.let { window ->
                    out += WindowCandidate(UUID.randomUUID(), window, path.joinToString(".").lowercase())
                }
                for ((key, value) in obj.entrySet()) {
                    collectWindowCandidates(value, now, path + key, out)
                }
            }
            element.isJsonArray -> {
                element.asJsonArray.forEachIndexed { index, value ->
                    collectWindowCandidates(value, now, path + "[$index]", out)
                }
            }
        }
    }

    private fun parseWindow(obj: JsonObject, now: Instant): UsageWindow? {
        var percent = firstDouble(obj, PERCENT_KEYS)
        if (percent == null) {
            val used = firstDouble(obj, USED_KEYS)
            val limit = firstDouble(obj, LIMIT_KEYS)
            if (used != null && limit != null && limit > 0) {
                percent = used / limit * 100
            }
        }

        var resetInSeconds = firstLong(obj)
        if (resetInSeconds == null) {
            firstDate(obj)?.let { resetAt ->
                resetInSeconds = (resetAt.epochSecond - now.epochSecond).coerceAtLeast(0)
            }
        }

        if (percent == null && resetInSeconds == null) return null
        return UsageWindow(
            percent = normalizePercent(percent ?: 0.0),
            resetInSeconds = resetInSeconds?.coerceAtLeast(0)
        )
    }

    private fun toEntry(label: String, window: UsageWindow?): UsageEntry {
        if (window == null) {
            return UsageEntry(0f, "$label · n/a")
        }

        val reset = window.resetInSeconds?.let { UsageFormatUtils.formatSecondsUntilReset(it) }
        return UsageEntry(
            percentageUsed = window.percent.toFloat().coerceIn(0f, 100f),
            subtitle = if (reset != null) "$label · $reset" else label
        )
    }

    private fun normalizePercent(raw: Double): Double {
        val asPercent = if (raw in 0.0..1.0) raw * 100 else raw
        return asPercent.coerceIn(0.0, 100.0)
    }

    private fun pickCandidate(candidates: List<WindowCandidate>, pickShorter: Boolean): WindowCandidate? {
        if (candidates.isEmpty()) return null
        val comparator = compareBy<WindowCandidate> { it.window.resetInSeconds ?: Long.MAX_VALUE }
            .thenByDescending { it.window.percent }
        return if (pickShorter) {
            candidates.minWithOrNull(comparator)
        } else {
            candidates.maxWithOrNull(comparator)
        }
    }

    private fun parseWorkspaceIds(text: String): List<String> {
        return Regex("id\\s*:\\s*\"([^\"]+)\"")
            .findAll(text)
            .map { it.groupValues[1] }
            .filter { it.startsWith("wrk_") }
            .distinct()
            .toList()
    }

    private fun parseWorkspaceIdsFromJson(text: String): List<String> {
        val root = runCatching { JsonParser.parseString(text) }.getOrNull() ?: return emptyList()
        val ids = mutableListOf<String>()
        collectWorkspaceIds(root, ids)
        return ids.distinct()
    }

    private fun collectWorkspaceIds(element: JsonElement, out: MutableList<String>) {
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (_, value) ->
                collectWorkspaceIds(value, out)
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectWorkspaceIds(it, out) }
            element.isJsonPrimitive -> {
                val value = runCatching { element.asString }.getOrNull()
                if (value?.startsWith("wrk_") == true) out += value
            }
        }
    }

    private fun buildServerUri(serverId: String, args: String?): URI {
        val encodedId = URLEncoder.encode(serverId, StandardCharsets.UTF_8)
        val encodedArgs = args?.takeIf { it.isNotEmpty() }
            ?.let { "&args=${URLEncoder.encode(it, StandardCharsets.UTF_8)}" }
            ?: ""
        return URI.create("$SERVER_URL?id=$encodedId$encodedArgs")
    }

    private fun looksSignedOut(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("login") ||
            lower.contains("sign in") ||
            lower.contains("auth/authorize") ||
            lower.contains("not associated with an account") ||
            lower.contains("actor of type \"public\"")
    }

    private fun firstObject(obj: JsonObject, vararg keys: String): JsonObject? {
        return keys.firstNotNullOfOrNull { obj.getObjectOrNull(it) }
    }

    private fun JsonObject.getObjectOrNull(key: String): JsonObject? {
        val element = get(key) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun firstDouble(obj: JsonObject, keys: List<String>): Double? {
        return keys.firstNotNullOfOrNull { key ->
            obj.get(key)?.toDoubleOrNull()
        }
    }

    private fun firstLong(obj: JsonObject): Long? {
        return RESET_IN_KEYS.firstNotNullOfOrNull { key ->
            obj.get(key)?.toLongOrNull()
        }
    }

    private fun firstDate(obj: JsonObject): Instant? {
        return RESET_AT_KEYS.firstNotNullOfOrNull { key ->
            obj.get(key)?.toInstantOrNull()
        }
    }

    private fun JsonElement.toDoubleOrNull(): Double? {
        if (!isJsonPrimitive) return null
        return runCatching { asDouble }.getOrNull()
    }

    private fun JsonElement.toLongOrNull(): Long? {
        return toDoubleOrNull()?.toLong()
    }

    private fun JsonElement.toInstantOrNull(): Instant? {
        if (!isJsonPrimitive) return null
        toDoubleOrNull()?.let { number ->
            return when {
                number > 1_000_000_000_000 -> Instant.ofEpochMilli(number.toLong())
                number > 1_000_000_000 -> Instant.ofEpochSecond(number.toLong())
                else -> null
            }
        }

        val value = runCatching { asString.trim() }.getOrNull() ?: return null
        value.toDoubleOrNull()?.let { number ->
            return when {
                number > 1_000_000_000_000 -> Instant.ofEpochMilli(number.toLong())
                number > 1_000_000_000 -> Instant.ofEpochSecond(number.toLong())
                else -> null
            }
        }

        return try {
            Instant.parse(value)
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value.substringBefore(".") + "Z")
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun isFiveHourKey(value: String): Boolean {
        return value.contains("rolling") ||
            value.contains("hour") ||
            value.contains("5h") ||
            value.contains("5-hour") ||
            value.contains("primary") ||
            value.contains("session")
    }

    private fun isWeekKey(value: String): Boolean {
        return value.contains("weekly") ||
            value.contains("week") ||
            value.contains("secondary")
    }

    private fun isMonthKey(value: String): Boolean {
        return value.contains("monthly") ||
            value.contains("month") ||
            value.contains("tertiary")
    }

    override fun fromState(state: UsageAccountState): UsageAccountConfig {
        return OpenCodeGoUsageAccountConfig(state).apply {
            val storedCookie = state.getString("cookieHeader")
            cookieHeader = normalizeCookieHeader(storedCookie) ?: storedCookie.trim()
        }
    }

    override fun toState(config: UsageAccountConfig): UsageAccountState {
        val c = config as OpenCodeGoUsageAccountConfig
        return createState(c) {
            putString("cookieHeader", normalizeCookieHeader(c.cookieHeader).orEmpty())
        }
    }

    companion object {
        const val PROVIDER_ID = "opencode-go"
        const val PROVIDER_NAME = "OpenCode Go"

        private const val BASE_URL = "https://opencode.ai"
        private const val SERVER_URL = "$BASE_URL/_server"
        private const val WORKSPACES_SERVER_ID =
            "def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f"
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"

        private const val WINDOW_5H_LABEL = "5h"
        private const val WINDOW_WEEK_LABEL = "Week"
        private const val WINDOW_MONTH_LABEL = "Month"

        private val PERCENT_KEYS = listOf(
            "usagePercent",
            "usedPercent",
            "percentUsed",
            "percent",
            "usage_percent",
            "used_percent",
            "utilization",
            "utilizationPercent",
            "utilization_percent",
            "usage"
        )
        private val USED_KEYS = listOf("used", "usage", "consumed", "count", "usedTokens")
        private val LIMIT_KEYS = listOf("limit", "total", "quota", "max", "cap", "tokenLimit")
        private val RESET_IN_KEYS = listOf(
            "resetInSec",
            "resetInSeconds",
            "resetSeconds",
            "reset_sec",
            "reset_in_sec",
            "resetsInSec",
            "resetsInSeconds",
            "resetIn",
            "resetSec"
        )
        private val RESET_AT_KEYS = listOf(
            "resetAt",
            "resetsAt",
            "reset_at",
            "resets_at",
            "nextReset",
            "next_reset"
        )

        internal fun normalizeCookieHeader(raw: String): String? {
            val cleaned = raw
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    when {
                        line.equals("Cookie", ignoreCase = true) -> null
                        line.startsWith("Cookie:", ignoreCase = true) -> line.substringAfter(":").trim()
                        else -> line
                    }
                }
                .joinToString("; ")

            val authCookies = cleaned
                .split(';')
                .map { it.trim() }
                .filter { it.contains('=') }
                .filter { cookie ->
                    val name = cookie.substringBefore('=').trim()
                    name == "auth" || name == "__Host-auth"
                }

            return authCookies.takeIf { it.isNotEmpty() }?.joinToString("; ")
        }
    }

    private data class UsageWindow(
        val percent: Double,
        val resetInSeconds: Long?
    )

    private data class UsageWindows(
        val fiveHour: UsageWindow?,
        val week: UsageWindow?,
        val month: UsageWindow?
    ) {
        fun isEmpty(): Boolean = fiveHour == null && week == null && month == null
    }

    private data class WindowCandidate(
        val id: UUID,
        val window: UsageWindow,
        val pathLower: String
    )

    class OpenCodeGoUsageAccountConfig(state: UsageAccountState? = null) : UsageAccountConfig(state) {
        override val providerId: String = PROVIDER_ID
        var cookieHeader: String = ""

        override fun getInfoString(): String {
            val authCookie = normalizeCookieHeader(cookieHeader)
                ?.split(';')
                ?.firstOrNull()
                ?.substringAfter('=', "")
                ?.takeIf { it.isNotBlank() }

            val parts = mutableListOf<String>()
            if (authCookie != null) {
                parts += UsageFormatUtils.formatSecret(authCookie)
            }
            return parts.joinToString(" · ")
        }
    }
}
