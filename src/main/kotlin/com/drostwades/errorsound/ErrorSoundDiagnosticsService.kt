package com.drostwades.errorsound

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import java.io.File

object ErrorSoundDiagnosticsService {

    private val log = Logger.getInstance(ErrorSoundDiagnosticsService::class.java)

    data class Snapshot(
        val rows: List<Pair<String, String>>,
        val notes: List<String>,
    )

    data class SelfTestResult(
        val success: Boolean,
        val message: String,
    )

    fun buildSnapshot(): Snapshot {
        val state = AlertSettings.getInstance().state
        val activeProject = resolveNotificationProject(null)
        val projectProfileStatus = activeProject?.let { project ->
            val labels = ProjectAlertSettings.getInstance(project).activeOverrideLabels()
            if (labels.isEmpty()) {
                "Inactive for active project: ${project.name}"
            } else {
                "Active for active project: ${project.name} (${labels.joinToString(", ")})"
            }
        } ?: "No active project context found"
        val customPath = state.customSoundPath.trim()
        val customFileStatus = when {
            customPath.isBlank() -> "Blank"
            File(customPath).isFile -> "Set (file exists)"
            else -> "Set (file not found)"
        }

        return Snapshot(
            rows = listOf(
                "Global monitoring" to enabledLabel(state.enabled),
                "Project profile overrides" to projectProfileStatus,
                "Snooze" to (SnoozeState.statusLabel() ?: "Inactive"),
                "Visual notifications" to enabledLabel(state.showVisualNotification),
                "Notify on errors" to enabledLabel(state.visualNotificationOnError),
                "Notify on successes" to enabledLabel(state.visualNotificationOnSuccess),
                "Sound source" to state.soundSource,
                "Selected global sound" to BuiltInSounds.findByIdOrDefault(state.builtInSoundId).displayName,
                "Custom file path" to customFileStatus,
                "Global volume" to "${state.volumePercent.coerceIn(0, 100)}%",
                "Alert duration" to "${state.alertDurationSeconds.coerceIn(1, 10)} sec",
                "Use actual sound duration" to enabledLabel(state.useActualSoundDuration),
                "Alert History entries" to AlertHistoryService.getInstance().snapshot().size.toString(),
                "Custom regex rules" to state.customRules.size.toString(),
                "Suppression rules" to state.suppressionRules.size.toString(),
                "Terminal exit-code rules" to state.exitCodeRules.size.toString(),
                "Rule presets" to "${RulePresetService.bundles.size} bundled preset bundle(s) available",
                "Rule import/export schema" to "Exports v2; imports v1 and v2",
                "Terminal integration" to "Optional terminal plugin integration configured; no reflection self-test is run",
            ),
            notes = listOf(
                "Diagnostics are local only and do not write files, send telemetry, or use the network.",
                "Sound self-tests use preview playback and do not create Alert History entries.",
                "Visual notification self-test uses the existing Error Sound Alert notification group.",
            ),
        )
    }

    fun testErrorSound(): SelfTestResult =
        testSound(ErrorKind.GENERIC, "GENERIC error sound")

    fun testSuccessSound(): SelfTestResult =
        testSound(ErrorKind.SUCCESS, "SUCCESS sound")

    fun showTestNotification(project: Project? = null): SelfTestResult {
        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup("Error Sound Alert")
            ?: return SelfTestResult(false, "Diagnostic notification group is unavailable.")

        val notificationProject = resolveNotificationProject(project)

        val application = ApplicationManager.getApplication()
        val notifyAction = {
            group.createNotification(
                "Diagnostics / Self-Test",
                "Diagnostic visual notification test.",
                NotificationType.INFORMATION,
            ).notify(notificationProject)
        }
        try {
            if (application.isDispatchThread) {
                notifyAction()
            } else {
                application.invokeLater {
                    runCatching { notifyAction() }
                        .onFailure { log.warn("Diagnostic notification could not be sent", it) }
                }
            }
        } catch (t: Throwable) {
            log.warn("Diagnostic notification could not be sent", t)
            return SelfTestResult(false, "Diagnostic notification could not be sent.")
        }

        return when {
            notificationProject != null ->
                SelfTestResult(true, "Diagnostic notification sent using active project: ${notificationProject.name}.")
            else ->
                SelfTestResult(true, "No active project context found; notification sent without project context.")
        }
    }

