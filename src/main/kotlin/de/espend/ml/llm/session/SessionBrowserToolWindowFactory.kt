package de.espend.ml.llm.session

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import de.espend.ml.llm.session.adapter.ClaudeSessionAdapter
import de.espend.ml.llm.session.adapter.CodexSessionAdapter
import de.espend.ml.llm.session.adapter.OpenCodeSessionAdapter
import de.espend.ml.llm.session.view.ErrorView
import de.espend.ml.llm.session.view.SessionDetailView
import de.espend.ml.llm.session.view.SessionListView
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel

/**
 * Tool Window factory for the Session Browser.
 * Displays Claude Code and OpenCode chat sessions for the current project.
 */
class SessionBrowserToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val sessionBrowser = SessionBrowserPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(sessionBrowser.panel, "", false)
        toolWindow.contentManager.addContent(content)

        // Add refresh action to tool window toolbar
        val refreshAction = object : AnAction("Refresh", "Refresh sessions", AllIcons.Actions.Refresh) {
            override fun actionPerformed(e: AnActionEvent) {
                sessionBrowser.refreshSessions()
            }
        }
        toolWindow.setTitleActions(listOf(refreshAction))
    }

    override fun shouldBeAvailable(project: Project) = true
}

/**
 * Panel containing the JBCefBrowser for displaying sessions.
 */
class SessionBrowserPanel(private val project: Project) {
    val browser: JBCefBrowser
    val panel: JPanel
    private var currentView: View = View.LIST
    private var currentSessionId: String? = null
    private var currentProvider: String? = null
    private val jsQueryRouter: JBCefJSQuery
    private val jsQueryCursor: JBCefJSQuery
    private val lafListener: LafManagerListener

    // View generators
    private val listView: SessionListView
    private val detailView: SessionDetailView
    private val errorView: ErrorView

    companion object {
        private val LOG = Logger.getInstance(SessionBrowserPanel::class.java)
    }

    init {
        browser = JBCefBrowser()
        panel = JPanel(BorderLayout())
        panel.add(browser.component, BorderLayout.CENTER)

        // Add mouse motion listener to update cursor based on hover state
        browser.component.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                // Default cursor - will be overridden by JS cursor updates
                browser.component.cursor = Cursor.getDefaultCursor()
            }
        })

        // Create JS queries for handling JavaScript calls
        jsQueryRouter = JBCefJSQuery.create(browser)
        jsQueryCursor = JBCefJSQuery.create(browser)

        // Initialize view generators
        listView = SessionListView(project, jsQueryRouter, jsQueryCursor)
        detailView = SessionDetailView(jsQueryRouter, jsQueryCursor)
        errorView = ErrorView(jsQueryRouter, jsQueryCursor)

        // Setup JS query handlers
        setupQueryHandlers()

        // Listen for theme changes and refresh the view
        lafListener = LafManagerListener { reloadCurrentView() }
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(LafManagerListener.TOPIC, lafListener)

        // Load initial HTML content (list view)
        loadSessions(false)
    }

    /**
     * Reloads the current view to apply theme changes.
     */
    private fun reloadCurrentView() {
        when (currentView) {
            View.LIST -> loadSessions(false)
            View.DETAIL -> {
                val sessionId = currentSessionId
                val provider = currentProvider
                if (sessionId != null && provider != null) {
                    loadSessionDetail(sessionId, provider)
                } else {
                    loadSessions(false)
                }
            }
        }
    }

    enum class View { LIST, DETAIL }

    fun loadSessions(showRefreshToast: Boolean = false) {
        currentView = View.LIST
        val sessionService = SessionService.getInstance(project)
        val sessions = sessionService.getAllSessions()
        val html = listView.generate(sessions, showRefreshToast)
        browser.loadHTML(html)
    }

    fun loadSessionDetail(sessionId: String, provider: String) {
        try {
            LOG.info("Loading session detail: sessionId=$sessionId, provider=$provider")
            currentView = View.DETAIL
            currentSessionId = sessionId
            currentProvider = provider

            // Get session detail from appropriate adapter
            val sessionDetail = when (provider) {
                "claude" -> ClaudeSessionAdapter(project).getSessionDetail(sessionId)
                "opencode" -> OpenCodeSessionAdapter(project).getSessionDetail(sessionId)
                "codex" -> CodexSessionAdapter(project).getSessionDetail(sessionId)
                else -> null
            }

            // Use unified detail view for all providers
            val html = if (sessionDetail != null) {
                detailView.generateSessionDetail(sessionId, sessionDetail)
            } else {
                detailView.generateNotFoundHtml(sessionId)
            }

            browser.loadHTML(html)
            LOG.info("Session detail page loaded successfully")
        } catch (e: Exception) {
            LOG.error("Failed to load session detail: sessionId=$sessionId, provider=$provider", e)
            val errorHtml = errorView.generateErrorPage("Failed to load session", e.message ?: "Unknown error")
            browser.loadHTML(errorHtml)
        }
    }

    fun refreshSessions() {
        loadSessions(showRefreshToast = true)
    }

    private fun setupQueryHandlers() {
        // Centralized router - handles all action: URLs from link clicks
        jsQueryRouter.addHandler { actionUrl ->
            try {
                LOG.info("Router received: $actionUrl")
                handleAction(actionUrl)
            } catch (e: Exception) {
                LOG.error("Error in router handler", e)
                val errorHtml = errorView.generateErrorPage("Navigation failed", e.message ?: "Unknown error")
                browser.loadHTML(errorHtml)
            }
            null
        }

        jsQueryCursor.addHandler { isClickable ->
            if (isClickable == "true") {
                browser.component.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            } else {
                browser.component.cursor = Cursor.getDefaultCursor()
            }
            null
        }
    }

    /**
     * Handles routing of action URLs to appropriate controllers.
     */
    private fun handleAction(actionUrl: String) {
        val route = de.espend.ml.llm.session.util.JsHandlers.parseActionUrl(actionUrl)
        if (route == null) {
            LOG.warn("Invalid action URL: $actionUrl")
            return
        }

        when (route.action) {
            de.espend.ml.llm.session.util.JsHandlers.Actions.SESSION_DETAIL -> {
                val sessionId = route.getParam("id")
                val provider = route.getParam("provider")
                if (sessionId != null && provider != null) {
                    loadSessionDetail(sessionId, provider)
                } else {
                    LOG.warn("Missing required params for session-detail: id=$sessionId, provider=$provider")
                }
            }
            de.espend.ml.llm.session.util.JsHandlers.Actions.GO_BACK -> {
                loadSessions()
            }
            else -> {
                LOG.warn("Unknown action: ${route.action}")
            }
        }
    }

    fun dispose() {
        jsQueryRouter.dispose()
        jsQueryCursor.dispose()
        browser.dispose()
    }
}
