package com.drostwades.errorsound

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Project-scoped alert settings.
 *
 * Persisted to workspace storage so that overrides are per-workspace (not shared across
 * all clones of the same project).
 *
 * Persisted values are opt-in overrides layered on top of global [AlertSettings].
 * Legacy Phase 7 fields [State.useOverride] and [State.enabledOverride] are preserved
 * so old workspace files that only override the master enabled flag continue to load.
 */
@State(
    name = "ErrorSoundProjectAlertSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
@Service(Service.Level.PROJECT)
class ProjectAlertSettings : PersistentStateComponent<ProjectAlertSettings.State> {

    /**
     * Per-project override state.
     *
     * Two fields model a nullable boolean (XML serialization cannot represent `Boolean?` directly):
     * - [useOverride] == `false` → override inactive; [enabledOverride] is ignored; effective enabled == global
     * - [useOverride] == `true`  → override active; effective enabled == [enabledOverride]
     */
    data class State(
        /**
         * Master switch for Phase 9 project profile overrides.
         * When `false`, all project override fields are ignored and global settings are used.
         */
        var useProfileOverrides: Boolean = false,

        /**
         * Workspace-scoped Phase 11 merge policy for global, repo, and project profile layers.
         * Stored as a string for resilient XML serialization; unknown values normalize to default.
         */
        var profileMergePolicy: String = ProfileMergePolicy.default.name,

        /**
         * Whether the project-level override for `enabled` is active.
         * When `false`, `enabledOverride` is ignored and the global value is used.
         */
        var useOverride: Boolean = false,

        /**
         * The override value for `enabled` when [useOverride] is `true`.
         * Ignored when [useOverride] is `false`.
         */
        var enabledOverride: Boolean = true,

        var useMonitoringOverrides: Boolean = false,
        var monitorConfigurationOverride: Boolean = true,
        var monitorCompilationOverride: Boolean = true,
        var monitorTestFailureOverride: Boolean = true,
        var monitorNetworkOverride: Boolean = true,
        var monitorExceptionOverride: Boolean = true,
        var monitorGenericOverride: Boolean = true,
        var monitorSuccessOverride: Boolean = false,

        var useSoundOverrides: Boolean = false,
        var useGlobalBuiltInSoundOverride: Boolean = true,
        var builtInSoundIdOverride: String = BuiltInSounds.default.id,
        var configurationSoundEnabledOverride: Boolean = true,
        var configurationSoundIdOverride: String = "huh",
        var compilationSoundEnabledOverride: Boolean = true,
        var compilationSoundIdOverride: String = "punch",
        var testFailureSoundEnabledOverride: Boolean = true,
        var testFailureSoundIdOverride: String = "dog_laughing_meme",
        var networkSoundEnabledOverride: Boolean = true,
        var networkSoundIdOverride: String = "yooo",
        var exceptionSoundEnabledOverride: Boolean = true,
        var exceptionSoundIdOverride: String = "boom",
        var genericSoundEnabledOverride: Boolean = true,
        var genericSoundIdOverride: String = BuiltInSounds.default.id,
        var successSoundEnabledOverride: Boolean = false,
        var successSoundIdOverride: String = "yeah_boy",

        var useVolumeOverrides: Boolean = false,
        var volumePercentOverride: Int = 80,
        var configurationVolumePercentOverride: Int? = null,
        var compilationVolumePercentOverride: Int? = null,
        var testFailureVolumePercentOverride: Int? = null,
        var networkVolumePercentOverride: Int? = null,
        var exceptionVolumePercentOverride: Int? = null,
        var genericVolumePercentOverride: Int? = null,
        var successVolumePercentOverride: Int? = null,

        var useDurationOverrides: Boolean = false,
        var alertDurationSecondsOverride: Int = 3,
        var useActualSoundDurationOverride: Boolean = false,

        var useVisualNotificationOverrides: Boolean = false,
        var showVisualNotificationOverride: Boolean = false,
        var visualNotificationOnErrorOverride: Boolean = true,
        var visualNotificationOnSuccessOverride: Boolean = true,

        var useMinProcessDurationOverride: Boolean = false,
        var minProcessDurationSecondsOverride: Int = 0,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = normalize(state)
    }

    /**
     * Returns the effective nullable override:
     * - `null`  if [useOverride] is false (inherit from global)
     * - `true`  if [useOverride] is true and [enabledOverride] is true
     * - `false` if [useOverride] is true and [enabledOverride] is false
     */
    fun effectiveEnabledOverride(): Boolean? =
        if (state.useProfileOverrides && state.useOverride) state.enabledOverride else null

    fun resetOverrides() {
        state = State(profileMergePolicy = mergePolicy().name)
    }

    fun copyGlobalSettings(global: AlertSettings.State) {
        val currentMergePolicy = mergePolicy().name
        state = State(
            useProfileOverrides = true,
            profileMergePolicy = currentMergePolicy,
            useOverride = true,
            enabledOverride = global.enabled,
            useMonitoringOverrides = true,
            monitorConfigurationOverride = global.monitorConfiguration,
            monitorCompilationOverride = global.monitorCompilation,
            monitorTestFailureOverride = global.monitorTestFailure,
            monitorNetworkOverride = global.monitorNetwork,
            monitorExceptionOverride = global.monitorException,
            monitorGenericOverride = global.monitorGeneric,
            monitorSuccessOverride = global.monitorSuccess,
            useSoundOverrides = true,
            useGlobalBuiltInSoundOverride = global.useGlobalBuiltInSound,
            builtInSoundIdOverride = normalizeSoundId(global.builtInSoundId),
            configurationSoundEnabledOverride = global.configurationSoundEnabled,
            configurationSoundIdOverride = normalizeSoundId(global.configurationSoundId),
            compilationSoundEnabledOverride = global.compilationSoundEnabled,
            compilationSoundIdOverride = normalizeSoundId(global.compilationSoundId),
            testFailureSoundEnabledOverride = global.testFailureSoundEnabled,
            testFailureSoundIdOverride = normalizeSoundId(global.testFailureSoundId),
            networkSoundEnabledOverride = global.networkSoundEnabled,
            networkSoundIdOverride = normalizeSoundId(global.networkSoundId),
            exceptionSoundEnabledOverride = global.exceptionSoundEnabled,
            exceptionSoundIdOverride = normalizeSoundId(global.exceptionSoundId),
            genericSoundEnabledOverride = global.genericSoundEnabled,
            genericSoundIdOverride = normalizeSoundId(global.genericSoundId),
            successSoundEnabledOverride = global.successSoundEnabled,
            successSoundIdOverride = normalizeSoundId(global.successSoundId),
            useVolumeOverrides = true,
            volumePercentOverride = global.volumePercent.coerceIn(0, 100),
            configurationVolumePercentOverride = global.configurationVolumePercent?.coerceIn(0, 100),
            compilationVolumePercentOverride = global.compilationVolumePercent?.coerceIn(0, 100),
            testFailureVolumePercentOverride = global.testFailureVolumePercent?.coerceIn(0, 100),
            networkVolumePercentOverride = global.networkVolumePercent?.coerceIn(0, 100),
            exceptionVolumePercentOverride = global.exceptionVolumePercent?.coerceIn(0, 100),
            genericVolumePercentOverride = global.genericVolumePercent?.coerceIn(0, 100),
            successVolumePercentOverride = global.successVolumePercent?.coerceIn(0, 100),
            useDurationOverrides = true,
            alertDurationSecondsOverride = global.alertDurationSeconds.coerceIn(1, 10),
            useActualSoundDurationOverride = global.useActualSoundDuration,
            useVisualNotificationOverrides = true,
            showVisualNotificationOverride = global.showVisualNotification,
            visualNotificationOnErrorOverride = global.visualNotificationOnError,
            visualNotificationOnSuccessOverride = global.visualNotificationOnSuccess,
            useMinProcessDurationOverride = true,
            minProcessDurationSecondsOverride = global.minProcessDurationSeconds.coerceIn(0, 300),
        )
    }

    fun activeOverrideLabels(): List<String> {
        if (!state.useProfileOverrides) return emptyList()
        val labels = mutableListOf<String>()
        if (state.useOverride) labels += "master enabled"
        if (state.useMonitoringOverrides) labels += "monitoring kinds"
        if (state.useSoundOverrides) labels += "sound behavior"
        if (state.useVolumeOverrides) labels += "volume"
        if (state.useDurationOverrides) labels += "duration"
        if (state.useVisualNotificationOverrides) labels += "visual notifications"
        if (state.useMinProcessDurationOverride) labels += "minimum process duration"
        return labels
    }

    fun mergePolicy(): ProfileMergePolicy = ProfileMergePolicy.fromStored(state.profileMergePolicy)

    private fun normalize(input: State): State {
        val migratedProfileEnabled = input.useProfileOverrides || input.useOverride
        return input.copy(
            useProfileOverrides = migratedProfileEnabled,
            profileMergePolicy = ProfileMergePolicy.fromStored(input.profileMergePolicy).name,
            builtInSoundIdOverride = normalizeSoundId(input.builtInSoundIdOverride),
            configurationSoundIdOverride = normalizeSoundId(input.configurationSoundIdOverride),
            compilationSoundIdOverride = normalizeSoundId(input.compilationSoundIdOverride),
            testFailureSoundIdOverride = normalizeSoundId(input.testFailureSoundIdOverride),
            networkSoundIdOverride = normalizeSoundId(input.networkSoundIdOverride),
            exceptionSoundIdOverride = normalizeSoundId(input.exceptionSoundIdOverride),
            genericSoundIdOverride = normalizeSoundId(input.genericSoundIdOverride),
            successSoundIdOverride = normalizeSoundId(input.successSoundIdOverride),
            volumePercentOverride = input.volumePercentOverride.coerceIn(0, 100),
            configurationVolumePercentOverride = input.configurationVolumePercentOverride?.coerceIn(0, 100),
            compilationVolumePercentOverride = input.compilationVolumePercentOverride?.coerceIn(0, 100),
            testFailureVolumePercentOverride = input.testFailureVolumePercentOverride?.coerceIn(0, 100),
            networkVolumePercentOverride = input.networkVolumePercentOverride?.coerceIn(0, 100),
            exceptionVolumePercentOverride = input.exceptionVolumePercentOverride?.coerceIn(0, 100),
            genericVolumePercentOverride = input.genericVolumePercentOverride?.coerceIn(0, 100),
            successVolumePercentOverride = input.successVolumePercentOverride?.coerceIn(0, 100),
            alertDurationSecondsOverride = input.alertDurationSecondsOverride.coerceIn(1, 10),
            minProcessDurationSecondsOverride = input.minProcessDurationSecondsOverride.coerceIn(0, 300),
        )
    }

    private fun normalizeSoundId(id: String): String {
        return if (id == BuiltInSounds.CUSTOM_FILE_ID) {
            BuiltInSounds.CUSTOM_FILE_ID
        } else {
            BuiltInSounds.findByIdOrDefault(id).id
        }
    }

    companion object {
        fun getInstance(project: Project): ProjectAlertSettings =
            project.getService(ProjectAlertSettings::class.java)
    }
}
