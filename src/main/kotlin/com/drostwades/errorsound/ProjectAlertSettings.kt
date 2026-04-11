package com.drostwades.errorsound

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Project-scoped alert settings (Phase 7 — Project-Level Profiles).
 *
 * Persisted to workspace storage so that overrides are per-workspace (not shared across
 * all clones of the same project).
 *
 * ## Two-field override model
 * Whether the override is active and its value are tracked as two separate boolean fields
 * rather than a single `Boolean?`, because the XML serializer used by
 * `PersistentStateComponent` cannot represent nullable primitives directly.
 *
 * - [State.useOverride] == `false` → no project override; inherit global [AlertSettings.State.enabled]
 * - [State.useOverride] == `true`  → project override is active; [State.enabledOverride] is the effective value
 *
 * Use [effectiveEnabledOverride] to obtain the nullable view (`null` = inherit global).
 *
 * **Phase 7 scope:** Only the `enabled` flag can be overridden here. All other settings
 * (sounds, per-kind flags, custom rules, exit-code rules) come from [AlertSettings] only.
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
         * Whether the project-level override for `enabled` is active.
         * When `false`, `enabledOverride` is ignored and the global value is used.
         */
        var useOverride: Boolean = false,

        /**
         * The override value for `enabled` when [useOverride] is `true`.
         * Ignored when [useOverride] is `false`.
         */
        var enabledOverride: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * Returns the effective nullable override:
     * - `null`  if [useOverride] is false (inherit from global)
     * - `true`  if [useOverride] is true and [enabledOverride] is true
     * - `false` if [useOverride] is true and [enabledOverride] is false
     */
    fun effectiveEnabledOverride(): Boolean? =
        if (state.useOverride) state.enabledOverride else null

    companion object {
        fun getInstance(project: Project): ProjectAlertSettings =
            project.getService(ProjectAlertSettings::class.java)
    }
}
