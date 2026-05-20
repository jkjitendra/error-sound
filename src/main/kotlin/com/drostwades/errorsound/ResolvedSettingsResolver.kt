package com.drostwades.errorsound

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Project service that resolves the *effective* [AlertSettings.State] for a given project
 * by layering repo-shared and project-level overrides on top of application-level settings.
 *
 * Callers should obtain the resolved state via [resolve] instead of reading
 * `AlertSettings.getInstance().state` directly.
 */
@Service(Service.Level.PROJECT)
class ResolvedSettingsResolver(private val project: Project) {

    /**
     * Returns an [AlertSettings.State] that reflects the effective settings for [project].
     *
     * The returned state is **always a copy** — mutating it has no side effects on stored settings,
     * regardless of whether a project override is active.
     *
     * Repo profile and project profile settings are opt-in by field/category. Rule collections
     * and custom file path stay global in Phase 10.
     */
    fun resolve(): AlertSettings.State {
        val global = AlertSettings.getInstance().state
        var resolved = global.copy()

        val repoResult = RepoProfileService.getInstance(project).load()
        if (repoResult.isApplied) {
            repoResult.profile?.let { resolved = it.applyTo(resolved) }
        }

        val projectState = ProjectAlertSettings.getInstance(project).state
        if (!projectState.useProfileOverrides) {
            return resolved
        }

        if (projectState.useOverride) {
            resolved = resolved.copy(enabled = projectState.enabledOverride)
        }

        if (projectState.useMonitoringOverrides) {
            resolved = resolved.copy(
                monitorConfiguration = projectState.monitorConfigurationOverride,
                monitorCompilation = projectState.monitorCompilationOverride,
                monitorTestFailure = projectState.monitorTestFailureOverride,
                monitorNetwork = projectState.monitorNetworkOverride,
                monitorException = projectState.monitorExceptionOverride,
                monitorGeneric = projectState.monitorGenericOverride,
                monitorSuccess = projectState.monitorSuccessOverride,
            )
        }

        if (projectState.useSoundOverrides) {
            resolved = resolved.copy(
                useGlobalBuiltInSound = projectState.useGlobalBuiltInSoundOverride,
                builtInSoundId = projectState.builtInSoundIdOverride,
                configurationSoundEnabled = projectState.configurationSoundEnabledOverride,
                configurationSoundId = projectState.configurationSoundIdOverride,
                compilationSoundEnabled = projectState.compilationSoundEnabledOverride,
                compilationSoundId = projectState.compilationSoundIdOverride,
                testFailureSoundEnabled = projectState.testFailureSoundEnabledOverride,
                testFailureSoundId = projectState.testFailureSoundIdOverride,
                networkSoundEnabled = projectState.networkSoundEnabledOverride,
                networkSoundId = projectState.networkSoundIdOverride,
                exceptionSoundEnabled = projectState.exceptionSoundEnabledOverride,
                exceptionSoundId = projectState.exceptionSoundIdOverride,
                genericSoundEnabled = projectState.genericSoundEnabledOverride,
                genericSoundId = projectState.genericSoundIdOverride,
                successSoundEnabled = projectState.successSoundEnabledOverride,
                successSoundId = projectState.successSoundIdOverride,
            )
        }

        if (projectState.useVolumeOverrides) {
            resolved = resolved.copy(
                volumePercent = projectState.volumePercentOverride.coerceIn(0, 100),
                configurationVolumePercent = projectState.configurationVolumePercentOverride?.coerceIn(0, 100),
                compilationVolumePercent = projectState.compilationVolumePercentOverride?.coerceIn(0, 100),
                testFailureVolumePercent = projectState.testFailureVolumePercentOverride?.coerceIn(0, 100),
                networkVolumePercent = projectState.networkVolumePercentOverride?.coerceIn(0, 100),
                exceptionVolumePercent = projectState.exceptionVolumePercentOverride?.coerceIn(0, 100),
                genericVolumePercent = projectState.genericVolumePercentOverride?.coerceIn(0, 100),
                successVolumePercent = projectState.successVolumePercentOverride?.coerceIn(0, 100),
            )
        }

        if (projectState.useDurationOverrides) {
            resolved = resolved.copy(
                alertDurationSeconds = projectState.alertDurationSecondsOverride.coerceIn(1, 10),
                useActualSoundDuration = projectState.useActualSoundDurationOverride,
            )
        }

        if (projectState.useVisualNotificationOverrides) {
            resolved = resolved.copy(
                showVisualNotification = projectState.showVisualNotificationOverride,
                visualNotificationOnError = projectState.visualNotificationOnErrorOverride,
                visualNotificationOnSuccess = projectState.visualNotificationOnSuccessOverride,
            )
        }

        if (projectState.useMinProcessDurationOverride) {
            resolved = resolved.copy(
                minProcessDurationSeconds = projectState.minProcessDurationSecondsOverride.coerceIn(0, 300),
            )
        }

        return resolved
    }

    companion object {
        fun getInstance(project: Project): ResolvedSettingsResolver =
            project.getService(ResolvedSettingsResolver::class.java)
    }
}