    private fun resolveNotificationProject(project: Project?): Project? {
        if (project != null && !project.isDisposed) {
            return project
        }
        return ProjectManager.getInstance().openProjects.firstOrNull { !it.isDisposed }
    }

    private fun testSound(kind: ErrorKind, label: String): SelfTestResult {
        val state = AlertSettings.getInstance().state
        if (!isKindSoundEnabled(state, kind)) {
            return SelfTestResult(false, "$label is disabled by the current sound settings.")
        }

        val volume = ErrorSoundPlayer.resolveEffectiveVolumePercent(state, kind)
        val duration = state.alertDurationSeconds
        val useActualDuration = state.useActualSoundDuration

        if (state.soundSource == AlertSettings.SoundSource.CUSTOM.name) {
            val customPath = state.customSoundPath.trim()
            if (customPath.isBlank()) {
                return SelfTestResult(false, "Custom sound source is selected, but no custom file path is set.")
            }
            if (!File(customPath).isFile) {
                return SelfTestResult(false, "Custom sound file was not found.")
            }
            ErrorSoundPlayer.previewCustom(customPath, volume, duration, useActualDuration)
            return SelfTestResult(true, "Playing $label using the configured custom sound file.")
        }

        val soundId = resolveBuiltInSoundId(state, kind)
        if (soundId == BuiltInSounds.CUSTOM_FILE_ID) {
            val customPath = state.customSoundPath.trim()
            if (customPath.isBlank() || !File(customPath).isFile) {
                return SelfTestResult(false, "This kind is mapped to the custom file, but the custom file is not available.")
            }
            ErrorSoundPlayer.previewCustom(customPath, volume, duration, useActualDuration)
            return SelfTestResult(true, "Playing $label using the configured custom sound file.")
        }

        val sound = BuiltInSounds.findByIdOrDefault(soundId)
        ErrorSoundPlayer.previewBuiltIn(sound.id, volume, duration, useActualDuration)
        return SelfTestResult(true, "Playing $label using ${sound.displayName}.")
    }

    private fun isKindSoundEnabled(settings: AlertSettings.State, kind: ErrorKind): Boolean {
        if (settings.useGlobalBuiltInSound) {
            return kind != ErrorKind.NONE
        }
        return when (kind) {
            ErrorKind.CONFIGURATION -> settings.configurationSoundEnabled
            ErrorKind.COMPILATION -> settings.compilationSoundEnabled
            ErrorKind.TEST_FAILURE -> settings.testFailureSoundEnabled
            ErrorKind.NETWORK -> settings.networkSoundEnabled
            ErrorKind.EXCEPTION -> settings.exceptionSoundEnabled
            ErrorKind.GENERIC -> settings.genericSoundEnabled
            ErrorKind.SUCCESS -> settings.successSoundEnabled
            ErrorKind.NONE -> false
        }
    }

    private fun resolveBuiltInSoundId(settings: AlertSettings.State, kind: ErrorKind): String {
        if (settings.useGlobalBuiltInSound) {
            return settings.builtInSoundId
        }
        return when (kind) {
            ErrorKind.CONFIGURATION -> settings.configurationSoundId
            ErrorKind.COMPILATION -> settings.compilationSoundId
            ErrorKind.TEST_FAILURE -> settings.testFailureSoundId
            ErrorKind.NETWORK -> settings.networkSoundId
            ErrorKind.EXCEPTION -> settings.exceptionSoundId
            ErrorKind.GENERIC -> settings.genericSoundId
            ErrorKind.SUCCESS -> settings.successSoundId
            ErrorKind.NONE -> settings.genericSoundId
        }
    }

    private fun enabledLabel(value: Boolean): String = if (value) "Enabled" else "Disabled"
}
