package com.drostwades.errorsound

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Project service that resolves the *effective* [AlertSettings.State] for a given project
 * by merging project-level overrides on top of application-level settings (Phase 7).
 *
 * ## Phase 7 merge rules
 * - **enabled**: resolved per-project (see below); all other fields come from global settings.
 * - **Everything else** (sounds, per-kind flags, custom rules, exit-code rules, …) is intentionally
 *   NOT overridable at the project level in Phase 7.
 *
 * ## Effective `enabled` resolution
 * ```
 * ProjectAlertSettings.effectiveEnabledOverride() == null  →  use AlertSettings.state.enabled
 * ProjectAlertSettings.effectiveEnabledOverride() == true  →  enabled = true  (regardless of global)
 * ProjectAlertSettings.effectiveEnabledOverride() == false →  enabled = false (regardless of global)
 * ```
 *
 * Callers should obtain the resolved state via [resolve] instead of reading
 * `AlertSettings.getInstance().state` directly — this ensures the project-level `enabled`
 * override is honoured.
 */
@Service(Service.Level.PROJECT)
class ResolvedSettingsResolver(private val project: Project) {

    /**
     * Returns an [AlertSettings.State] that reflects the effective settings for [project].
     *
     * The returned state is **always a copy** — mutating it has no side effects on stored settings,
     * regardless of whether a project override is active.
     *
     * Only the [AlertSettings.State.enabled] field may differ from the global state.
     * All other fields are copied unchanged from [AlertSettings.getInstance().state].
     */
    fun resolve(): AlertSettings.State {
        val global = AlertSettings.getInstance().state
        val override = ProjectAlertSettings.getInstance(project).effectiveEnabledOverride()
        return global.copy(enabled = override ?: global.enabled)
    }

    companion object {
        fun getInstance(project: Project): ResolvedSettingsResolver =
            project.getService(ResolvedSettingsResolver::class.java)
    }
}
