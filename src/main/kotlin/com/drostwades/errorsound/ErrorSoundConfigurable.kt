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
import com.intellij.ui.components.JBScrollPane
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
import javax.swing.JTextArea
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

    // ── Per-kind volume controls (Phase 8 — Per-Kind Volume) ──────────────────
    // Each kind has: a checkbox (activates the override), a slider (0–100), and a value label.
    // Slider and label are disabled when the checkbox is unchecked.
    private val configurationVolumeCheck  = JBCheckBox("Custom volume")
    private val configurationVolumeSlider = JBSlider(0, 100, 80)
    private val configurationVolumeLabel  = JBLabel("80%")

    private val compilationVolumeCheck    = JBCheckBox("Custom volume")
    private val compilationVolumeSlider   = JBSlider(0, 100, 80)
    private val compilationVolumeLabel    = JBLabel("80%")

    private val testFailureVolumeCheck    = JBCheckBox("Custom volume")
    private val testFailureVolumeSlider   = JBSlider(0, 100, 80)
    private val testFailureVolumeLabel    = JBLabel("80%")

    private val networkVolumeCheck        = JBCheckBox("Custom volume")
    private val networkVolumeSlider       = JBSlider(0, 100, 80)
    private val networkVolumeLabel        = JBLabel("80%")

    private val exceptionVolumeCheck      = JBCheckBox("Custom volume")
    private val exceptionVolumeSlider     = JBSlider(0, 100, 80)
    private val exceptionVolumeLabel      = JBLabel("80%")

    private val genericVolumeCheck        = JBCheckBox("Custom volume")
    private val genericVolumeSlider       = JBSlider(0, 100, 80)
    private val genericVolumeLabel        = JBLabel("80%")

    private val successVolumeCheck        = JBCheckBox("Custom volume")
    private val successVolumeSlider       = JBSlider(0, 100, 80)
    private val successVolumeLabel        = JBLabel("80%")

    /**
     * Maps each per-kind sound combo to a lambda that returns its effective preview volume.
     * When the kind's "Custom volume" checkbox is checked, returns the kind slider value;
     * otherwise falls back to the global [volumeSlider] value.
     * [builtInSoundCombo] is intentionally absent — it always uses [volumeSlider].
     */
    private val kindVolumeMap: Map<ComboBox<BuiltInSound>, () -> Int> by lazy {
        mapOf(
            configurationSoundCombo to { if (configurationVolumeCheck.isSelected) configurationVolumeSlider.value else volumeSlider.value },
            compilationSoundCombo   to { if (compilationVolumeCheck.isSelected)   compilationVolumeSlider.value   else volumeSlider.value },
            testFailureSoundCombo   to { if (testFailureVolumeCheck.isSelected)   testFailureVolumeSlider.value   else volumeSlider.value },
            networkSoundCombo       to { if (networkVolumeCheck.isSelected)       networkVolumeSlider.value       else volumeSlider.value },
            exceptionSoundCombo     to { if (exceptionVolumeCheck.isSelected)     exceptionVolumeSlider.value     else volumeSlider.value },
            genericSoundCombo       to { if (genericVolumeCheck.isSelected)       genericVolumeSlider.value       else volumeSlider.value },
            successSoundCombo       to { if (successVolumeCheck.isSelected)       successVolumeSlider.value       else volumeSlider.value },
        )
    }

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

    // Rule testing sandbox (Phase 1 roadmap)
    private val ruleTestSourceCombo = ComboBox(RuleTestService.SourceMode.entries.toTypedArray())
    private val ruleTestTargetCombo = ComboBox(AlertSettings.MatchTarget.entries.toTypedArray())
    private val ruleTestExitCodeSpinner = javax.swing.JSpinner(
        javax.swing.SpinnerNumberModel(0, -9999, 9999, 1)
    )
    private val ruleTestOutputArea = JTextArea(6, 60).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val ruleTestResultArea = JTextArea(8, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        text = "Paste sample output, choose a source and target, then click Test Rules."
    }
    private val ruleTestButton = JButton("Test Rules")

    // Exit code rules table
    private val soundChoices: List<SoundChoice> =
        listOf(SoundChoice(null, "(default)")) + BuiltInSounds.all.map { SoundChoice(it.id, it.toString()) }
    private val exitCodeRuleTableModel = ExitCodeRuleTableModel(soundChoices)
    private val exitCodeRuleTable = JBTable(exitCodeRuleTableModel)

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

        volumeSlider.addChangeListener { updateSliderValueLabels(); refreshUncheckedKindVolumeLabels() }
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
                withHelp(
                    createKindPanel(
                        createErrorRow(configurationEnabledCheck, configurationSoundCombo),
                        createKindVolumeRow(configurationVolumeCheck, configurationVolumeSlider, configurationVolumeLabel)
                    ),
                    "Enable/disable sound for configuration errors; optionally override volume for this kind."
                ),
                1, false
            )
            .addLabeledComponent(
                "Compilation error:",
                withHelp(
                    createKindPanel(
                        createErrorRow(compilationEnabledCheck, compilationSoundCombo),
                        createKindVolumeRow(compilationVolumeCheck, compilationVolumeSlider, compilationVolumeLabel)
                    ),
                    "Enable/disable sound for compilation errors; optionally override volume for this kind."
                ),
                1, false
            )
            .addLabeledComponent(
                "Test failure:",
                withHelp(
                    createKindPanel(
                        createErrorRow(testFailureEnabledCheck, testFailureSoundCombo),
                        createKindVolumeRow(testFailureVolumeCheck, testFailureVolumeSlider, testFailureVolumeLabel)
                    ),
                    "Enable/disable sound for test failures; optionally override volume for this kind."
                ),
                1, false
            )
            .addLabeledComponent(
                "Network error:",
                withHelp(
                    createKindPanel(
                        createErrorRow(networkEnabledCheck, networkSoundCombo),
                        createKindVolumeRow(networkVolumeCheck, networkVolumeSlider, networkVolumeLabel)
                    ),
                    "Enable/disable sound for network errors; optionally override volume for this kind."
                ),
                1, false
            )
            .addLabeledComponent(
                "Exception:",
                withHelp(
                    createKindPanel(
                        createErrorRow(exceptionEnabledCheck, exceptionSoundCombo),
                        createKindVolumeRow(exceptionVolumeCheck, exceptionVolumeSlider, exceptionVolumeLabel)
                    ),
                    "Enable/disable sound for exception-based errors; optionally override volume for this kind."
                ),
                1, false
            )
            .addLabeledComponent(
                "Generic error:",
                withHelp(
                    createKindPanel(
                        createErrorRow(genericEnabledCheck, genericSoundCombo),
                        createKindVolumeRow(genericVolumeCheck, genericVolumeSlider, genericVolumeLabel)
                    ),
                    "Enable/disable sound for uncategorized errors; optionally override volume for this kind."
                ),
                1, false
            )
            .addSeparator(8)
            .addLabeledComponent(
                "Success:",
                withHelp(
                    createKindPanel(
                        createErrorRow(successEnabledCheck, successSoundCombo),
                        createKindVolumeRow(successVolumeCheck, successVolumeSlider, successVolumeLabel)
                    ),
                    "Enable/disable sound for successful process completions; optionally override volume for this kind."
                ),
                1, false
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
            .addSeparator(8)
            .addComponent(createRuleTestingSandboxPanel(), 1)
            .addSeparator(8)
            .addComponent(createExitCodeRulesPanel(), 1)
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
            // Phase 8: per-kind volume — check activation flag AND value when active
            configurationVolumeCheck.isSelected != (state.configurationVolumePercent != null) ||
            (configurationVolumeCheck.isSelected && configurationVolumeSlider.value != state.configurationVolumePercent) ||
            compilationVolumeCheck.isSelected != (state.compilationVolumePercent != null) ||
            (compilationVolumeCheck.isSelected && compilationVolumeSlider.value != state.compilationVolumePercent) ||
            testFailureVolumeCheck.isSelected != (state.testFailureVolumePercent != null) ||
            (testFailureVolumeCheck.isSelected && testFailureVolumeSlider.value != state.testFailureVolumePercent) ||
            networkVolumeCheck.isSelected != (state.networkVolumePercent != null) ||
            (networkVolumeCheck.isSelected && networkVolumeSlider.value != state.networkVolumePercent) ||
            exceptionVolumeCheck.isSelected != (state.exceptionVolumePercent != null) ||
            (exceptionVolumeCheck.isSelected && exceptionVolumeSlider.value != state.exceptionVolumePercent) ||
            genericVolumeCheck.isSelected != (state.genericVolumePercent != null) ||
            (genericVolumeCheck.isSelected && genericVolumeSlider.value != state.genericVolumePercent) ||
            successVolumeCheck.isSelected != (state.successVolumePercent != null) ||
            (successVolumeCheck.isSelected && successVolumeSlider.value != state.successVolumePercent) ||
            customPathField.text.trim() != state.customSoundPath ||
            volumeSlider.value != state.volumePercent ||
            durationSlider.value != state.alertDurationSeconds ||
            (minDurationSpinner.value as? Int) != state.minProcessDurationSeconds ||
            showVisualNotificationCheck.isSelected != state.showVisualNotification ||
            visualNotificationOnErrorCheck.isSelected != state.visualNotificationOnError ||
            visualNotificationOnSuccessCheck.isSelected != state.visualNotificationOnSuccess ||
            customRuleTableModel.getRules() != state.customRules ||
            exitCodeRuleTableModel.getRules() != state.exitCodeRules
    }

    override fun apply() {
        ErrorSoundPlayer.stopPreview()
        // Stop any in-progress cell edit before reading table data
        if (customRuleTable.isEditing) customRuleTable.cellEditor?.stopCellEditing()
        if (exitCodeRuleTable.isEditing) exitCodeRuleTable.cellEditor?.stopCellEditing()

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
                // Phase 8: per-kind volume — persist null when checkbox is unchecked
                configurationVolumePercent = if (configurationVolumeCheck.isSelected) configurationVolumeSlider.value else null,
                compilationVolumePercent   = if (compilationVolumeCheck.isSelected)   compilationVolumeSlider.value   else null,
                testFailureVolumePercent   = if (testFailureVolumeCheck.isSelected)   testFailureVolumeSlider.value   else null,
                networkVolumePercent       = if (networkVolumeCheck.isSelected)       networkVolumeSlider.value       else null,
                exceptionVolumePercent     = if (exceptionVolumeCheck.isSelected)     exceptionVolumeSlider.value     else null,
                genericVolumePercent       = if (genericVolumeCheck.isSelected)       genericVolumeSlider.value       else null,
                successVolumePercent       = if (successVolumeCheck.isSelected)       successVolumeSlider.value       else null,
                customSoundPath = customPathField.text.trim(),
                volumePercent = volumeSlider.value,
                alertDurationSeconds = durationSlider.value,
                minProcessDurationSeconds = (minDurationSpinner.value as? Int) ?: 0,
                showVisualNotification = showVisualNotificationCheck.isSelected,
                visualNotificationOnError = visualNotificationOnErrorCheck.isSelected,
                visualNotificationOnSuccess = visualNotificationOnSuccessCheck.isSelected,
                customRules = customRuleTableModel.getRules().toMutableList(),
                exitCodeRules = exitCodeRuleTableModel.getRules().toMutableList(),
            )
        )
        // Sync tables back to normalized state so isModified() returns false immediately after Apply.
        customRuleTableModel.setRules(settings.state.customRules)
        exitCodeRuleTableModel.setRules(settings.state.exitCodeRules)
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

            // Phase 8: restore per-kind volume — check = non-null, slider = override or global fallback
            configurationVolumeCheck.isSelected  = state.configurationVolumePercent != null
            configurationVolumeSlider.value       = state.configurationVolumePercent ?: state.volumePercent
            configurationVolumeLabel.text         = "${configurationVolumeSlider.value}%"

            compilationVolumeCheck.isSelected     = state.compilationVolumePercent != null
            compilationVolumeSlider.value         = state.compilationVolumePercent ?: state.volumePercent
            compilationVolumeLabel.text           = "${compilationVolumeSlider.value}%"

            testFailureVolumeCheck.isSelected     = state.testFailureVolumePercent != null
            testFailureVolumeSlider.value         = state.testFailureVolumePercent ?: state.volumePercent
            testFailureVolumeLabel.text           = "${testFailureVolumeSlider.value}%"

            networkVolumeCheck.isSelected         = state.networkVolumePercent != null
            networkVolumeSlider.value             = state.networkVolumePercent ?: state.volumePercent
            networkVolumeLabel.text               = "${networkVolumeSlider.value}%"

            exceptionVolumeCheck.isSelected       = state.exceptionVolumePercent != null
            exceptionVolumeSlider.value           = state.exceptionVolumePercent ?: state.volumePercent
            exceptionVolumeLabel.text             = "${exceptionVolumeSlider.value}%"

            genericVolumeCheck.isSelected         = state.genericVolumePercent != null
            genericVolumeSlider.value             = state.genericVolumePercent ?: state.volumePercent
            genericVolumeLabel.text               = "${genericVolumeSlider.value}%"

            successVolumeCheck.isSelected         = state.successVolumePercent != null
            successVolumeSlider.value             = state.successVolumePercent ?: state.volumePercent
            successVolumeLabel.text               = "${successVolumeSlider.value}%"

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
            exitCodeRuleTableModel.setRules(state.exitCodeRules)
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

        // Phase 8: per-kind volume slider and label are enabled only when their checkbox is checked.
        // The checkbox itself is always enabled (independent of sound source and global mode).
        configurationVolumeSlider.isEnabled = configurationVolumeCheck.isSelected
        configurationVolumeLabel.isEnabled  = configurationVolumeCheck.isSelected
        compilationVolumeSlider.isEnabled   = compilationVolumeCheck.isSelected
        compilationVolumeLabel.isEnabled    = compilationVolumeCheck.isSelected
        testFailureVolumeSlider.isEnabled   = testFailureVolumeCheck.isSelected
        testFailureVolumeLabel.isEnabled    = testFailureVolumeCheck.isSelected
        networkVolumeSlider.isEnabled       = networkVolumeCheck.isSelected
        networkVolumeLabel.isEnabled        = networkVolumeCheck.isSelected
        exceptionVolumeSlider.isEnabled     = exceptionVolumeCheck.isSelected
        exceptionVolumeLabel.isEnabled      = exceptionVolumeCheck.isSelected
        genericVolumeSlider.isEnabled       = genericVolumeCheck.isSelected
        genericVolumeLabel.isEnabled        = genericVolumeCheck.isSelected
        successVolumeSlider.isEnabled       = successVolumeCheck.isSelected
        successVolumeLabel.isEnabled        = successVolumeCheck.isSelected
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

    // ── Rule Testing Sandbox ──────────────────────────────────────────────────

    private fun createRuleTestingSandboxPanel(): JPanel {
        ruleTestButton.addActionListener {
            if (customRuleTable.isEditing) customRuleTable.cellEditor?.stopCellEditing()
            runRuleSandbox()
        }

        val controls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(JBLabel("Source:"))
            add(ruleTestSourceCombo)
            add(JBLabel("Match target:"))
            add(ruleTestTargetCombo)
            add(JBLabel("Exit code:"))
            add(ruleTestExitCodeSpinner)
            add(ruleTestButton)
        }

        val inputScroll = JBScrollPane(ruleTestOutputArea).apply {
            preferredSize = java.awt.Dimension(0, 120)
        }
        val resultScroll = JBScrollPane(ruleTestResultArea).apply {
            preferredSize = java.awt.Dimension(0, 150)
        }

        val content = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(JBLabel("Rule Testing Sandbox"))
            add(controls)
            add(JBLabel("Sample output:"))
            add(inputScroll)
            add(JBLabel("Result:"))
            add(resultScroll)
        }

        return JPanel(BorderLayout(0, 4)).apply {
            add(content, BorderLayout.CENTER)
        }
    }

    private fun runRuleSandbox() {
        val sourceMode = ruleTestSourceCombo.selectedItem as? RuleTestService.SourceMode
            ?: RuleTestService.SourceMode.RUN_DEBUG
        val matchTarget = ruleTestTargetCombo.selectedItem as? AlertSettings.MatchTarget
            ?: AlertSettings.MatchTarget.LINE_TEXT
        val exitCode = (ruleTestExitCodeSpinner.value as? Number)?.toInt() ?: 0

        val result = RuleTestService.evaluate(
            RuleTestService.Input(
                rules = customRuleTableModel.getRules(),
                sampleOutput = ruleTestOutputArea.text.orEmpty(),
                matchTarget = matchTarget,
                exitCode = exitCode,
                sourceMode = sourceMode,
            )
        )

        ruleTestResultArea.text = formatRuleTestResult(result)
        ruleTestResultArea.caretPosition = 0
    }

    private fun formatRuleTestResult(result: RuleTestService.Result): String {
        val lines = mutableListOf<String>()

        if (result.customMatched) {
            val match = result.customMatch!!
            lines += "Custom rule matched: Yes"
            lines += "Matched rule: row ${match.rowNumber}, id ${match.id}"
            lines += "Pattern: ${match.pattern}"
            lines += "Match target: ${match.target}"
            lines += "Matched context: ${match.matchedContext}"
            lines += "Resulting ErrorKind: ${match.kind}"
        } else {
            lines += "Custom rule matched: No"
            lines += "Resulting ErrorKind: ${result.resultingKind}"
        }

        lines += "Built-in classifier if no custom rule matched: " +
            if (result.builtInWouldMatch) "Yes (${result.builtInKind})" else "No"

        if (!result.customMatched && !result.builtInWouldMatch) {
            lines += "No custom rule matched and the built-in classifier would not match this sample."
        }

        if (result.validationErrors.isNotEmpty()) {
            lines += ""
            lines += "Regex validation errors:"
            result.validationErrors.forEach { error ->
                lines += "- Row ${error.rowNumber}, id ${error.id}: ${error.message}"
            }
        }

        if (result.notes.isNotEmpty()) {
            lines += ""
            lines += "Notes:"
            result.notes.forEach { note -> lines += "- $note" }
        }

        return lines.joinToString("\n")
    }

    // ── Exit Code Rules Panel ──────────────────────────────────────────────────

    private fun createExitCodeRulesPanel(): JPanel {
        exitCodeRuleTable.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        exitCodeRuleTable.fillsViewportHeight = true
        exitCodeRuleTable.rowHeight = 24

        val cm = exitCodeRuleTable.columnModel
        cm.getColumn(0).apply {      // Exit Code
            preferredWidth = 75
            maxWidth = 90
        }
        cm.getColumn(1).apply {      // Enabled
            preferredWidth = 60
            maxWidth = 70
        }
        cm.getColumn(2).apply {      // Kind
            preferredWidth = 130
            cellEditor = DefaultCellEditor(
                javax.swing.JComboBox(
                    CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS
                        .sortedBy { it.name }
                        .map { it.name }
                        .toTypedArray()
                )
            )
        }
        cm.getColumn(3).apply {      // Sound override
            preferredWidth = 150
            cellEditor = DefaultCellEditor(javax.swing.JComboBox(soundChoices.toTypedArray()))
        }
        cm.getColumn(4).apply {      // Suppress
            preferredWidth = 70
            maxWidth = 80
        }

        val tablePanel = ToolbarDecorator.createDecorator(exitCodeRuleTable)
            .disableUpAction()
            .disableDownAction()
            .setAddAction {
                if (exitCodeRuleTable.isEditing) exitCodeRuleTable.cellEditor?.stopCellEditing()
                exitCodeRuleTableModel.addRule(AlertSettings.ExitCodeRuleState())
                val newRow = exitCodeRuleTableModel.rowCount - 1
                exitCodeRuleTable.setRowSelectionInterval(newRow, newRow)
                exitCodeRuleTable.scrollRectToVisible(exitCodeRuleTable.getCellRect(newRow, 0, true))
            }
            .setRemoveAction {
                if (exitCodeRuleTable.isEditing) exitCodeRuleTable.cellEditor?.stopCellEditing()
                exitCodeRuleTable.selectedRows.sortedDescending().forEach { exitCodeRuleTableModel.removeRule(it) }
            }
            .createPanel().apply {
                preferredSize = java.awt.Dimension(0, 160)
            }

        val helpTop = JBLabel(
            """
            <html>
              Exit-code rules apply to <b>terminal</b> commands only — first matching enabled rule wins.
              <br/>
              Rules are checked <i>after</i> custom regex rules but before built-in classification.
            </html>
            """.trimIndent()
        ).apply {
            border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
        }

        val helpBottom = JBLabel(
            """
            <html>
              <b>Sound</b> — "(default)" uses normal kind/global resolution; any other value overrides
              the sound for that event only.
              <br/>
              <b>Suppress</b> — silences the alert entirely (e.g. Ctrl+C / exit 130).
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

    /** Wraps a nullable built-in sound ID for display in the Exit Code Rules sound combo column. */
    private data class SoundChoice(val id: String?, val label: String) {
        override fun toString(): String = label
    }

    private class ExitCodeRuleTableModel(
        private val soundChoices: List<SoundChoice>,
    ) : AbstractTableModel() {

        /** Internal mutable row mirroring [AlertSettings.ExitCodeRuleState] with [SoundChoice] for col 3. */
        private data class Row(
            var exitCode: Int,
            var enabled: Boolean,
            var kind: String,
            var sound: SoundChoice,
            var suppress: Boolean,
        )

        private val rows: MutableList<Row> = mutableListOf()

        private fun soundChoiceForId(id: String?): SoundChoice =
            if (id == null) soundChoices.first() else soundChoices.find { it.id == id } ?: soundChoices.first()

        fun setRules(newRules: List<AlertSettings.ExitCodeRuleState>) {
            rows.clear()
            newRules.forEach { r ->
                rows.add(Row(
                    exitCode = r.exitCode,
                    enabled = r.enabled,
                    kind = r.kind,
                    sound = soundChoiceForId(r.soundId),
                    suppress = r.suppress,
                ))
            }
            fireTableDataChanged()
        }

        fun getRules(): List<AlertSettings.ExitCodeRuleState> = rows.map { r ->
            AlertSettings.ExitCodeRuleState(
                exitCode = r.exitCode,
                enabled = r.enabled,
                kind = r.kind,
                soundId = r.sound.id,
                suppress = r.suppress,
            )
        }

        fun addRule(rule: AlertSettings.ExitCodeRuleState) {
            rows.add(Row(
                exitCode = rule.exitCode,
                enabled = rule.enabled,
                kind = rule.kind,
                sound = soundChoiceForId(rule.soundId),
                suppress = rule.suppress,
            ))
            fireTableRowsInserted(rows.size - 1, rows.size - 1)
        }

        fun removeRule(index: Int) {
            if (index in rows.indices) {
                rows.removeAt(index)
                fireTableRowsDeleted(index, index)
            }
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 5
        override fun getColumnName(col: Int): String = when (col) {
            0 -> "Exit Code"
            1 -> "Enabled"
            2 -> "Kind"
            3 -> "Sound"
            4 -> "Suppress"
            else -> ""
        }
        override fun getColumnClass(col: Int): Class<*> = when (col) {
            0 -> Int::class.javaObjectType
            1 -> Boolean::class.javaObjectType
            2 -> String::class.java
            3 -> SoundChoice::class.java
            4 -> Boolean::class.javaObjectType
            else -> Any::class.java
        }
        override fun isCellEditable(row: Int, col: Int): Boolean = true

        override fun getValueAt(row: Int, col: Int): Any? = when (col) {
            0 -> rows[row].exitCode
            1 -> rows[row].enabled
            2 -> rows[row].kind
            3 -> rows[row].sound
            4 -> rows[row].suppress
            else -> null
        }

        override fun setValueAt(value: Any?, row: Int, col: Int) {
            if (row !in rows.indices) return
            when (col) {
                0 -> rows[row].exitCode = when (value) {
                    is Int -> value
                    is String -> value.toIntOrNull() ?: rows[row].exitCode
                    else -> rows[row].exitCode
                }
                1 -> rows[row].enabled = value as? Boolean ?: true
                2 -> rows[row].kind = value as? String ?: ErrorKind.GENERIC.name
                3 -> rows[row].sound = value as? SoundChoice ?: soundChoices.first()
                4 -> rows[row].suppress = value as? Boolean ?: false
            }
            fireTableCellUpdated(row, col)
        }
    }

    // ── Existing helpers (unchanged) ───────────────────────────────────────────

    private fun attachBuiltInPreview(combo: ComboBox<BuiltInSound>) {
        combo.addActionListener { previewSelectedBuiltIn(combo) }
    }

    private fun previewSelectedBuiltIn(combo: ComboBox<BuiltInSound>) {
        if (suppressPreview) return
        if (!combo.isFocusOwner && !combo.isPopupVisible) return
        val selectedId = (combo.selectedItem as? BuiltInSound)?.id ?: return
        // Phase 8: use per-kind effective volume; absent from map means global combo → use volumeSlider
        val effectiveVol = kindVolumeMap[combo]?.invoke() ?: volumeSlider.value
        ErrorSoundPlayer.stopPreview()
        if (selectedId == BuiltInSounds.CUSTOM_FILE_ID) {
            ErrorSoundPlayer.previewCustom(customPathField.text.trim(), effectiveVol, durationSlider.value)
        } else {
            ErrorSoundPlayer.previewBuiltIn(selectedId, effectiveVol, durationSlider.value)
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

    /**
     * Refreshes the display label of every per-kind volume row whose "Custom volume"
     * checkbox is **unchecked**, so the greyed-out label mirrors the current global
     * [volumeSlider] value instead of showing a stale fallback.
     *
     * Called from the global volume slider listener. Does not affect any kind whose
     * checkbox is checked — those labels are owned by their own slider listener.
     */
    private fun refreshUncheckedKindVolumeLabels() {
        val globalPct = volumeSlider.value
        if (!configurationVolumeCheck.isSelected) configurationVolumeLabel.text = "$globalPct%"
        if (!compilationVolumeCheck.isSelected)   compilationVolumeLabel.text   = "$globalPct%"
        if (!testFailureVolumeCheck.isSelected)   testFailureVolumeLabel.text   = "$globalPct%"
        if (!networkVolumeCheck.isSelected)       networkVolumeLabel.text       = "$globalPct%"
        if (!exceptionVolumeCheck.isSelected)     exceptionVolumeLabel.text     = "$globalPct%"
        if (!genericVolumeCheck.isSelected)       genericVolumeLabel.text       = "$globalPct%"
        if (!successVolumeCheck.isSelected)       successVolumeLabel.text       = "$globalPct%"
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

    /**
     * Stacks [soundRow] and [volumeRow] vertically into a single component
     * for use with [com.intellij.util.ui.FormBuilder.addLabeledComponent].
     */
    private fun createKindPanel(soundRow: JPanel, volumeRow: JPanel): JPanel =
        JPanel(BorderLayout(0, 2)).apply {
            add(soundRow, BorderLayout.CENTER)
            add(volumeRow, BorderLayout.SOUTH)
        }

    /**
     * Creates the compact "Custom volume" row for a single error kind (Phase 8).
     *
     * Layout: [checkbox] [slider ── CENTER] ["xx%" label]
     *
     * The [slider] and [label] enabled state is managed by [updateInputState]; [check] is always enabled.
     */
    private fun createKindVolumeRow(check: JBCheckBox, slider: JBSlider, label: JBLabel): JPanel {
        slider.paintTicks = true
        slider.majorTickSpacing = 25
        check.addActionListener { updateInputState() }
        slider.addChangeListener { label.text = "${slider.value}%" }
        val sliderWithLabel = JPanel(BorderLayout(4, 0)).apply {
            add(slider, BorderLayout.CENTER)
            label.border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
            add(label, BorderLayout.EAST)
        }
        return JPanel(BorderLayout(6, 0)).apply {
            add(check, BorderLayout.WEST)
            add(sliderWithLabel, BorderLayout.CENTER)
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
