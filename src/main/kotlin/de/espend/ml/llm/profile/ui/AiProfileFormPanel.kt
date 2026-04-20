package de.espend.ml.llm.profile.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import de.espend.ml.llm.ModelSelectionDialog
import de.espend.ml.llm.PluginIcons
import de.espend.ml.llm.profile.AiProfileApiType
import de.espend.ml.llm.profile.AiProfileConfig
import de.espend.ml.llm.profile.AiProfilePlatformEndpoint
import de.espend.ml.llm.profile.AiProfilePlatformInfo
import de.espend.ml.llm.profile.AiProfilePlatformRegistry
import de.espend.ml.llm.profile.AiProfileTransport
import de.espend.ml.llm.profile.AiProfileTransportOption
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.KeyStroke
import javax.swing.JOptionPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent

class AiProfileFormPanel(
    private val config: AiProfileConfig
) : JPanel(GridBagLayout()) {

    private companion object {
        private val DIRECT_PLATFORM_IDS = setOf(
            AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE,
            AiProfilePlatformRegistry.PLATFORM_PI_DIRECT,
            AiProfilePlatformRegistry.PLATFORM_GEMINI,
            AiProfilePlatformRegistry.PLATFORM_OPENCODE,
            AiProfilePlatformRegistry.PLATFORM_CURSOR,
            AiProfilePlatformRegistry.PLATFORM_KILO,
            AiProfilePlatformRegistry.PLATFORM_FACTORY_AI
        )
        private val COMPATIBLE_PLATFORM_IDS = setOf(
            AiProfilePlatformRegistry.PLATFORM_ANTHROPIC_COMPATIBLE,
            AiProfilePlatformRegistry.PLATFORM_OPENAI_COMPATIBLE
        )
    }

    val enabledCheckBox = JCheckBox("Profile enabled", config.isEnabled)
    val nameField = JBTextField(config.name)

    private var selectedPlatformInfo: AiProfilePlatformInfo =
        AiProfilePlatformRegistry.findPlatform(config.platform) ?: AiProfilePlatformRegistry.platforms.first()

    private val platformComboBox = ComboBox<AiProfilePlatformInfo>()
    private val transportComboBox = JComboBox<AiProfileTransportOption>()
    private val claudeExecutableField = JBTextField(config.claudeCodeExecutable).apply {
        emptyText.setText("Built-in binary used")
    }
    private val providerPanel = JPanel(GridBagLayout())
    private val contentPanel = JPanel(GridBagLayout())

    private var claudeExecutableRowLabel: JBLabel? = null
    private var claudeExecutableRowField: JPanel? = null
    private var claudeExecutableHint: JBLabel? = null
    private var transportRowLabel: JBLabel? = null
    private var transportRowField: JComboBox<AiProfileTransportOption>? = null
    private var apiKeyField: JBTextField? = null
    private var baseUrlField: JBTextField? = null
    private var modelField: JBTextField? = null

    init {
        configureBaseLayout()
        bindInitialState()
        rebuildDynamicFields()
        updateExecutableVisibility()
        updateTransportVisibility()
    }

    fun applyTo(target: AiProfileConfig) {
        target.name = nameField.text.trim()
        target.isEnabled = enabledCheckBox.isSelected
        target.platform = selectedPlatform().id
        target.apiType = selectedTransportOption().apiType?.id.orEmpty()
        target.transport = selectedTransportOption().transport.id
        target.claudeCodeExecutable = claudeExecutableField.text.trim()
        target.apiKey = apiKeyField?.text?.trim().orEmpty()
        target.baseUrl = baseUrlField?.text?.trim().orEmpty()
        target.model = normalizeModelValue(modelField?.text)
    }

    fun showDialog(parentComponent: Component): Boolean {
        contentPanel.revalidate()

        val defaultHeight = JBUI.scale(400)
        val minWidth = JBUI.scale(560)

        val scrollPane = object : JBScrollPane(contentPanel) {
            override fun getPreferredSize(): Dimension {
                val contentHeight = contentPanel.preferredSize.height + JBUI.scale(4)
                return Dimension(minWidth, maxOf(defaultHeight, minOf(contentHeight, defaultHeight)))
            }
        }.apply {
            border = null
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }

        val okButton = JButton("OK")
        val cancelButton = JButton("Cancel")
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), JBUI.scale(4))).apply {
            add(okButton)
            add(cancelButton)
        }

        val mainPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(scrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        val owner = SwingUtilities.getWindowAncestor(parentComponent)
        val dialog = JDialog(owner, "AI Profile", Dialog.ModalityType.APPLICATION_MODAL)
        dialog.contentPane = mainPanel
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.rootPane.defaultButton = okButton
        dialog.rootPane.registerKeyboardAction(
            { dialog.dispose() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            WHEN_IN_FOCUSED_WINDOW
        )

        var confirmed = false
        okButton.addActionListener {
            if (nameField.text.trim().isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Profile name is required.", "AI Profile", JOptionPane.ERROR_MESSAGE)
                return@addActionListener
            }
            confirmed = true
            dialog.dispose()
        }
        cancelButton.addActionListener { dialog.dispose() }

        dialog.pack()
        dialog.setLocationRelativeTo(parentComponent)
        dialog.isVisible = true

        return confirmed
    }

    private fun configureBaseLayout() {
        val smallFont = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        enabledCheckBox.font = smallFont
        nameField.font = smallFont
        claudeExecutableField.font = smallFont
        configurePlatformComboBox(smallFont)
        configureTransportComboBox(smallFont)

        var row = 0

        contentPanel.add(createSectionHeader("Profile"), GridBagConstraints().apply {
            gridx = 0
            gridy = row++
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
        })

        contentPanel.add(enabledCheckBox, GridBagConstraints().apply {
            gridx = 0
            gridy = row++
            gridwidth = 2
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(6, 24, 2, 2)
        })

        addLabeledRow("Name", nameField, row++, smallFont)
        addLabeledRow("Platform", platformComboBox, row++, smallFont)
        addLabeledRow("ACP", transportComboBox, row++, smallFont).also {
            transportRowLabel = it.first
            transportRowField = it.second
        }

        val executablePanel = createExecutablePanel()
        claudeExecutableRowLabel = JBLabel("Executable").apply { font = smallFont }
        claudeExecutableRowField = executablePanel.first
        claudeExecutableHint = executablePanel.second
        contentPanel.add(claudeExecutableRowLabel, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2, 24, 2, 8)
        })
        contentPanel.add(executablePanel.first, GridBagConstraints().apply {
            gridx = 1
            gridy = row++
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(2)
        })
        contentPanel.add(executablePanel.second, GridBagConstraints().apply {
            gridx = 1
            gridy = row++
            weightx = 1.0
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(0, 2, 2, 2)
        })

        contentPanel.add(createSectionHeader("Settings"), GridBagConstraints().apply {
            gridx = 0
            gridy = row++
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insetsTop(10)
        })

        contentPanel.add(providerPanel, GridBagConstraints().apply {
            gridx = 0
            gridy = row++
            gridwidth = 2
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(6, 24, 2, 2)
        })

        contentPanel.add(JPanel(), GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weighty = 1.0
            fill = GridBagConstraints.VERTICAL
        })

        transportComboBox.addActionListener {
            rebuildDynamicFields()
            updateExecutableVisibility()
            updateTransportVisibility()
        }
    }

    private fun configurePlatformComboBox(font: Font) {
        platformComboBox.font = font
        platformComboBox.setSwingPopup(false)
        platformComboBox.maximumRowCount = 18
        platformComboBox.renderer = createPlatformComboRenderer()
        platformComboBox.removeAllItems()
        orderedPlatforms().forEach(platformComboBox::addItem)
        platformComboBox.selectedItem = selectedPlatformInfo

        platformComboBox.addActionListener {
            val selected = platformComboBox.selectedItem as? AiProfilePlatformInfo ?: return@addActionListener
            if (selectedPlatformInfo.id != selected.id) {
                selectedPlatformInfo = selected
                rebuildTransportChoices()
                rebuildDynamicFields()
                updateExecutableVisibility()
                updateTransportVisibility()
            }
        }
    }

    private fun configureTransportComboBox(font: Font) {
        transportComboBox.font = font
        transportComboBox.renderer = JBLabel().let { template ->
            javax.swing.ListCellRenderer<AiProfileTransportOption> { list, value, index, isSelected, _ ->
                val label = JBLabel(value?.label.orEmpty()).apply {
                    this.font = template.font
                    icon = value?.transport?.let(::transportIcon)
                    iconTextGap = JBUI.scale(6)
                    border = JBUI.Borders.empty(4, 8)
                    isOpaque = true
                    background = if (isSelected) list.selectionBackground else list.background
                    foreground = if (isSelected) list.selectionForeground else list.foreground
                }
                label
            }
        }
    }

    private fun transportIcon(transport: AiProfileTransport) = when (transport) {
        AiProfileTransport.CLAUDE_ACP -> PluginIcons.scaleIcon(PluginIcons.CLAUDE, 16)
        AiProfileTransport.PI -> PluginIcons.scaleIcon(PluginIcons.PI, 16)
        AiProfileTransport.DROID -> PluginIcons.scaleIcon(PluginIcons.DROID, 16)
        AiProfileTransport.FAST_AGENT -> PluginIcons.scaleIcon(PluginIcons.FAST_AGENT, 16)
        AiProfileTransport.GEMINI -> PluginIcons.scaleIcon(PluginIcons.GEMINI, 16)
        AiProfileTransport.OPENCODE -> PluginIcons.scaleIcon(PluginIcons.OPENCODE, 16)
        AiProfileTransport.CURSOR -> PluginIcons.scaleIcon(PluginIcons.CURSOR, 16)
        AiProfileTransport.KILO -> PluginIcons.scaleIcon(PluginIcons.KILO, 16)
    }

    private fun bindInitialState() {
        rebuildTransportChoices()
    }

    private fun rebuildTransportChoices() {
        val currentTransportId = (transportComboBox.selectedItem as? AiProfileTransportOption)?.transport?.id
            ?: config.effectiveTransport()
        val currentApiTypeId = (transportComboBox.selectedItem as? AiProfileTransportOption)?.apiType?.id
            ?: config.effectiveApiType()
        val options = AiProfilePlatformRegistry.transportOptions(selectedPlatformInfo)

        transportComboBox.removeAllItems()
        options.forEach { transportComboBox.addItem(it) }
        transportComboBox.selectedItem = AiProfilePlatformRegistry.resolveTransportOption(
            selectedPlatformInfo,
            currentTransportId,
            currentApiTypeId
        )
        transportComboBox.isEnabled = options.size > 1
    }

    private fun rebuildDynamicFields() {
        val platform = selectedPlatform()
        val endpoint = selectedEndpoint()
        val apiType = selectedTransportOption().apiType
        val transport = selectedTransport()
        val currentApiKey = apiKeyField?.text ?: config.apiKey
        val currentBaseUrl = baseUrlField?.text ?: config.baseUrl
        val currentModel = normalizeModelValue(modelField?.text ?: config.model)

        providerPanel.removeAll()
        apiKeyField = null
        baseUrlField = null
        modelField = null

        var row = 0

        providerPanel.add(JBLabel(createInfoHint(platform, apiType, endpoint, transport)).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        }, GridBagConstraints().apply {
            gridx = 0
            gridy = row++
            gridwidth = 3
            weightx = 1.0
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insetsBottom(8)
        })

        if (endpoint?.supportsCustomBaseUrl == true) {
            baseUrlField = JBTextField(currentBaseUrl, 24)
            addProviderRow("Base URL", baseUrlField!!, row++)
        } else if (!endpoint?.baseUrl.isNullOrBlank()) {
            providerPanel.add(JBLabel("Base URL").apply {
                font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
            }, GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.NORTHWEST
                insets = JBUI.insets(2, 0, 2, 8)
            })
            providerPanel.add(JBLabel(endpoint.baseUrl.orEmpty()).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
            }, GridBagConstraints().apply {
                gridx = 1
                gridy = row++
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            })
        }

        if (endpoint != null) {
            apiKeyField = JBTextField(currentApiKey, 24)
            addProviderRow("API Key", apiKeyField!!, row++)

            val allowMultipleModels = transport != AiProfileTransport.FAST_AGENT &&
                platform.id != AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE
            val modelInput = JBTextField(currentModel, 24).apply {
                emptyText.setText(platform.defaultModel.ifBlank { "Auto-discovery" })
            }
            modelField = modelInput

            val browseButton = JButton("...").apply {
                minimumSize = Dimension(30, modelInput.preferredSize.height)
                preferredSize = Dimension(30, modelInput.preferredSize.height)
                addActionListener {
                    val modelsUrl =
                        AiProfilePlatformRegistry.getResolvedModelsUrl(platform, endpoint, baseUrlField?.text.orEmpty())
                            ?: return@addActionListener
                    val dialog =
                        ModelSelectionDialog(
                            modelsUrl,
                            apiKeyField?.text?.trim().orEmpty(),
                            normalizeModelValue(modelInput.text),
                            allowMultiple = allowMultipleModels
                        )
                    if (dialog.showAndGet()) {
                        val selection = if (allowMultipleModels) {
                            dialog.selectedModels.joinToString(", ")
                        } else {
                            dialog.selectedModel.orEmpty()
                        }
                        if (selection.isNotEmpty()) {
                            modelInput.text = selection
                        }
                    }
                }
            }
            val modelInputPanel = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
                isOpaque = false
                add(modelInput, BorderLayout.CENTER)
                add(browseButton, BorderLayout.EAST)
            }
            val modelHintLabel = JBLabel(
                if (allowMultipleModels) {
                    "Multiple models can be entered as comma-separated values. Empty entries are ignored."
                } else {
                    "Enter a single model."
                }
            ).apply {
                foreground = UIUtil.getContextHelpForeground()
                font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
            }
            addProviderRow("Models", modelInputPanel, row++)
            addProviderHintRow(modelHintLabel, row++)

            fun updateModelActions() {
                val hasApiKey = apiKeyField?.text?.trim()?.isNotEmpty() == true
                browseButton.isEnabled =
                    hasApiKey && AiProfilePlatformRegistry.getResolvedModelsUrl(platform, endpoint, baseUrlField?.text.orEmpty()) != null
            }

            updateModelActions()
            apiKeyField?.document?.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    updateModelActions()
                }
            })
            baseUrlField?.document?.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    updateModelActions()
                }
            })
        }

        providerPanel.revalidate()
        providerPanel.repaint()
    }

    private fun createInfoHint(
        platform: AiProfilePlatformInfo,
        apiType: AiProfileApiType?,
        endpoint: AiProfilePlatformEndpoint?,
        transport: AiProfileTransport
    ): String {
        AiProfilePlatformRegistry.transportHelpText(platform, transport).takeIf { it.isNotBlank() }?.let { return it }

        return when {
            endpoint == null -> ""
            endpoint.supportsCustomBaseUrl -> "Custom ${(apiType ?: AiProfileApiType.ANTHROPIC).compatibilityLabel} endpoint with configurable Base URL."
            else -> "${platform.label} ${(apiType ?: AiProfileApiType.ANTHROPIC).compatibilityLabel} API."
        }
    }

    private fun createExecutablePanel(): Pair<JPanel, JBLabel> {
        val inputPanel = JPanel(BorderLayout(JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(claudeExecutableField, BorderLayout.CENTER)
            add(JButton("Auto-Detect").apply {
                addActionListener {
                    claudeExecutableField.text = AiProfilePlatformRegistry.autoDetectExecutable(selectedTransport()).orEmpty()
                }
            }, BorderLayout.EAST)
        }

        val hintLabel = JBLabel("").apply {
            foreground = UIUtil.getContextHelpForeground()
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        }

        return inputPanel to hintLabel
    }

    private fun updateExecutableVisibility() {
        val transport = selectedTransport()
        val visible = AiProfilePlatformRegistry.supportsExecutableOverride(transport)
        claudeExecutableRowLabel?.isVisible = visible
        claudeExecutableRowField?.isVisible = visible
        claudeExecutableHint?.isVisible = visible
        claudeExecutableField.emptyText.setText(executablePlaceholder(transport))
        claudeExecutableHint?.text = executableHintText(transport)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun updateTransportVisibility() {
        val visible = selectedPlatform().showTransportSelector
        transportRowLabel?.isVisible = visible
        transportRowField?.isVisible = visible
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun orderedDirectPlatforms(): List<AiProfilePlatformInfo> {
        return listOfNotNull(
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_CLAUDE_CODE),
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_PI_DIRECT),
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_GEMINI),
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_OPENCODE),
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_CURSOR),
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_KILO),
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_FACTORY_AI)
        )
    }

    private fun orderedCompatiblePlatforms(): List<AiProfilePlatformInfo> {
        return listOfNotNull(
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_ANTHROPIC_COMPATIBLE),
            AiProfilePlatformRegistry.findPlatform(AiProfilePlatformRegistry.PLATFORM_OPENAI_COMPATIBLE)
        )
    }

    private fun orderedPlatforms(): List<AiProfilePlatformInfo> {
        return orderedDirectPlatforms() + orderedCompatiblePlatforms() + orderedHostedPlatforms()
    }

    private fun orderedHostedPlatforms(): List<AiProfilePlatformInfo> {
        return AiProfilePlatformRegistry.platforms.filterNot { it.id in DIRECT_PLATFORM_IDS || it.id in COMPATIBLE_PLATFORM_IDS }
    }

    private fun createPlatformComboRenderer(): GroupedComboBoxRenderer<AiProfilePlatformInfo> {
        return object : GroupedComboBoxRenderer<AiProfilePlatformInfo>(platformComboBox) {
            override fun getText(item: AiProfilePlatformInfo): String {
                return item.label
            }

            override fun getIcon(item: AiProfilePlatformInfo) = PluginIcons.scaleIcon(item.icon, 16)

            override fun separatorFor(value: AiProfilePlatformInfo): ListSeparator? {
                return when (value.id) {
                    orderedDirectPlatforms().firstOrNull()?.id -> ListSeparator("Direct")
                    AiProfilePlatformRegistry.PLATFORM_ANTHROPIC_COMPATIBLE -> ListSeparator("Compatible APIs")
                    orderedHostedPlatforms().firstOrNull()?.id -> ListSeparator("Platforms")
                    else -> null
                }
            }
        }
    }

    private fun addProviderRow(label: String, component: Component, row: Int) {
        providerPanel.add(JBLabel(label).apply {
            font = UIUtil.getLabelFont().deriveFont(UIUtil.getLabelFont().size2D - 1f)
        }, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2, 0, 2, 8)
        })
        providerPanel.add(component, GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
        })
    }

    private fun addProviderHintRow(component: Component, row: Int) {
        providerPanel.add(component, GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(0, 0, 2, 0)
        })
    }

    private fun addLabeledRow(label: String, component: Component, row: Int, font: Font): Pair<JBLabel, JComboBox<AiProfileTransportOption>?> {
        val labelComponent = JBLabel(label).apply { this.font = font }
        contentPanel.add(labelComponent, GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(2, 24, 2, 8)
        })
        contentPanel.add(component, GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(2)
        })

        @Suppress("UNCHECKED_CAST")
        return labelComponent to (component as? JComboBox<AiProfileTransportOption>)
    }

    private fun createSectionHeader(title: String): JPanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
        add(JBLabel(title).apply {
            font = UIUtil.getLabelFont().deriveFont(Font.BOLD)
        }, GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
        })
        add(JSeparator(SwingConstants.HORIZONTAL), GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insetsLeft(8)
        })
    }

    private fun selectedPlatform(): AiProfilePlatformInfo = selectedPlatformInfo

    private fun selectedEndpoint(): AiProfilePlatformEndpoint? {
        return AiProfilePlatformRegistry.resolveEndpoint(selectedPlatformInfo, selectedTransportOption().apiType?.id.orEmpty())
    }

    private fun selectedTransportOption(): AiProfileTransportOption {
        return transportComboBox.selectedItem as? AiProfileTransportOption
            ?: AiProfilePlatformRegistry.defaultTransportOption(selectedPlatformInfo.id)
            ?: error("No transport option for platform ${selectedPlatformInfo.id}")
    }

    private fun selectedTransport(): AiProfileTransport {
        return selectedTransportOption().transport
    }

    private fun executablePlaceholder(transport: AiProfileTransport): String {
        return when (transport) {
            AiProfileTransport.CLAUDE_ACP -> "Built-in binary used"
            else -> "Auto-detect ${AiProfilePlatformRegistry.defaultCommand(transport)}"
        }
    }

    private fun executableHintText(transport: AiProfileTransport): String {
        return when (transport) {
            AiProfileTransport.CLAUDE_ACP -> "Leave empty to use the built-in Claude Code binary."
            else -> "Leave empty to auto-detect `${AiProfilePlatformRegistry.defaultCommand(transport)}`."
        }
    }

    private fun normalizeModelValue(value: String?): String {
        return value
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(", ")
            .orEmpty()
    }
}
