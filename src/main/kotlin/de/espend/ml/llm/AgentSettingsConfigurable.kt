package de.espend.ml.llm

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.options.Configurable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import javax.swing.event.DocumentEvent

/**
 * Settings Configurable for AI provider agents.
 * UI for providers: anthropic-default, anthropic-compatible, z.ai, minimax, openrouter, mimo.
 */
class AgentSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private val providerPanels = mutableMapOf<String, ProviderPanel>()
    private val claudeExecutableField: JBTextField = JBTextField().apply {
        emptyText.setText("Built-in binary used")
    }

    override fun getDisplayName(): String = "Agent Providers"

    override fun createComponent(): JComponent {
        mainPanel = JPanel(GridBagLayout())

        val registry = AgentRegistry.getInstance()

        // Settings section
        val settingsPanel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(10, 0)

            // Section header
            val settingsHeader = JPanel(GridBagLayout()).apply {
                val settingsLabel = JBLabel("Settings").apply {
                    font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
                }
                add(settingsLabel, GridBagConstraints().apply {
                    gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                })
                add(JSeparator(SwingConstants.HORIZONTAL), GridBagConstraints().apply {
                    gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsLeft(10)
                })
            }
            add(settingsHeader, GridBagConstraints().apply {
                gridx = 0; gridy = 0; gridwidth = 3; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })

            // Settings content panel with left margin
            val settingsContent = JPanel(GridBagLayout()).apply {
                // Claude Code Executable row
                val claudeLabel = JBLabel("Claude Code Executable:").apply {
                    font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
                }
                add(claudeLabel, GridBagConstraints().apply {
                    gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsTop(2)
                })

                claudeExecutableField.text = registry.state.claudeCodeExecutable ?: ""
                add(claudeExecutableField, GridBagConstraints().apply {
                    gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(2, 5)
                })

                val claudeDetectButton = JButton("Auto-Detect").apply {
                    addActionListener {
                        val detectedPath = CommandPathUtils.findClaudePath()
                        if (detectedPath != null) {
                            claudeExecutableField.text = detectedPath
                        } else {
                            claudeExecutableField.text = ""
                        }
                    }
                }
                add(claudeDetectButton, GridBagConstraints().apply {
                    gridx = 2; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insets(2, 2, 2, 0)
                })

                // Filler
                add(Box.createHorizontalGlue(), GridBagConstraints().apply {
                    gridx = 3; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                })

                // Small description text below
                val helpLabel = JBLabel("@zed-industries/claude-code-acp provides a built-in claude binary (may be outdated). Override if needed.").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
                }
                add(helpLabel, GridBagConstraints().apply {
                    gridx = 0; gridy = 1; gridwidth = 4; anchor = GridBagConstraints.WEST; insets = JBUI.insetsTop(3)
                })
            }
            add(settingsContent, GridBagConstraints().apply {
                gridx = 0; gridy = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsLeft(24)
            })
        }
        mainPanel!!.add(settingsPanel, GridBagConstraints().apply {
            gridx = 0; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
        })

        // Providers section header
        val providersHeader = JPanel(GridBagLayout()).apply {
            val providersLabel = JBLabel("Providers").apply {
                font = UIUtil.getLabelFont().deriveFont(java.awt.Font.BOLD)
            }
            add(providersLabel, GridBagConstraints().apply {
                gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
            })
            add(JSeparator(SwingConstants.HORIZONTAL), GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsLeft(10)
            })
        }
        mainPanel!!.add(providersHeader, GridBagConstraints().apply {
            gridx = 0; gridy = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsTop(10)
        })

        ProviderConfig.PROVIDERS.forEachIndexed { index, provider ->
            val existingConfig = registry.agentConfigs.find { it.provider == provider }
            val panel = ProviderPanel(provider, existingConfig)
            providerPanels[provider] = panel

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = index + 2  // +2 because settings panel and providers header are at rows 0-1
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insetsTop(2)
            }
            mainPanel!!.add(panel, gbc)
        }

        // Filler
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = ProviderConfig.PROVIDERS.size + 2  // +2 because settings panel and providers header are at rows 0-1
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        mainPanel!!.add(JPanel(), gbc)

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val registry = AgentRegistry.getInstance()

        // Check if Claude executable path changed
        val currentClaude = claudeExecutableField.text.trim().ifEmpty { null }
        if (registry.state.claudeCodeExecutable != currentClaude) return true

        for (provider in ProviderConfig.PROVIDERS) {
            val panel = providerPanels[provider] ?: continue
            val existingConfig = registry.agentConfigs.find { it.provider == provider }
            val panelConfig = panel.getConfig()

            if (existingConfig == null && panelConfig.isEnabled) return true
            if (existingConfig != null && existingConfig != panelConfig) return true
        }
        return false
    }

    override fun apply() {
        val registry = AgentRegistry.getInstance()

        // Save Claude Code executable path
        registry.state.claudeCodeExecutable = claudeExecutableField.text.trim().ifEmpty { null }

        // Remove all old agents
        registry.agentConfigs.forEach { registry.removeAgent(it.id) }

        // Register new agents from the panels
        for (provider in ProviderConfig.PROVIDERS) {
            val panel = providerPanels[provider] ?: continue
            val config = panel.getConfig()
            if (config.isEnabled) {
                registry.addAgent(config)
            }
        }
    }

    override fun reset() {
        val registry = AgentRegistry.getInstance()

        // Reset Claude executable path
        claudeExecutableField.text = registry.state.claudeCodeExecutable ?: ""

        for (provider in ProviderConfig.PROVIDERS) {
            val panel = providerPanels[provider] ?: continue
            val existingConfig = registry.agentConfigs.find { it.provider == provider }
            panel.loadConfig(existingConfig)
        }
    }

    override fun disposeUIResources() {
        mainPanel = null
        providerPanels.clear()
    }

    /**
     * Panel for a single provider.
     */
    private class ProviderPanel(
        private val provider: String,
        initialConfig: AgentConfig?
    ) : JPanel(GridBagLayout()) {

        private val enabledCheckbox: JBCheckBox
        private val apiKeyField: JBTextField?
        private val baseUrlField: JBTextField?
        private var modelField: JBTextField? = null
        private val descriptionArea: JTextArea?
        private val inputsPanel: JPanel
        private var executableField: JBTextField? = null

        init {
            val providerInfo = ProviderConfig.PROVIDER_INFOS[provider]
            val providerName = providerInfo?.label ?: provider
            val providerIcon = providerInfo?.icon?.let { PluginIcons.scaleIcon(it, 16) }

            // Helper to create register link
            fun createRegisterLink(url: String): ActionLink {
                return ActionLink("Register") { BrowserUtil.browse(url) }.apply {
                    setExternalLinkIcon()
                }
            }

            // Create header with checkbox, icon, and text
            enabledCheckbox = JBCheckBox("", initialConfig?.isEnabled ?: false)
            val headerPanel = JPanel(GridBagLayout()).apply {
                add(enabledCheckbox, GridBagConstraints().apply {
                    gridx = 0; gridy = 0; anchor = GridBagConstraints.WEST
                })
                if (providerIcon != null) {
                    add(JBLabel(providerIcon).apply {
                        setIconTextGap(10)
                        cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                        addMouseListener(object : java.awt.event.MouseAdapter() {
                            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                                enabledCheckbox.isSelected = !enabledCheckbox.isSelected
                                updateInputsVisibility()
                            }
                        })
                    }, GridBagConstraints().apply {
                        gridx = 1; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsLeft(5)
                    })
                }
                add(JBLabel(providerName).apply {
                    cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
                    addMouseListener(object : java.awt.event.MouseAdapter() {
                        override fun mouseClicked(e: java.awt.event.MouseEvent) {
                            enabledCheckbox.isSelected = !enabledCheckbox.isSelected
                            updateInputsVisibility()
                        }
                    })
                }, GridBagConstraints().apply {
                    gridx = 2; gridy = 0; anchor = GridBagConstraints.WEST; insets = JBUI.insetsLeft(5)
                })
                // Filler to keep header left-aligned
                add(Box.createHorizontalGlue(), GridBagConstraints().apply {
                    gridx = 3; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
                })
            }
            add(headerPanel, GridBagConstraints().apply {
                gridx = 0; gridy = 0; weightx = 0.0
                anchor = GridBagConstraints.WEST; fill = GridBagConstraints.NONE
                insets = JBUI.insets(10, 0, 5, 0)
            })

            // Filler to keep content left-aligned even when inputs are hidden
            add(Box.createHorizontalGlue(), GridBagConstraints().apply {
                gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })

            val labelInsets = JBUI.insetsTop(2)
            val fieldInsets = JBUI.insets(2, 0, 2, 5)

            inputsPanel = JPanel(GridBagLayout())

            when (provider) {
                ProviderConfig.PROVIDER_ANTHROPIC_DEFAULT -> {
                    // Anthropic Default: Show install command only
                    apiKeyField = null
                    baseUrlField = null
                    val installText = "Uses native Claude CLI via npm install -g @zed-industries/claude-code-acp"
                    descriptionArea = JTextArea(installText).apply {
                        isEditable = false
                        wrapStyleWord = true
                        lineWrap = true
                        background = this@ProviderPanel.background
                        font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
                        foreground = UIUtil.getContextHelpForeground()
                    }
                    inputsPanel.add(descriptionArea, GridBagConstraints().apply {
                        gridx = 0; gridy = 0; weightx = 1.0
                        anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = labelInsets
                    })
                }
                ProviderConfig.PROVIDER_ANTHROPIC_COMPATIBLE -> {
                    // Anthropic Compatible: API key, Base URL, Model fields + install command
                    apiKeyField = JBTextField(initialConfig?.apiKey ?: "", 20)
                    baseUrlField = JBTextField(initialConfig?.baseUrl ?: "", 20)
                    val modelField = JBTextField(initialConfig?.model ?: "", 20)
                    descriptionArea = null

                    // Install command label
                    val installLabel = JBLabel("Supports any Anthropic-like API via npm install -g @zed-industries/claude-code-acp").apply {
                        foreground = UIUtil.getContextHelpForeground()
                        font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
                    }
                    inputsPanel.add(installLabel, GridBagConstraints().apply {
                        gridx = 0; gridy = 0; gridwidth = 3; weightx = 1.0
                        anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsBottom(5)
                    })

                    // Base URL
                    inputsPanel.add(JBLabel("Base URL:"), GridBagConstraints().apply {
                        gridx = 0; gridy = 1; anchor = GridBagConstraints.WEST; insets = labelInsets
                    })
                    inputsPanel.add(baseUrlField, GridBagConstraints().apply {
                        gridx = 1; gridy = 1; weightx = 1.0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = fieldInsets
                    })

                    // API Key
                    inputsPanel.add(JBLabel("API Key:"), GridBagConstraints().apply {
                        gridx = 0; gridy = 2; anchor = GridBagConstraints.WEST; insets = labelInsets
                    })
                    inputsPanel.add(apiKeyField, GridBagConstraints().apply {
                        gridx = 1; gridy = 2; weightx = 1.0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = fieldInsets
                    })

                    // Model with browse button
                    val browseButton = JButton("...").apply {
                        preferredSize = Dimension(30, modelField.preferredSize.height)
                        addActionListener {
                            val baseUrl = baseUrlField.text.trim()
                            val apiKey = apiKeyField.text.trim()
                            if (baseUrl.isNotEmpty()) {
                                val dialog = ModelSelectionDialog("${baseUrl.trimEnd('/')}/v1/models", apiKey, modelField.text.trim())
                                if (dialog.showAndGet()) {
                                    dialog.selectedModel?.let { modelField.text = it }
                                }
                            }
                        }
                    }

                    // Disable model controls when no API key
                    fun updateModelEnabled() {
                        val hasKey = apiKeyField.text.trim().isNotEmpty()
                        modelField.isEnabled = hasKey
                        browseButton.isEnabled = hasKey
                    }
                    updateModelEnabled()
                    apiKeyField.document.addDocumentListener(object : DocumentAdapter() {
                        override fun textChanged(e: DocumentEvent) {
                            updateModelEnabled()
                        }
                    })

                    inputsPanel.add(JBLabel("Model:"), GridBagConstraints().apply {
                        gridx = 0; gridy = 3; anchor = GridBagConstraints.WEST; insets = labelInsets
                    })
                    inputsPanel.add(modelField, GridBagConstraints().apply {
                        gridx = 1; gridy = 3; weightx = 1.0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = fieldInsets
                    })
                    inputsPanel.add(browseButton, GridBagConstraints().apply {
                        gridx = 2; gridy = 3; anchor = GridBagConstraints.WEST; insets = JBUI.insets(2, 2, 2, 5)
                    })

                    // Store modelField reference for getConfig()
                    this.modelField = modelField
                }
                ProviderConfig.PROVIDER_GEMINI,
                ProviderConfig.PROVIDER_OPENCODE,
                ProviderConfig.PROVIDER_CURSOR,
                ProviderConfig.PROVIDER_DROID -> {
                    // Description and executable field for these providers
                    apiKeyField = null
                    baseUrlField = null
                    descriptionArea = null

                    // Description label (like installLabel in Anthropic Compatible)
                    val descLabel = JBLabel(providerInfo?.description ?: "").apply {
                        foreground = UIUtil.getContextHelpForeground()
                        font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
                    }
                    inputsPanel.add(descLabel, GridBagConstraints().apply {
                        gridx = 0; gridy = 0; gridwidth = 3; weightx = 1.0
                        anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsBottom(5)
                    })

                    // Executable label
                    inputsPanel.add(JBLabel("Executable:"), GridBagConstraints().apply {
                        gridx = 0; gridy = 1; anchor = GridBagConstraints.WEST; insets = labelInsets
                    })

                    // Executable field
                    val commandName = when (provider) {
                        ProviderConfig.PROVIDER_GEMINI -> "gemini"
                        ProviderConfig.PROVIDER_OPENCODE -> "opencode"
                        ProviderConfig.PROVIDER_CURSOR -> "cursor-agent-acp"
                        ProviderConfig.PROVIDER_DROID -> "droid"
                        else -> provider
                    }
                    executableField = JBTextField(initialConfig?.executable ?: "", 20).apply {
                        emptyText.setText("Auto-detection ($commandName)")
                    }
                    inputsPanel.add(executableField!!, GridBagConstraints().apply {
                        gridx = 1; gridy = 1; weightx = 1.0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = fieldInsets
                    })

                    // Auto-detect button
                    val autoDetectButton = JButton("Auto-Detect").apply {
                        addActionListener {
                            val detected = when (provider) {
                                ProviderConfig.PROVIDER_GEMINI -> CommandPathUtils.findGeminiPath()
                                ProviderConfig.PROVIDER_OPENCODE -> CommandPathUtils.findOpenCodePath()
                                ProviderConfig.PROVIDER_CURSOR -> CommandPathUtils.findCursorAgentAcpPath()
                                ProviderConfig.PROVIDER_DROID -> CommandPathUtils.findDroidPath()
                                else -> null
                            }
                            executableField?.text = detected ?: ""
                        }
                    }
                    inputsPanel.add(autoDetectButton, GridBagConstraints().apply {
                        gridx = 2; gridy = 1; anchor = GridBagConstraints.WEST; insets = JBUI.insets(2, 2, 2, 5)
                    })
                }
                else -> {
                    // Standard providers: use ProviderInfo for metadata
                    apiKeyField = JBTextField(initialConfig?.apiKey ?: "", 20)
                    baseUrlField = null
                    descriptionArea = null

                    val fullDesc = providerInfo?.description
                        ?: "$providerName via Anthropic Compatible API. npm install -g @zed-industries/claude-code-acp"
                    val descLabel = JBLabel(fullDesc).apply {
                        foreground = UIUtil.getContextHelpForeground()
                        font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
                    }
                    inputsPanel.add(descLabel, GridBagConstraints().apply {
                        gridx = 0; gridy = 0; gridwidth = 3; weightx = 1.0
                        anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insetsBottom(5)
                    })

                    inputsPanel.add(JBLabel("API Key:"), GridBagConstraints().apply {
                        gridx = 0; gridy = 1; anchor = GridBagConstraints.WEST; insets = labelInsets
                    })
                    inputsPanel.add(apiKeyField, GridBagConstraints().apply {
                        gridx = 1; gridy = 1; weightx = 1.0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = fieldInsets
                    })

                    // Model field for all standard providers
                    val modelField = JBTextField(initialConfig?.model ?: "", 20).apply {
                        emptyText.setText(providerInfo?.autoDiscoveryText ?: "Auto-discovery")
                    }
                    this.modelField = modelField

                    inputsPanel.add(JBLabel("Model:"), GridBagConstraints().apply {
                        gridx = 0; gridy = 2; anchor = GridBagConstraints.WEST; insets = labelInsets
                    })
                    inputsPanel.add(modelField, GridBagConstraints().apply {
                        gridx = 1; gridy = 2; weightx = 1.0; anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = fieldInsets
                    })

                    // Browse button only for providers with a models URL
                    val modelsUrl = providerInfo?.modelsUrl
                    if (modelsUrl != null) {
                        val browseButton = JButton("...").apply {
                            preferredSize = Dimension(30, modelField.preferredSize.height)
                            addActionListener {
                                val apiKey = apiKeyField.text.trim()
                                val dialog = ModelSelectionDialog(modelsUrl, apiKey, modelField.text.trim())
                                if (dialog.showAndGet()) {
                                    dialog.selectedModel?.let { modelField.text = it }
                                }
                            }
                        }

                        // Disable model controls when no API key
                        fun updateModelEnabled() {
                            val hasKey = apiKeyField.text.trim().isNotEmpty()
                            modelField.isEnabled = hasKey
                            browseButton.isEnabled = hasKey
                        }
                        updateModelEnabled()
                        apiKeyField.document.addDocumentListener(object : DocumentAdapter() {
                            override fun textChanged(e: DocumentEvent) {
                                updateModelEnabled()
                            }
                        })

                        inputsPanel.add(browseButton, GridBagConstraints().apply {
                            gridx = 2; gridy = 2; anchor = GridBagConstraints.WEST; insets = JBUI.insets(2, 2, 2, 5)
                        })
                    }
                }
            }

            // Add register link if provider has one
            providerInfo?.registerUrl?.let { url ->
                inputsPanel.add(createRegisterLink(url), GridBagConstraints().apply {
                    gridx = 0; gridy = 99; anchor = GridBagConstraints.WEST; insets = JBUI.insetsTop(8)
                })
            }

            // Filler to push content left
            inputsPanel.add(Box.createHorizontalGlue(), GridBagConstraints().apply {
                gridx = 3; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            })

            // Inputs container (hidden when disabled) - with left margin for nested content
            add(inputsPanel, GridBagConstraints().apply {
                gridx = 0; gridy = 1; weightx = 1.0
                anchor = GridBagConstraints.WEST; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(2, 24, 2, 0)
            })

            // Toggle visibility
            updateInputsVisibility()
            enabledCheckbox.addActionListener { updateInputsVisibility() }
        }

        fun updateInputsVisibility() {
            inputsPanel.isVisible = enabledCheckbox.isSelected
        }

        fun getConfig(): AgentConfig {
            return AgentConfig(
                id = provider,
                provider = provider,
                apiKey = apiKeyField?.text?.trim() ?: "",
                baseUrl = baseUrlField?.text?.trim() ?: "",
                model = modelField?.text?.trim() ?: "",
                isEnabled = enabledCheckbox.isSelected,
                executable = executableField?.text?.trim() ?: ""
            )
        }

        fun loadConfig(config: AgentConfig?) {
            apiKeyField?.text = config?.apiKey ?: ""
            baseUrlField?.text = config?.baseUrl ?: ""
            modelField?.text = config?.model ?: ""
            executableField?.text = config?.executable ?: ""
            enabledCheckbox.isSelected = config?.isEnabled ?: false
            updateInputsVisibility()
        }
    }
}
