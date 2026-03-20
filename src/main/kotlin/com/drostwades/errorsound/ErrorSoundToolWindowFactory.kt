package com.drostwades.errorsound

import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

class ErrorSoundToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = ErrorSoundToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.preferredFocusableComponent = panel.preferredFocus()
        toolWindow.contentManager.addContent(content)
    }
}

private class ErrorSoundToolWindowPanel(
    private val project: Project
) : JPanel(BorderLayout()) {

    private val settings: AlertSettings.State
        get() = AlertSettings.getInstance().state

    private val titleLabel = JBLabel("Error Monitoring").apply {
        font = font.deriveFont(Font.BOLD, font.size2D + 1f)
    }

    private val subtitleLabel = JBLabel("Control which error categories trigger alerts").apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBFont.small()
    }

    private val statusLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBFont.small()
    }

    private val enabledCheckBox = JBCheckBox("Enable monitoring", settings.enabled)

    private val configurationCheckBox = JBCheckBox("Configuration", settings.monitorConfiguration)
    private val compilationCheckBox = JBCheckBox("Compilation", settings.monitorCompilation)
    private val testFailureCheckBox = JBCheckBox("Test failure", settings.monitorTestFailure)
    private val networkCheckBox = JBCheckBox("Network", settings.monitorNetwork)
    private val exceptionCheckBox = JBCheckBox("Exception", settings.monitorException)
    private val genericCheckBox = JBCheckBox("Generic", settings.monitorGeneric)
    private val successCheckBox = JBCheckBox("Success", settings.monitorSuccess)

    private val selectAllLink = ActionLink("Select all") { setAllKinds(true) }
    private val clearAllLink = ActionLink("Clear all") { setAllKinds(false) }

    private val presetAllLink = ActionLink("All") { applyPresetAll() }
    private val presetBuildOnlyLink = ActionLink("Build only") { applyBuildOnlyPreset() }
    private val presetRuntimeOnlyLink = ActionLink("Runtime only") { applyRuntimeOnlyPreset() }

    private val openSettingsButton = JButton("Open sound settings").apply {
        addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, ErrorSoundConfigurable::class.java)
        }
    }

    private val typeRows = listOf(
        KindRow(
            ErrorKind.CONFIGURATION,
            configurationCheckBox,
            "Startup, property, bean, or app configuration failures"
        ),
        KindRow(
            ErrorKind.COMPILATION,
            compilationCheckBox,
            "Compiler, Gradle, Maven, or build-time errors"
        ),
        KindRow(
            ErrorKind.TEST_FAILURE,
            testFailureCheckBox,
            "Failed tests, assertions, and verification issues"
        ),
        KindRow(
            ErrorKind.NETWORK,
            networkCheckBox,
            "Timeouts, DNS failures, refused connections"
        ),
        KindRow(
            ErrorKind.EXCEPTION,
            exceptionCheckBox,
            "Runtime exceptions, stack traces, and caused-by chains"
        ),
        KindRow(
            ErrorKind.GENERIC,
            genericCheckBox,
            "Other failures that do not match a specific category"
        )
    )

    private val successRow = KindRow(
        ErrorKind.SUCCESS,
        successCheckBox,
        "Successful process completions (exit code 0)"
    )

    init {
        border = JBUI.Borders.empty()

        val scrollPane = JBScrollPane(buildContent()).apply {
            border = JBUI.Borders.empty()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(scrollPane, BorderLayout.CENTER)

        bindEvents()
        refreshUiState()
    }

    fun preferredFocus(): JComponent = enabledCheckBox

    private fun buildContent(): JComponent {
        val column = JPanel()
        column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
        column.border = JBUI.Borders.empty(10, 12, 12, 12)
        column.isOpaque = false

        column.add(compact(buildHeader()))
        column.add(Box.createVerticalStrut(8))

        column.add(compact(enabledCheckBox))
        column.add(Box.createVerticalStrut(10))

        column.add(compact(TitledSeparator("Error Types")))
        column.add(Box.createVerticalStrut(6))

        typeRows.forEachIndexed { index, row ->
            column.add(compact(createTypeRow(row)))
            if (index != typeRows.lastIndex) {
                column.add(Box.createVerticalStrut(4))
            }
        }

        column.add(Box.createVerticalStrut(8))
        column.add(compact(TitledSeparator("Success")))
        column.add(Box.createVerticalStrut(6))
        column.add(compact(createTypeRow(successRow)))

        column.add(Box.createVerticalStrut(8))
        column.add(compact(buildQuickActions()))

        column.add(Box.createVerticalStrut(6))
        column.add(compact(buildPresets()))

        column.add(Box.createVerticalStrut(10))
        column.add(
            compact(
                JBLabel("Sound selection and preview stay in plugin settings.").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = JBFont.small()
                }
            )
        )

        column.add(Box.createVerticalStrut(8))
        column.add(compact(openSettingsButton))

        // Wrap in BorderLayout.NORTH so the column pins to the top
        // and does not stretch to fill the scroll pane viewport.
        val wrapper = JPanel(BorderLayout())
        wrapper.isOpaque = false
        wrapper.add(column, BorderLayout.NORTH)
        return wrapper
    }

    private fun buildHeader(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false

        panel.add(left(titleLabel))
        panel.add(Box.createVerticalStrut(1))
        panel.add(left(subtitleLabel))
        panel.add(Box.createVerticalStrut(3))
        panel.add(left(statusLabel))

        return panel
    }

    private fun buildQuickActions(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false

        panel.add(selectAllLink)
        panel.add(spacerDot())
        panel.add(clearAllLink)

        return panel
    }

    private fun buildPresets(): JComponent {
        val wrapper = JPanel()
        wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
        wrapper.isOpaque = false

        val label = JBLabel("Presets").apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
        }

        val row = JPanel()
        row.layout = BoxLayout(row, BoxLayout.X_AXIS)
        row.isOpaque = false

        row.add(presetAllLink)
        row.add(spacerDot())
        row.add(presetBuildOnlyLink)
        row.add(spacerDot())
        row.add(presetRuntimeOnlyLink)

        wrapper.add(left(label))
        wrapper.add(Box.createVerticalStrut(2))
        wrapper.add(left(row))

        return wrapper
    }

    private fun spacerDot(): JComponent {
        return JBLabel("  •  ").apply {
            foreground = UIUtil.getContextHelpForeground()
        }
    }

    private fun createTypeRow(row: KindRow): JComponent {
        val wrapper = JPanel()
        wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
        wrapper.isOpaque = false
        wrapper.border = JBUI.Borders.empty(0, 0, 0, 0)

        row.checkBox.border = JBUI.Borders.empty()
        row.checkBox.alignmentX = Component.LEFT_ALIGNMENT

        val description = JBLabel(row.description).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
            border = JBUI.Borders.emptyLeft(22)
            alignmentX = Component.LEFT_ALIGNMENT
        }

        wrapper.add(row.checkBox)
        wrapper.add(Box.createVerticalStrut(1))
        wrapper.add(description)

        return wrapper
    }

    private fun bindEvents() {
        enabledCheckBox.addActionListener {
            settings.enabled = enabledCheckBox.isSelected
            refreshUiState()
        }

        typeRows.forEach { row ->
            row.checkBox.addActionListener {
                AlertMonitoring.setKindEnabled(settings, row.kind, row.checkBox.isSelected)
                refreshUiState()
            }
        }

        successRow.checkBox.addActionListener {
            AlertMonitoring.setKindEnabled(settings, successRow.kind, successRow.checkBox.isSelected)
            refreshUiState()
        }
    }

    private fun setAllKinds(enabled: Boolean) {
        typeRows.forEach { row ->
            row.checkBox.isSelected = enabled
            AlertMonitoring.setKindEnabled(settings, row.kind, enabled)
        }
        refreshUiState()
    }

    private fun setKind(kind: ErrorKind, enabled: Boolean) {
        val row = typeRows.firstOrNull { it.kind == kind } ?: return
        row.checkBox.isSelected = enabled
        AlertMonitoring.setKindEnabled(settings, kind, enabled)
    }

    private fun applyPresetAll() {
        setAllKinds(true)
    }

    private fun applyBuildOnlyPreset() {
        setKind(ErrorKind.CONFIGURATION, true)
        setKind(ErrorKind.COMPILATION, true)
        setKind(ErrorKind.TEST_FAILURE, true)
        setKind(ErrorKind.NETWORK, false)
        setKind(ErrorKind.EXCEPTION, false)
        setKind(ErrorKind.GENERIC, false)
        refreshUiState()
    }

    private fun applyRuntimeOnlyPreset() {
        setKind(ErrorKind.CONFIGURATION, false)
        setKind(ErrorKind.COMPILATION, false)
        setKind(ErrorKind.TEST_FAILURE, false)
        setKind(ErrorKind.NETWORK, true)
        setKind(ErrorKind.EXCEPTION, true)
        setKind(ErrorKind.GENERIC, true)
        refreshUiState()
    }

    private fun refreshUiState() {
        val monitoringEnabled = settings.enabled
        val enabledCount = typeRows.count { it.checkBox.isSelected }
        val successEnabled = successRow.checkBox.isSelected

        statusLabel.text = if (monitoringEnabled) {
            val parts = mutableListOf<String>()
            parts.add("$enabledCount error")
            if (successEnabled) parts.add("success")
            "Active · ${parts.joinToString(" + ")} enabled"
        } else {
            "Paused"
        }

        statusLabel.foreground = if (monitoringEnabled) {
            UIUtil.getContextHelpForeground()
        } else {
            UIUtil.getInactiveTextColor()
        }

        typeRows.forEach { row ->
            row.checkBox.isEnabled = monitoringEnabled
        }
        successRow.checkBox.isEnabled = monitoringEnabled

        selectAllLink.isEnabled = monitoringEnabled
        clearAllLink.isEnabled = monitoringEnabled
        presetAllLink.isEnabled = monitoringEnabled
        presetBuildOnlyLink.isEnabled = monitoringEnabled
        presetRuntimeOnlyLink.isEnabled = monitoringEnabled
    }

    private fun <T : JComponent> compact(component: T): T {
        component.alignmentX = Component.LEFT_ALIGNMENT
        val preferred = component.preferredSize
        component.maximumSize = Dimension(Int.MAX_VALUE, preferred.height)
        return component
    }

    private fun <T : JComponent> left(component: T): T {
        component.alignmentX = Component.LEFT_ALIGNMENT
        return component
    }

    private data class KindRow(
        val kind: ErrorKind,
        val checkBox: JBCheckBox,
        val description: String,
    )
}