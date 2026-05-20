package com.drostwades.errorsound

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBSlider
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.SwingConstants
import javax.swing.event.ChangeListener

class ProjectProfilePanel(
    private val project: Project,
    private val onChange: () -> Unit,
) : JPanel() {

    private val projectSettings: ProjectAlertSettings
        get() = ProjectAlertSettings.getInstance(project)

    private val useProfileOverridesCheckBox = JBCheckBox("Use project profile overrides")
    private val copyGlobalButton = JButton("Copy current global settings")
    private val resetButton = JButton("Reset project overrides")
    private val repoProfileStatusLabel = JBLabel()
    private val reloadRepoProfileButton = JButton("Reload repo profile")
    private val openRepoProfileButton = JButton("Open repo profile file")

    private val useEnabledOverrideCheckBox = JBCheckBox("Override master monitoring enabled")
    private val enabledOverrideCheckBox = JBCheckBox("Enable monitoring for this project")

    private val useMonitoringOverridesCheckBox = JBCheckBox("Override monitored kinds")
    private val monitorConfigurationCheckBox = JBCheckBox("Configuration")
    private val monitorCompilationCheckBox = JBCheckBox("Compilation")
    private val monitorTestFailureCheckBox = JBCheckBox("Test failure")
    private val monitorNetworkCheckBox = JBCheckBox("Network")
    private val monitorExceptionCheckBox = JBCheckBox("Exception")
    private val monitorGenericCheckBox = JBCheckBox("Generic")
    private val monitorSuccessCheckBox = JBCheckBox("Success")

    private val soundChoices = BuiltInSounds.all.toTypedArray()
    private val useSoundOverridesCheckBox = JBCheckBox("Override built-in sound behavior")
    private val useGlobalBuiltInSoundCheckBox = JBCheckBox("Use one built-in sound for all kinds")
    private val globalSoundCombo = ComboBox(soundChoices)
    private val configurationSoundRow = KindSoundRow(ErrorKind.CONFIGURATION, JBCheckBox("Configuration"), ComboBox(soundChoices))
    private val compilationSoundRow = KindSoundRow(ErrorKind.COMPILATION, JBCheckBox("Compilation"), ComboBox(soundChoices))
    private val testFailureSoundRow = KindSoundRow(ErrorKind.TEST_FAILURE, JBCheckBox("Test failure"), ComboBox(soundChoices))
    private val networkSoundRow = KindSoundRow(ErrorKind.NETWORK, JBCheckBox("Network"), ComboBox(soundChoices))
    private val exceptionSoundRow = KindSoundRow(ErrorKind.EXCEPTION, JBCheckBox("Exception"), ComboBox(soundChoices))
    private val genericSoundRow = KindSoundRow(ErrorKind.GENERIC, JBCheckBox("Generic"), ComboBox(soundChoices))
    private val successSoundRow = KindSoundRow(ErrorKind.SUCCESS, JBCheckBox("Success"), ComboBox(soundChoices))
    private val soundRows = listOf(
        configurationSoundRow,
        compilationSoundRow,
        testFailureSoundRow,
        networkSoundRow,
        exceptionSoundRow,
        genericSoundRow,
        successSoundRow,
    )

    private val useVolumeOverridesCheckBox = JBCheckBox("Override volume")
    private val globalVolumeSlider = JBSlider(0, 100, 80)
    private val globalVolumeLabel = JBLabel("80%")
    private val configurationVolumeRow = KindVolumeRow(ErrorKind.CONFIGURATION, JBCheckBox("Configuration"), JBSlider(0, 100, 80), JBLabel("80%"))
    private val compilationVolumeRow = KindVolumeRow(ErrorKind.COMPILATION, JBCheckBox("Compilation"), JBSlider(0, 100, 80), JBLabel("80%"))
    private val testFailureVolumeRow = KindVolumeRow(ErrorKind.TEST_FAILURE, JBCheckBox("Test failure"), JBSlider(0, 100, 80), JBLabel("80%"))
    private val networkVolumeRow = KindVolumeRow(ErrorKind.NETWORK, JBCheckBox("Network"), JBSlider(0, 100, 80), JBLabel("80%"))
    private val exceptionVolumeRow = KindVolumeRow(ErrorKind.EXCEPTION, JBCheckBox("Exception"), JBSlider(0, 100, 80), JBLabel("80%"))
    private val genericVolumeRow = KindVolumeRow(ErrorKind.GENERIC, JBCheckBox("Generic"), JBSlider(0, 100, 80), JBLabel("80%"))
    private val successVolumeRow = KindVolumeRow(ErrorKind.SUCCESS, JBCheckBox("Success"), JBSlider(0, 100, 80), JBLabel("80%"))
    private val volumeRows = listOf(
        configurationVolumeRow,
        compilationVolumeRow,
        testFailureVolumeRow,
        networkVolumeRow,
        exceptionVolumeRow,
        genericVolumeRow,
        successVolumeRow,
    )

    private val useDurationOverridesCheckBox = JBCheckBox("Override alert duration and play-once mode")
    private val durationSpinner = JSpinner(SpinnerNumberModel(3, 1, 10, 1))
    private val useActualSoundDurationCheckBox = JBCheckBox("Use actual sound file duration (play once)")

    private val useVisualNotificationOverridesCheckBox = JBCheckBox("Override visual notification settings")
    private val showVisualNotificationCheckBox = JBCheckBox("Show balloon notifications")
    private val visualNotificationOnErrorCheckBox = JBCheckBox("Notify on errors")
    private val visualNotificationOnSuccessCheckBox = JBCheckBox("Notify on successes")

    private val useMinDurationOverrideCheckBox = JBCheckBox("Override minimum process duration")
    private val minDurationSpinner = JSpinner(SpinnerNumberModel(0, 0, 300, 1))

    private var updating = false

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        add(buildContent())
        bindEvents()
        RepoProfileService.getInstance(project).reload()
        refreshFromState()
    }

    fun refreshFromState() {
        updating = true
        try {
            val state = projectSettings.state
            val repoProfile = RepoProfileService.getInstance(project).load()
            repoProfileStatusLabel.text = repoProfileStatusText(repoProfile)
            openRepoProfileButton.isEnabled = repoProfile.isFilePresent

            useProfileOverridesCheckBox.isSelected = state.useProfileOverrides

            useEnabledOverrideCheckBox.isSelected = state.useOverride
            enabledOverrideCheckBox.isSelected = state.enabledOverride

            useMonitoringOverridesCheckBox.isSelected = state.useMonitoringOverrides
            monitorConfigurationCheckBox.isSelected = state.monitorConfigurationOverride
            monitorCompilationCheckBox.isSelected = state.monitorCompilationOverride
            monitorTestFailureCheckBox.isSelected = state.monitorTestFailureOverride
            monitorNetworkCheckBox.isSelected = state.monitorNetworkOverride
            monitorExceptionCheckBox.isSelected = state.monitorExceptionOverride
            monitorGenericCheckBox.isSelected = state.monitorGenericOverride
            monitorSuccessCheckBox.isSelected = state.monitorSuccessOverride

            useSoundOverridesCheckBox.isSelected = state.useSoundOverrides
            useGlobalBuiltInSoundCheckBox.isSelected = state.useGlobalBuiltInSoundOverride
            selectSound(globalSoundCombo, state.builtInSoundIdOverride)
            soundRows.forEach { row ->
                row.enabledCheckBox.isSelected = soundEnabled(state, row.kind)
                selectSound(row.combo, soundId(state, row.kind))
            }

            useVolumeOverridesCheckBox.isSelected = state.useVolumeOverrides
            setSlider(globalVolumeSlider, globalVolumeLabel, state.volumePercentOverride)
            volumeRows.forEach { row ->
                val value = volumeOverride(state, row.kind)
                row.useOverrideCheckBox.isSelected = value != null
                setSlider(row.slider, row.label, value ?: state.volumePercentOverride)
            }

            useDurationOverridesCheckBox.isSelected = state.useDurationOverrides
            durationSpinner.value = state.alertDurationSecondsOverride.coerceIn(1, 10)
            useActualSoundDurationCheckBox.isSelected = state.useActualSoundDurationOverride

            useVisualNotificationOverridesCheckBox.isSelected = state.useVisualNotificationOverrides
            showVisualNotificationCheckBox.isSelected = state.showVisualNotificationOverride
            visualNotificationOnErrorCheckBox.isSelected = state.visualNotificationOnErrorOverride
            visualNotificationOnSuccessCheckBox.isSelected = state.visualNotificationOnSuccessOverride

            useMinDurationOverrideCheckBox.isSelected = state.useMinProcessDurationOverride
            minDurationSpinner.value = state.minProcessDurationSecondsOverride.coerceIn(0, 300)
        } finally {
            updating = false
        }
        updateEnabledState()
    }

    private fun buildContent(): JComponent {
        val wrapper = JPanel()
        wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
        wrapper.isOpaque = false

        wrapper.add(left(hint("Unchecked means this project inherits the repo profile when present, otherwise global settings.")))
        wrapper.add(Box.createVerticalStrut(4))
        wrapper.add(left(hint("Workspace overrides win over the repo profile; unchecked fields inherit repo values when present, otherwise global.")))
        wrapper.add(Box.createVerticalStrut(4))
        wrapper.add(left(repoProfileStatusLabel.apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
        }))
        wrapper.add(Box.createVerticalStrut(4))
        wrapper.add(left(compactRow(reloadRepoProfileButton, openRepoProfileButton)))
        wrapper.add(Box.createVerticalStrut(6))
        wrapper.add(left(useProfileOverridesCheckBox))
        wrapper.add(Box.createVerticalStrut(4))
        wrapper.add(left(compactRow(copyGlobalButton, resetButton)))
        wrapper.add(Box.createVerticalStrut(8))
        wrapper.add(collapsibleSection("Master monitoring", true, useEnabledOverrideCheckBox, column(enabledOverrideCheckBox)))
        wrapper.add(collapsibleSection("Monitoring kinds", true, useMonitoringOverridesCheckBox, column(
            monitorConfigurationCheckBox,
            monitorCompilationCheckBox,
            monitorTestFailureCheckBox,
            monitorNetworkCheckBox,
            monitorExceptionCheckBox,
            monitorGenericCheckBox,
            monitorSuccessCheckBox,
        )))
        wrapper.add(collapsibleSection("Built-in sound behavior", false, useSoundOverridesCheckBox, buildSoundSection()))
        wrapper.add(collapsibleSection("Volume", false, useVolumeOverridesCheckBox, buildVolumeSection()))
        wrapper.add(collapsibleSection("Duration / Play once", false, useDurationOverridesCheckBox, column(
            stackedField("Alert duration (sec)", durationSpinner),
            useActualSoundDurationCheckBox,
        )))
        wrapper.add(collapsibleSection("Visual notifications", false, useVisualNotificationOverridesCheckBox, column(
            showVisualNotificationCheckBox,
            visualNotificationOnErrorCheckBox,
            visualNotificationOnSuccessCheckBox,
        )))
        wrapper.add(collapsibleSection("Minimum process duration", false, useMinDurationOverrideCheckBox, stackedField(
            "Minimum duration (sec)",
            minDurationSpinner,
        )))
        return wrapper
    }

    private fun buildSoundSection(): JComponent {
        val panel = column(
            useGlobalBuiltInSoundCheckBox,
            stackedField("Global built-in sound", globalSoundCombo),
        )
        soundRows.forEach { row ->
            panel.add(Box.createVerticalStrut(2))
            panel.add(compactField(row.enabledCheckBox.text, compactRow(row.enabledCheckBox, row.combo)))
        }
        return panel
    }

    private fun buildVolumeSection(): JComponent {
        val panel = column(stackedField("Global volume", sliderRow(globalVolumeSlider, globalVolumeLabel)))
        volumeRows.forEach { row ->
            panel.add(Box.createVerticalStrut(2))
            panel.add(compactField(row.useOverrideCheckBox.text, compactRow(row.useOverrideCheckBox, sliderRow(row.slider, row.label))))
        }
        return panel
    }

    private fun bindEvents() {
        useProfileOverridesCheckBox.addActionListener {
            mutate {
                it.useProfileOverrides = useProfileOverridesCheckBox.isSelected
                if (!it.useProfileOverrides) {
                    clearOverrideFlags(it)
                }
            }
        }
        copyGlobalButton.addActionListener {
            projectSettings.copyGlobalSettings(AlertSettings.getInstance().state)
            refreshFromState()
            onChange()
        }
        resetButton.addActionListener {
            projectSettings.resetOverrides()
            refreshFromState()
            onChange()
        }
        reloadRepoProfileButton.addActionListener {
            RepoProfileService.getInstance(project).reload()
            refreshFromState()
            onChange()
        }
        openRepoProfileButton.addActionListener {
            if (!RepoProfileService.getInstance(project).openProfileFile()) {
                Messages.showWarningDialog(
                    project,
                    "The repo profile file could not be opened.",
                    "Error Sound Alert",
                )
            }
        }

        useEnabledOverrideCheckBox.addActionListener { mutate { it.useOverride = useEnabledOverrideCheckBox.isSelected } }
        enabledOverrideCheckBox.addActionListener { mutate { it.enabledOverride = enabledOverrideCheckBox.isSelected } }

        useMonitoringOverridesCheckBox.addActionListener { mutate { it.useMonitoringOverrides = useMonitoringOverridesCheckBox.isSelected } }
        monitorConfigurationCheckBox.addActionListener { mutate { it.monitorConfigurationOverride = monitorConfigurationCheckBox.isSelected } }
        monitorCompilationCheckBox.addActionListener { mutate { it.monitorCompilationOverride = monitorCompilationCheckBox.isSelected } }
        monitorTestFailureCheckBox.addActionListener { mutate { it.monitorTestFailureOverride = monitorTestFailureCheckBox.isSelected } }
        monitorNetworkCheckBox.addActionListener { mutate { it.monitorNetworkOverride = monitorNetworkCheckBox.isSelected } }
        monitorExceptionCheckBox.addActionListener { mutate { it.monitorExceptionOverride = monitorExceptionCheckBox.isSelected } }
        monitorGenericCheckBox.addActionListener { mutate { it.monitorGenericOverride = monitorGenericCheckBox.isSelected } }
        monitorSuccessCheckBox.addActionListener { mutate { it.monitorSuccessOverride = monitorSuccessCheckBox.isSelected } }

        useSoundOverridesCheckBox.addActionListener { mutate { it.useSoundOverrides = useSoundOverridesCheckBox.isSelected } }
        useGlobalBuiltInSoundCheckBox.addActionListener { mutate { it.useGlobalBuiltInSoundOverride = useGlobalBuiltInSoundCheckBox.isSelected } }
        globalSoundCombo.addActionListener { mutate { it.builtInSoundIdOverride = selectedSoundId(globalSoundCombo) } }
        soundRows.forEach { row ->
            row.enabledCheckBox.addActionListener { mutate { setSoundEnabled(it, row.kind, row.enabledCheckBox.isSelected) } }
            row.combo.addActionListener { mutate { setSoundId(it, row.kind, selectedSoundId(row.combo)) } }
        }

        useVolumeOverridesCheckBox.addActionListener { mutate { it.useVolumeOverrides = useVolumeOverridesCheckBox.isSelected } }
        globalVolumeSlider.addChangeListener(ChangeListener {
            globalVolumeLabel.text = "${globalVolumeSlider.value}%"
            mutate { it.volumePercentOverride = globalVolumeSlider.value.coerceIn(0, 100) }
        })
        volumeRows.forEach { row ->
            row.useOverrideCheckBox.addActionListener {
                mutate { setVolumeOverride(it, row.kind, if (row.useOverrideCheckBox.isSelected) row.slider.value else null) }
            }
            row.slider.addChangeListener(ChangeListener {
                row.label.text = "${row.slider.value}%"
                mutate {
                    if (row.useOverrideCheckBox.isSelected) {
                        setVolumeOverride(it, row.kind, row.slider.value.coerceIn(0, 100))
                    }
                }
            })
        }

        useDurationOverridesCheckBox.addActionListener { mutate { it.useDurationOverrides = useDurationOverridesCheckBox.isSelected } }
        durationSpinner.addChangeListener { mutate { it.alertDurationSecondsOverride = (durationSpinner.value as? Int ?: 3).coerceIn(1, 10) } }
        useActualSoundDurationCheckBox.addActionListener { mutate { it.useActualSoundDurationOverride = useActualSoundDurationCheckBox.isSelected } }

        useVisualNotificationOverridesCheckBox.addActionListener {
            mutate { it.useVisualNotificationOverrides = useVisualNotificationOverridesCheckBox.isSelected }
        }
        showVisualNotificationCheckBox.addActionListener { mutate { it.showVisualNotificationOverride = showVisualNotificationCheckBox.isSelected } }
        visualNotificationOnErrorCheckBox.addActionListener { mutate { it.visualNotificationOnErrorOverride = visualNotificationOnErrorCheckBox.isSelected } }
        visualNotificationOnSuccessCheckBox.addActionListener { mutate { it.visualNotificationOnSuccessOverride = visualNotificationOnSuccessCheckBox.isSelected } }

        useMinDurationOverrideCheckBox.addActionListener {
            mutate { it.useMinProcessDurationOverride = useMinDurationOverrideCheckBox.isSelected }
        }
        minDurationSpinner.addChangeListener {
            mutate { it.minProcessDurationSecondsOverride = (minDurationSpinner.value as? Int ?: 0).coerceIn(0, 300) }
        }
    }

    private fun mutate(block: (ProjectAlertSettings.State) -> Unit) {
        if (updating) return
        block(projectSettings.state)
        updateEnabledState()
        onChange()
    }

    private fun updateEnabledState() {
        val profile = useProfileOverridesCheckBox.isSelected
        copyGlobalButton.isEnabled = true
        resetButton.isEnabled = true

        setEnabledRecursive(useEnabledOverrideCheckBox, profile)
        enabledOverrideCheckBox.isEnabled = profile && useEnabledOverrideCheckBox.isSelected

        setEnabledRecursive(useMonitoringOverridesCheckBox, profile)
        listOf(
            monitorConfigurationCheckBox,
            monitorCompilationCheckBox,
            monitorTestFailureCheckBox,
            monitorNetworkCheckBox,
            monitorExceptionCheckBox,
            monitorGenericCheckBox,
            monitorSuccessCheckBox,
        ).forEach { it.isEnabled = profile && useMonitoringOverridesCheckBox.isSelected }

        setEnabledRecursive(useSoundOverridesCheckBox, profile)
        val soundEnabled = profile && useSoundOverridesCheckBox.isSelected
        useGlobalBuiltInSoundCheckBox.isEnabled = soundEnabled
        globalSoundCombo.isEnabled = soundEnabled
        soundRows.forEach { row ->
            row.enabledCheckBox.isEnabled = soundEnabled && !useGlobalBuiltInSoundCheckBox.isSelected
            row.combo.isEnabled = soundEnabled && !useGlobalBuiltInSoundCheckBox.isSelected && row.enabledCheckBox.isSelected
        }

        setEnabledRecursive(useVolumeOverridesCheckBox, profile)
        val volumeEnabled = profile && useVolumeOverridesCheckBox.isSelected
        globalVolumeSlider.isEnabled = volumeEnabled
        globalVolumeLabel.isEnabled = volumeEnabled
        volumeRows.forEach { row ->
            row.useOverrideCheckBox.isEnabled = volumeEnabled
            row.slider.isEnabled = volumeEnabled && row.useOverrideCheckBox.isSelected
            row.label.isEnabled = volumeEnabled && row.useOverrideCheckBox.isSelected
        }

        setEnabledRecursive(useDurationOverridesCheckBox, profile)
        durationSpinner.isEnabled = profile && useDurationOverridesCheckBox.isSelected
        useActualSoundDurationCheckBox.isEnabled = profile && useDurationOverridesCheckBox.isSelected

        setEnabledRecursive(useVisualNotificationOverridesCheckBox, profile)
        val notificationsEnabled = profile && useVisualNotificationOverridesCheckBox.isSelected
        showVisualNotificationCheckBox.isEnabled = notificationsEnabled
        visualNotificationOnErrorCheckBox.isEnabled = notificationsEnabled && showVisualNotificationCheckBox.isSelected
        visualNotificationOnSuccessCheckBox.isEnabled = notificationsEnabled && showVisualNotificationCheckBox.isSelected

        setEnabledRecursive(useMinDurationOverrideCheckBox, profile)
        minDurationSpinner.isEnabled = profile && useMinDurationOverrideCheckBox.isSelected
    }

    private fun repoProfileStatusText(result: RepoProfileLoadResult): String {
        val warningSuffix = if (result.warnings.isEmpty()) "" else ", ${result.warnings.size} warning(s)"
        return when (result.status) {
            RepoProfileLoadResult.Status.NO_PROJECT_BASE_PATH -> "Repo profile: project root unavailable."
            RepoProfileLoadResult.Status.ABSENT -> "Repo profile: not found (${RepoProfileService.FILE_NAME})."
            RepoProfileLoadResult.Status.LOADED -> {
                val name = result.profile?.profileName?.takeIf { it.isNotBlank() } ?: "unnamed profile"
                "Repo profile: $name (schema v${result.profile?.schemaVersion ?: "?"}$warningSuffix)."
            }
            RepoProfileLoadResult.Status.DISABLED -> {
                val name = result.profile?.profileName?.takeIf { it.isNotBlank() } ?: "unnamed profile"
                "Repo profile: $name is disabled$warningSuffix."
            }
            RepoProfileLoadResult.Status.INVALID -> "Repo profile: invalid$warningSuffix."
        }
    }

    private fun clearOverrideFlags(state: ProjectAlertSettings.State) {
        state.useOverride = false
        state.useMonitoringOverrides = false
        state.useSoundOverrides = false
        state.useVolumeOverrides = false
        state.useDurationOverrides = false
        state.useVisualNotificationOverrides = false
        state.useMinProcessDurationOverride = false
    }

    private fun collapsibleSection(
        title: String,
        expandedByDefault: Boolean,
        overrideCheckBox: JBCheckBox,
        content: JComponent,
    ): JComponent {
        val child = column(overrideCheckBox, content)
        content.border = JBUI.Borders.emptyLeft(18)
        val section = CollapsibleSection(title, child, expandedByDefault)
        return section.panel
    }

    private fun compactField(label: String, component: JComponent): JComponent {
        val wrapper = JPanel()
        wrapper.layout = BoxLayout(wrapper, BoxLayout.Y_AXIS)
        wrapper.isOpaque = false
        wrapper.border = JBUI.Borders.empty(1, 0)
        wrapper.add(left(hint(label)))
        wrapper.add(left(component))
        return wrapper
    }

    private fun stackedField(label: String, component: JComponent): JComponent =
        compactField(label, component)

    private fun column(vararg components: JComponent): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        components.forEach { panel.add(left(fillWidth(it))) }
        return panel
    }

    private fun compactRow(vararg components: JComponent): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.isOpaque = false
        components.forEachIndexed { index, component ->
            if (index > 0) panel.add(Box.createHorizontalStrut(6))
            panel.add(component)
        }
        panel.maximumSize = panel.preferredSize
        return panel
    }

    private fun sliderRow(slider: JBSlider, label: JBLabel): JPanel {
        slider.paintTicks = true
        slider.majorTickSpacing = 25
        slider.preferredSize = java.awt.Dimension(132, slider.preferredSize.height)
        slider.maximumSize = slider.preferredSize
        return compactRow(slider, label)
    }

    private fun hint(text: String): JBLabel =
        JBLabel(text).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBFont.small()
        }

    private fun <T : JComponent> left(component: T): T {
        component.alignmentX = Component.LEFT_ALIGNMENT
        return component
    }

    private fun <T : JComponent> fillWidth(component: T): T {
        component.alignmentX = Component.LEFT_ALIGNMENT
        component.maximumSize = java.awt.Dimension(Int.MAX_VALUE, component.preferredSize.height)
        return component
    }

    private fun setEnabledRecursive(component: JComponent, enabled: Boolean) {
        component.isEnabled = enabled
    }

    private fun setSlider(slider: JBSlider, label: JBLabel, value: Int) {
        val bounded = value.coerceIn(0, 100)
        slider.value = bounded
        label.text = "$bounded%"
    }

    private fun selectSound(combo: ComboBox<BuiltInSound>, id: String) {
        val sound = BuiltInSounds.findByIdOrDefault(id)
        combo.selectedItem = sound
    }

    private fun selectedSoundId(combo: ComboBox<BuiltInSound>): String =
        (combo.selectedItem as? BuiltInSound)?.id ?: BuiltInSounds.default.id

    private fun soundEnabled(state: ProjectAlertSettings.State, kind: ErrorKind): Boolean = when (kind) {
        ErrorKind.CONFIGURATION -> state.configurationSoundEnabledOverride
        ErrorKind.COMPILATION -> state.compilationSoundEnabledOverride
        ErrorKind.TEST_FAILURE -> state.testFailureSoundEnabledOverride
        ErrorKind.NETWORK -> state.networkSoundEnabledOverride
        ErrorKind.EXCEPTION -> state.exceptionSoundEnabledOverride
        ErrorKind.GENERIC -> state.genericSoundEnabledOverride
        ErrorKind.SUCCESS -> state.successSoundEnabledOverride
        ErrorKind.NONE -> false
    }

    private fun setSoundEnabled(state: ProjectAlertSettings.State, kind: ErrorKind, enabled: Boolean) {
        when (kind) {
            ErrorKind.CONFIGURATION -> state.configurationSoundEnabledOverride = enabled
            ErrorKind.COMPILATION -> state.compilationSoundEnabledOverride = enabled
            ErrorKind.TEST_FAILURE -> state.testFailureSoundEnabledOverride = enabled
            ErrorKind.NETWORK -> state.networkSoundEnabledOverride = enabled
            ErrorKind.EXCEPTION -> state.exceptionSoundEnabledOverride = enabled
            ErrorKind.GENERIC -> state.genericSoundEnabledOverride = enabled
            ErrorKind.SUCCESS -> state.successSoundEnabledOverride = enabled
            ErrorKind.NONE -> Unit
        }
    }

    private fun soundId(state: ProjectAlertSettings.State, kind: ErrorKind): String = when (kind) {
        ErrorKind.CONFIGURATION -> state.configurationSoundIdOverride
        ErrorKind.COMPILATION -> state.compilationSoundIdOverride
        ErrorKind.TEST_FAILURE -> state.testFailureSoundIdOverride
        ErrorKind.NETWORK -> state.networkSoundIdOverride
        ErrorKind.EXCEPTION -> state.exceptionSoundIdOverride
        ErrorKind.GENERIC -> state.genericSoundIdOverride
        ErrorKind.SUCCESS -> state.successSoundIdOverride
        ErrorKind.NONE -> BuiltInSounds.default.id
    }

    private fun setSoundId(state: ProjectAlertSettings.State, kind: ErrorKind, soundId: String) {
        when (kind) {
            ErrorKind.CONFIGURATION -> state.configurationSoundIdOverride = soundId
            ErrorKind.COMPILATION -> state.compilationSoundIdOverride = soundId
            ErrorKind.TEST_FAILURE -> state.testFailureSoundIdOverride = soundId
            ErrorKind.NETWORK -> state.networkSoundIdOverride = soundId
            ErrorKind.EXCEPTION -> state.exceptionSoundIdOverride = soundId
            ErrorKind.GENERIC -> state.genericSoundIdOverride = soundId
            ErrorKind.SUCCESS -> state.successSoundIdOverride = soundId
            ErrorKind.NONE -> Unit
        }
    }

    private fun volumeOverride(state: ProjectAlertSettings.State, kind: ErrorKind): Int? = when (kind) {
        ErrorKind.CONFIGURATION -> state.configurationVolumePercentOverride
        ErrorKind.COMPILATION -> state.compilationVolumePercentOverride
        ErrorKind.TEST_FAILURE -> state.testFailureVolumePercentOverride
        ErrorKind.NETWORK -> state.networkVolumePercentOverride
        ErrorKind.EXCEPTION -> state.exceptionVolumePercentOverride
        ErrorKind.GENERIC -> state.genericVolumePercentOverride
        ErrorKind.SUCCESS -> state.successVolumePercentOverride
        ErrorKind.NONE -> null
    }

    private fun setVolumeOverride(state: ProjectAlertSettings.State, kind: ErrorKind, value: Int?) {
        val bounded = value?.coerceIn(0, 100)
        when (kind) {
            ErrorKind.CONFIGURATION -> state.configurationVolumePercentOverride = bounded
            ErrorKind.COMPILATION -> state.compilationVolumePercentOverride = bounded
            ErrorKind.TEST_FAILURE -> state.testFailureVolumePercentOverride = bounded
            ErrorKind.NETWORK -> state.networkVolumePercentOverride = bounded
            ErrorKind.EXCEPTION -> state.exceptionVolumePercentOverride = bounded
            ErrorKind.GENERIC -> state.genericVolumePercentOverride = bounded
            ErrorKind.SUCCESS -> state.successVolumePercentOverride = bounded
            ErrorKind.NONE -> Unit
        }
    }

    private data class KindSoundRow(
        val kind: ErrorKind,
        val enabledCheckBox: JBCheckBox,
        val combo: ComboBox<BuiltInSound>,
    )

    private data class KindVolumeRow(
        val kind: ErrorKind,
        val useOverrideCheckBox: JBCheckBox,
        val slider: JBSlider,
        val label: JBLabel,
    )

    private inner class CollapsibleSection(
        private val title: String,
        private val content: JComponent,
        expandedByDefault: Boolean,
    ) {
        private var expanded = expandedByDefault
        private val header = JButton().apply {
            horizontalAlignment = SwingConstants.LEFT
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            border = JBUI.Borders.empty(2, 0)
            font = JBFont.small().deriveFont(Font.BOLD)
            addActionListener { toggle() }
        }

        val panel: JPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(4, 0, 4, 0)
            add(left(fillWidth(header)))
            add(left(fillWidth(content)))
        }

        init {
            update()
        }

        private fun toggle() {
            expanded = !expanded
            update()
            this@ProjectProfilePanel.revalidate()
            this@ProjectProfilePanel.repaint()
            panel.revalidate()
            panel.repaint()
        }

        private fun update() {
            header.text = if (expanded) "[v] $title" else "[>] $title"
            content.isVisible = expanded
        }
    }
}
