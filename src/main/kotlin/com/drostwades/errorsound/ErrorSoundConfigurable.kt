package com.drostwades.errorsound

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.JBColor
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBSlider
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.util.regex.PatternSyntaxException
import javax.swing.BorderFactory
import javax.swing.DefaultCellEditor
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class ErrorSoundConfigurable : Configurable {

    private val settings: AlertSettings
        get() = AlertSettings.getInstance()

    private var panel: JPanel? = null
    private var suppressPreview = false

    private val enabledCheck = JBCheckBox("Enable alert sound for failed Run/Debug processes")
    private val sourceCombo = ComboBox(AlertSettings.SoundSource.entries.toTypedArray())
    private val builtInSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())
    private val useGlobalBuiltInCheck = JBCheckBox("Use one built-in sound for all error kinds")

    private val configurationEnabledCheck = JBCheckBox("Enable")
    private val configurationSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())

    private val compilationEnabledCheck = JBCheckBox("Enable")
    private val compilationSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())

    private val testFailureEnabledCheck = JBCheckBox("Enable")
    private val testFailureSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())

    private val networkEnabledCheck = JBCheckBox("Enable")
    private val networkSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())

    private val exceptionEnabledCheck = JBCheckBox("Enable")
    private val exceptionSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())

    private val genericEnabledCheck = JBCheckBox("Enable")
    private val genericSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())

    private val successEnabledCheck = JBCheckBox("Enable")
    private val successSoundCombo = ComboBox(BuiltInSounds.all.toTypedArray())

    private val customPathField = TextFieldWithBrowseButton()
    private val customPreviewButton = JButton("Preview")
    private val volumeSlider = JBSlider(0, 100, 80)
    private val durationSlider = JBSlider(1, 10, 3)
    private val volumeValueLabel = JBLabel()
    private val durationValueLabel = JBLabel()

    // Minimum process duration threshold (0 = disabled)
    private val minDurationSpinner = javax.swing.JSpinner(
        javax.swing.SpinnerNumberModel(0, 0, 300, 1)
    )

    // Visual notification controls
    private val showVisualNotificationCheck = JBCheckBox("Show balloon notification alongside sound")
    private val visualNotificationOnErrorCheck = JBCheckBox("On errors")
    private val visualNotificationOnSuccessCheck = JBCheckBox("On successes")

    // Custom regex rules table
    private val customRuleTableModel = CustomRuleTableModel()
    private val customRuleTable = JBTable(customRuleTableModel)

    override fun getDisplayName(): String = "Error Sound Alert"

    override fun createComponent(): JComponent {
        val root = JPanel(BorderLayout())

        showVisualNotificationCheck.addActionListener { updateInputState() }

        customPathField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptor(true, false, false, false, false, false).apply {
                    title = "Choose Sound File"
                    description = "Select a WAV, AIFF, or AU file."
                }
            )
        )
        customPathField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = onCustomPathChanged()
            override fun removeUpdate(e: DocumentEvent?) = onCustomPathChanged()
            override fun changedUpdate(e: DocumentEvent?) = onCustomPathChanged()
        })

        volumeSlider.paintTicks = true
        volumeSlider.majorTickSpacing = 25

        durationSlider.paintTicks = true
        durationSlider.majorTickSpacing = 1
        durationSlider.minorTickSpacing = 1
        durationSlider.snapToTicks = true

        volumeSlider.addChangeListener { updateSliderValueLabels() }
        durationSlider.addChangeListener {
            updateSliderValueLabels()
            if (!suppressPreview) {
                ErrorSoundPlayer.stopPreview()
            }
        }

        sourceCombo.addActionListener {
            if (!suppressPreview) {
                ErrorSoundPlayer.stopPreview()
            }
            updateInputState()
        }

        useGlobalBuiltInCheck.addActionListener {
            if (useGlobalBuiltInCheck.isSelected) {
                sourceCombo.selectedItem = AlertSettings.SoundSource.BUNDLED
                syncPerKindToGlobal()
            }
            if (!suppressPreview) {
                ErrorSoundPlayer.stopPreview()
            }
            updateInputState()
        }

        builtInSoundCombo.addActionListener {
            if (useGlobalBuiltInCheck.isSelected) {
                syncPerKindToGlobal()
            }
            previewSelectedBuiltIn(builtInSoundCombo)
        }

        attachBuiltInPreview(configurationSoundCombo)
        attachBuiltInPreview(compilationSoundCombo)
        attachBuiltInPreview(testFailureSoundCombo)
        attachBuiltInPreview(networkSoundCombo)
        attachBuiltInPreview(exceptionSoundCombo)
        attachBuiltInPreview(genericSoundCombo)
        attachBuiltInPreview(successSoundCombo)

        customPreviewButton.addActionListener {
            ErrorSoundPlayer.stopPreview()
            ErrorSoundPlayer.previewCustom(customPathField.text.trim(), volumeSlider.value, durationSlider.value)
        }

        val form = FormBuilder.createFormBuilder()
            .addComponent(enabledCheck, 1)
            .addLabeledComponent(
                "Sound source:",
                withHelp(sourceCombo, "Choose Built-in to use bundled sounds, or Custom to use a local file."),
                1,
                false
            )
            .addLabeledComponent(
                "Global built-in sound:",
                withHelp(builtInSoundCombo, "This sound is used for all error kinds when global mode is enabled."),
                1,
                false
            )
            .addComponent(
                withHelp(useGlobalBuiltInCheck, "When enabled, per-error and custom options are disabled and all errors use the global built-in sound."),
                1
            )
            .addLabeledComponent(
                "Configuration error:",
                withHelp(createErrorRow(configurationEnabledCheck, configurationSoundCombo), "Enable/disable sound for configuration errors and select its sound."),
                1,
                false
            )
            .addLabeledComponent(
                "Compilation error:",
                withHelp(createErrorRow(compilationEnabledCheck, compilationSoundCombo), "Enable/disable sound for compilation errors and select its sound."),
                1,
                false
            )
            .addLabeledComponent(
                "Test failure:",
                withHelp(createErrorRow(testFailureEnabledCheck, testFailureSoundCombo), "Enable/disable sound for test failures and select its sound."),
                1,
                false
            )
            .addLabeledComponent(
                "Network error:",
                withHelp(createErrorRow(networkEnabledCheck, networkSoundCombo), "Enable/disable sound for network errors and select its sound."),
                1,
                false
            )
            .addLabeledComponent(
                "Exception:",
                withHelp(createErrorRow(exceptionEnabledCheck, exceptionSoundCombo), "Enable/disable sound for exception-based errors and select its sound."),
                1,
                false
            )
            .addLabeledComponent(
                "Generic error:",
                withHelp(createErrorRow(genericEnabledCheck, genericSoundCombo), "Enable/disable sound for uncategorized errors and select its sound."),
                1,
                false
            )
            .addSeparator(8)
            .addLabeledComponent(
                "Success:",
                withHelp(createErrorRow(successEnabledCheck, successSoundCombo), "Enable/disable sound for successful process completions (exit code 0)."),
                1,
                false
            )
            .addLabeledComponent(
                "Custom sound file:",
                withHelp(createCustomFilePanel(), "Used only when Sound source is Custom and global mode is disabled."),
                1,
                false
            )
            .addLabeledComponent("Volume (%):", createSliderPanel(volumeSlider, volumeValueLabel), 1, false)
            .addLabeledComponent("Alert duration (sec):", createSliderPanel(durationSlider, durationValueLabel), 1, false)
            .addLabeledComponent(
                "Min process duration (sec):",
                withHelp(minDurationSpinner, "Suppress alerts for processes that finish faster than this threshold (0 = no threshold). Applies to Run/Debug only — console and terminal alerts are unaffected."),
                1,
                false
            )
            .addSeparator(8)
            .addComponent(
                withHelp(
                    showVisualNotificationCheck,
                    "When enabled, a balloon notification appears alongside the sound alert (off by default)."
                ),
                1
            )
            .addLabeledComponent(
                "Notify on:",
                createNotificationSubRow(),
                1,
                false
            )
            .addSeparator(8)
            .addComponent(createCustomRulesPanel(), 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        root.add(form, BorderLayout.NORTH)
        panel = root
        reset()
        return root
    }

    override fun isModified(): Boolean {
        val state = settings.state
        val selectedSource = (sourceCombo.selectedItem as? AlertSettings.SoundSource)?.name
            ?: AlertSettings.SoundSource.BUNDLED.name

        return enabledCheck.isSelected != state.enabled ||
            selectedSource != state.soundSource ||
            (builtInSoundCombo.selectedItem as? BuiltInSound)?.id != state.builtInSoundId ||
            useGlobalBuiltInCheck.isSelected != state.useGlobalBuiltInSound ||
            configurationEnabledCheck.isSelected != state.configurationSoundEnabled ||
            (configurationSoundCombo.selectedItem as? BuiltInSound)?.id != state.configurationSoundId ||
            compilationEnabledCheck.isSelected != state.compilationSoundEnabled ||
            (compilationSoundCombo.selectedItem as? BuiltInSound)?.id != state.compilationSoundId ||
            testFailureEnabledCheck.isSelected != state.testFailureSoundEnabled ||
            (testFailureSoundCombo.selectedItem as? BuiltInSound)?.id != state.testFailureSoundId ||
            networkEnabledCheck.isSelected != state.networkSoundEnabled ||
            (networkSoundCombo.selectedItem as? BuiltInSound)?.id != state.networkSoundId ||
            exceptionEnabledCheck.isSelected != state.exceptionSoundEnabled ||
            (exceptionSoundCombo.selectedItem as? BuiltInSound)?.id != state.exceptionSoundId ||
            genericEnabledCheck.isSelected != state.genericSoundEnabled ||
            (genericSoundCombo.selectedItem as? BuiltInSound)?.id != state.genericSoundId ||
            successEnabledCheck.isSelected != state.successSoundEnabled ||
            (successSoundCombo.selectedItem as? BuiltInSound)?.id != state.successSoundId ||
            customPathField.text.trim() != state.customSoundPath ||
            volumeSlider.value != state.volumePercent ||
            durationSlider.value != state.alertDurationSeconds ||
            (minDurationSpinner.value as? Int) != state.minProcessDurationSeconds ||
            showVisualNotificationCheck.isSelected != state.showVisualNotification ||
            visualNotificationOnErrorCheck.isSelected != state.visualNotificationOnError ||
            visualNotificationOnSuccessCheck.isSelected != state.visualNotificationOnSuccess ||
            customRuleTableModel.getRules() != state.customRules
    }

    override fun apply() {
        ErrorSoundPlayer.stopPreview()
        // Stop any in-progress cell edit before reading table data
        if (customRuleTable.isEditing) customRuleTable.cellEditor?.stopCellEditing()

        val selectedSource = (sourceCombo.selectedItem as? AlertSettings.SoundSource)?.name
            ?: AlertSettings.SoundSource.BUNDLED.name
        val current = settings.state

        settings.loadState(
            current.copy(
                enabled = enabledCheck.isSelected,
                soundSource = if (useGlobalBuiltInCheck.isSelected) AlertSettings.SoundSource.BUNDLED.name else selectedSource,
                builtInSoundId = (builtInSoundCombo.selectedItem as? BuiltInSound)?.id ?: BuiltInSounds.default.id,
                useGlobalBuiltInSound = useGlobalBuiltInCheck.isSelected,
                configurationSoundEnabled = configurationEnabledCheck.isSelected,
                configurationSoundId = (configurationSoundCombo.selectedItem as? BuiltInSound)?.id ?: "huh",
                compilationSoundEnabled = compilationEnabledCheck.isSelected,
                compilationSoundId = (compilationSoundCombo.selectedItem as? BuiltInSound)?.id ?: "punch",
                testFailureSoundEnabled = testFailureEnabledCheck.isSelected,
                testFailureSoundId = (testFailureSoundCombo.selectedItem as? BuiltInSound)?.id ?: "dog_laughing_meme",
                networkSoundEnabled = networkEnabledCheck.isSelected,
                networkSoundId = (networkSoundCombo.selectedItem as? BuiltInSound)?.id ?: "yooo",
                exceptionSoundEnabled = exceptionEnabledCheck.isSelected,
                exceptionSoundId = (exceptionSoundCombo.selectedItem as? BuiltInSound)?.id ?: "boom",
                genericSoundEnabled = genericEnabledCheck.isSelected,
                genericSoundId = (genericSoundCombo.selectedItem as? BuiltInSound)?.id ?: BuiltInSounds.default.id,
                successSoundEnabled = successEnabledCheck.isSelected,
                successSoundId = (successSoundCombo.selectedItem as? BuiltInSound)?.id ?: "yeah_boy",
                customSoundPath = customPathField.text.trim(),
                volumePercent = volumeSlider.value,
                alertDurationSeconds = durationSlider.value,
                minProcessDurationSeconds = (minDurationSpinner.value as? Int) ?: 0,
                showVisualNotification = showVisualNotificationCheck.isSelected,
                visualNotificationOnError = visualNotificationOnErrorCheck.isSelected,
                visualNotificationOnSuccess = visualNotificationOnSuccessCheck.isSelected,
                customRules = customRuleTableModel.getRules().toMutableList(),
            )
        )
        // Sync the table back to the normalized state (loadState() may have clamped rule count,
        // truncated patterns, or corrected matchTarget/kind values). This ensures isModified()
        // returns false immediately after Apply and the UI reflects what was actually persisted.
        customRuleTableModel.setRules(settings.state.customRules)
    }

    override fun reset() {
        val state = settings.state
        suppressPreview = true
        try {
            enabledCheck.isSelected = state.enabled
            customPathField.text = state.customSoundPath
            refreshSoundOptionModels()
            sourceCombo.selectedItem = runCatching { AlertSettings.SoundSource.valueOf(state.soundSource) }
                .getOrDefault(AlertSettings.SoundSource.BUNDLED)
            selectSoundId(builtInSoundCombo, state.builtInSoundId)
            useGlobalBuiltInCheck.isSelected = state.useGlobalBuiltInSound
            if (useGlobalBuiltInCheck.isSelected) {
                sourceCombo.selectedItem = AlertSettings.SoundSource.BUNDLED
            }

            configurationEnabledCheck.isSelected = state.configurationSoundEnabled
            selectSoundId(configurationSoundCombo, state.configurationSoundId)

            compilationEnabledCheck.isSelected = state.compilationSoundEnabled
            selectSoundId(compilationSoundCombo, state.compilationSoundId)

            testFailureEnabledCheck.isSelected = state.testFailureSoundEnabled
            selectSoundId(testFailureSoundCombo, state.testFailureSoundId)

            networkEnabledCheck.isSelected = state.networkSoundEnabled
            selectSoundId(networkSoundCombo, state.networkSoundId)

            exceptionEnabledCheck.isSelected = state.exceptionSoundEnabled
            selectSoundId(exceptionSoundCombo, state.exceptionSoundId)

            genericEnabledCheck.isSelected = state.genericSoundEnabled
            selectSoundId(genericSoundCombo, state.genericSoundId)

            successEnabledCheck.isSelected = state.successSoundEnabled
            selectSoundId(successSoundCombo, state.successSoundId)
            volumeSlider.value = state.volumePercent
            durationSlider.value = state.alertDurationSeconds
            minDurationSpinner.value = state.minProcessDurationSeconds
            showVisualNotificationCheck.isSelected = state.showVisualNotification
            visualNotificationOnErrorCheck.isSelected = state.visualNotificationOnError
            visualNotificationOnSuccessCheck.isSelected = state.visualNotificationOnSuccess
            updateSliderValueLabels()

            if (useGlobalBuiltInCheck.isSelected) {
                syncPerKindToGlobal()
            }
            updateInputState()

            customRuleTableModel.setRules(state.customRules)
        } finally {
            suppressPreview = false
        }
    }

    override fun disposeUIResources() {
        ErrorSoundPlayer.stopPreview()
        panel = null
    }

    private fun updateInputState() {
        val isGlobalMode = useGlobalBuiltInCheck.isSelected
        val isBuiltIn = !isGlobalMode && sourceCombo.selectedItem == AlertSettings.SoundSource.BUNDLED
        val isCustom = !isGlobalMode && sourceCombo.selectedItem == AlertSettings.SoundSource.CUSTOM

        sourceCombo.isEnabled = !isGlobalMode
        builtInSoundCombo.isEnabled = true

        configurationEnabledCheck.isEnabled = isBuiltIn && !isGlobalMode
        configurationSoundCombo.isEnabled = isBuiltIn && !isGlobalMode && configurationEnabledCheck.isSelected

        compilationEnabledCheck.isEnabled = isBuiltIn && !isGlobalMode
        compilationSoundCombo.isEnabled = isBuiltIn && !isGlobalMode && compilationEnabledCheck.isSelected

        testFailureEnabledCheck.isEnabled = isBuiltIn && !isGlobalMode
        testFailureSoundCombo.isEnabled = isBuiltIn && !isGlobalMode && testFailureEnabledCheck.isSelected

        networkEnabledCheck.isEnabled = isBuiltIn && !isGlobalMode
        networkSoundCombo.isEnabled = isBuiltIn && !isGlobalMode && networkEnabledCheck.isSelected

        exceptionEnabledCheck.isEnabled = isBuiltIn && !isGlobalMode
        exceptionSoundCombo.isEnabled = isBuiltIn && !isGlobalMode && exceptionEnabledCheck.isSelected

        genericEnabledCheck.isEnabled = isBuiltIn && !isGlobalMode
        genericSoundCombo.isEnabled = isBuiltIn && !isGlobalMode && genericEnabledCheck.isSelected

        successEnabledCheck.isEnabled = isBuiltIn && !isGlobalMode
        successSoundCombo.isEnabled = isBuiltIn && !isGlobalMode && successEnabledCheck.isSelected

        customPathField.isEnabled = !isGlobalMode
        customPreviewButton.isEnabled = !isGlobalMode && customPathField.text.trim().isNotEmpty() && isCustom

        val notificationsEnabled = showVisualNotificationCheck.isSelected
        visualNotificationOnErrorCheck.isEnabled = notificationsEnabled
        visualNotificationOnSuccessCheck.isEnabled = notificationsEnabled
    }

    private fun createNotificationSubRow(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(visualNotificationOnErrorCheck)
            add(visualNotificationOnSuccessCheck)
        }
    }

    // ── Custom Rules Panel ─────────────────────────────────────────────────────

    private fun createCustomRulesPanel(): JPanel {
        customRuleTable.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        customRuleTable.fillsViewportHeight = true
        customRuleTable.rowHeight = 24

        val cm = customRuleTable.columnModel
        cm.getColumn(0).apply {
            preferredWidth = 55
            maxWidth = 65
        }
        cm.getColumn(1).apply {
            preferredWidth = 220
            cellRenderer = PatternValidatingRenderer()
        }
        cm.getColumn(2).apply {
            preferredWidth = 130
            cellEditor = DefaultCellEditor(
                javax.swing.JComboBox(AlertSettings.MatchTarget.entries.map { it.name }.toTypedArray())
            )
        }
        cm.getColumn(3).apply {
            preferredWidth = 120
            cellEditor = DefaultCellEditor(
                javax.swing.JComboBox(
                    CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS
                        .sortedBy { it.name }
                        .map { it.name }
                        .toTypedArray()
                )
            )
        }

        val tablePanel = ToolbarDecorator.createDecorator(customRuleTable)
            .disableUpAction()
            .disableDownAction()
            .setAddAction {
                if (customRuleTable.isEditing) customRuleTable.cellEditor?.stopCellEditing()
                customRuleTableModel.addRule(AlertSettings.CustomRuleState())
                val newRow = customRuleTableModel.rowCount - 1
                customRuleTable.setRowSelectionInterval(newRow, newRow)
                customRuleTable.scrollRectToVisible(customRuleTable.getCellRect(newRow, 1, true))
            }
            .setRemoveAction {
                if (customRuleTable.isEditing) customRuleTable.cellEditor?.stopCellEditing()
                customRuleTable.selectedRows.sortedDescending().forEach { customRuleTableModel.removeRule(it) }
            }
            .createPanel().apply {
                preferredSize = java.awt.Dimension(0, 180)
            }

        val helpTop = JBLabel(
            """
        <html>
          Custom rules run <b>before</b> built-in classification — first matching rule wins.
          <br/>
          Disabled rules and invalid patterns are ignored.
        </html>
        """.trimIndent()
        ).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
        }

        val helpBottom = JBLabel(
            """
        <html>
          <b>Target scope —</b><br/>
          LINE_TEXT: per line/chunk in Run/Debug and Console.<br/>
          FULL_OUTPUT: Run/Debug final buffered output only.<br/>
          EXIT_CODE_AND_TEXT: Run/Debug final output and Terminal
          (matches against <tt>exitcode:N\n&lt;text&gt;</tt>).
        </html>
        """.trimIndent()
        ).apply {
            foreground = JBColor.GRAY
            border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
        }

        return JPanel(BorderLayout(0, 0)).apply {
            add(helpTop, BorderLayout.NORTH)
            add(tablePanel, BorderLayout.CENTER)
            add(helpBottom, BorderLayout.SOUTH)
        }
    }

    // ── Inner classes ──────────────────────────────────────────────────────────

    private class CustomRuleTableModel : AbstractTableModel() {
        private val rules: MutableList<AlertSettings.CustomRuleState> = mutableListOf()

        fun setRules(newRules: List<AlertSettings.CustomRuleState>) {
            rules.clear()
            rules.addAll(newRules.map { it.copy() })  // deep copy — edits in UI don't mutate settings
            fireTableDataChanged()
        }

        fun getRules(): List<AlertSettings.CustomRuleState> = rules.toList()

        fun addRule(rule: AlertSettings.CustomRuleState) {
            rules.add(rule)
            fireTableRowsInserted(rules.size - 1, rules.size - 1)
        }

        fun removeRule(index: Int) {
            if (index in rules.indices) {
                rules.removeAt(index)
                fireTableRowsDeleted(index, index)
            }
        }

        override fun getRowCount(): Int = rules.size
        override fun getColumnCount(): Int = 4
        override fun getColumnName(col: Int): String = when (col) {
            0 -> "Enabled"
            1 -> "Pattern"
            2 -> "Match Target"
            3 -> "Kind"
            else -> ""
        }
        override fun getColumnClass(col: Int): Class<*> =
            if (col == 0) Boolean::class.javaObjectType else String::class.java
        override fun isCellEditable(row: Int, col: Int): Boolean = true

        override fun getValueAt(row: Int, col: Int): Any = when (col) {
            0 -> rules[row].enabled
            1 -> rules[row].pattern
            2 -> rules[row].matchTarget
            3 -> rules[row].kind
            else -> ""
        }

        override fun setValueAt(value: Any?, row: Int, col: Int) {
            if (row !in rules.indices) return
            when (col) {
                0 -> rules[row].enabled = value as? Boolean ?: true
                1 -> rules[row].pattern = value as? String ?: ""
                2 -> rules[row].matchTarget = value as? String ?: AlertSettings.MatchTarget.LINE_TEXT.name
                3 -> rules[row].kind = value as? String ?: ErrorKind.GENERIC.name
            }
            fireTableCellUpdated(row, col)
        }
    }

    /** Renders the Pattern cell with a red tint when the regex is syntactically invalid. */
    private class PatternValidatingRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            val pattern = value as? String ?: ""
            if (pattern.isNotBlank() && !isSelected) {
                val valid = try { java.util.regex.Pattern.compile(pattern); true }
                catch (_: PatternSyntaxException) { false }
                if (!valid) {
                    comp.background = JBColor(Color(255, 185, 185), Color(110, 55, 55))
                    (comp as? JLabel)?.toolTipText = "Invalid regex pattern"
                } else {
                    (comp as? JLabel)?.toolTipText = null
                }
            }
            return comp
        }
    }

    // ── Existing helpers (unchanged) ───────────────────────────────────────────

    private fun attachBuiltInPreview(combo: ComboBox<BuiltInSound>) {
        combo.addActionListener { previewSelectedBuiltIn(combo) }
    }

    private fun previewSelectedBuiltIn(combo: ComboBox<BuiltInSound>) {
        if (suppressPreview) {
            return
        }
        if (!combo.isFocusOwner && !combo.isPopupVisible) {
            return
        }
        val selectedId = (combo.selectedItem as? BuiltInSound)?.id ?: return
        ErrorSoundPlayer.stopPreview()
        if (selectedId == BuiltInSounds.CUSTOM_FILE_ID) {
            ErrorSoundPlayer.previewCustom(customPathField.text.trim(), volumeSlider.value, durationSlider.value)
        } else {
            ErrorSoundPlayer.previewBuiltIn(selectedId, volumeSlider.value, durationSlider.value)
        }
    }

    private fun syncPerKindToGlobal() {
        val global = builtInSoundCombo.selectedItem as? BuiltInSound ?: return
        suppressPreview = true
        try {
            configurationSoundCombo.selectedItem = global
            compilationSoundCombo.selectedItem = global
            testFailureSoundCombo.selectedItem = global
            networkSoundCombo.selectedItem = global
            exceptionSoundCombo.selectedItem = global
            genericSoundCombo.selectedItem = global
            successSoundCombo.selectedItem = global
        } finally {
            suppressPreview = false
        }
    }

    private fun updateSliderValueLabels() {
        volumeValueLabel.text = "Current: ${volumeSlider.value}%"
        durationValueLabel.text = "Current: ${durationSlider.value} sec"
    }

    private fun createSliderPanel(slider: JBSlider, valueLabel: JBLabel): JPanel {
        return JPanel(BorderLayout()).apply {
            add(slider, BorderLayout.CENTER)
            valueLabel.border = BorderFactory.createEmptyBorder(2, 2, 0, 0)
            add(valueLabel, BorderLayout.SOUTH)
        }
    }

    private fun createCustomFilePanel(): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            add(customPathField, BorderLayout.CENTER)
            add(customPreviewButton, BorderLayout.EAST)
        }
    }

    private fun createErrorRow(enabledCheck: JBCheckBox, combo: ComboBox<BuiltInSound>): JPanel {
        enabledCheck.addActionListener { updateInputState() }
        return JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(enabledCheck)
            add(combo)
        }
    }

    private fun withHelp(component: JComponent, helpText: String): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            add(component, BorderLayout.CENTER)
            add(ContextHelpLabel.create(helpText), BorderLayout.EAST)
        }
    }

    private fun onCustomPathChanged() {
        if (!suppressPreview) {
            ErrorSoundPlayer.stopPreview()
        }
        refreshSoundOptionModels()
        updateInputState()
    }

    private fun refreshSoundOptionModels() {
        val options = BuiltInSounds.allWithCustom(customPathField.text.trim()).toTypedArray()
        val selected = mapOf(
            builtInSoundCombo to selectedSoundId(builtInSoundCombo),
            configurationSoundCombo to selectedSoundId(configurationSoundCombo),
            compilationSoundCombo to selectedSoundId(compilationSoundCombo),
            testFailureSoundCombo to selectedSoundId(testFailureSoundCombo),
            networkSoundCombo to selectedSoundId(networkSoundCombo),
            exceptionSoundCombo to selectedSoundId(exceptionSoundCombo),
            genericSoundCombo to selectedSoundId(genericSoundCombo),
            successSoundCombo to selectedSoundId(successSoundCombo),
        )

        suppressPreview = true
        try {
            for ((combo, id) in selected) {
                combo.model = DefaultComboBoxModel(options)
                selectSoundId(combo, id)
            }
        } finally {
            suppressPreview = false
        }
    }

    private fun selectedSoundId(combo: ComboBox<BuiltInSound>): String {
        return (combo.selectedItem as? BuiltInSound)?.id ?: BuiltInSounds.default.id
    }

    private fun selectSoundId(combo: ComboBox<BuiltInSound>, id: String) {
        val normalizedId = if (id == BuiltInSounds.CUSTOM_FILE_ID && customPathField.text.trim().isBlank()) {
            BuiltInSounds.default.id
        } else {
            id
        }
        combo.selectedItem = BuiltInSounds.findByIdOrDefault(normalizedId, customPathField.text.trim())
    }
}
