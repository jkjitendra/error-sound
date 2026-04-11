package com.drostwades.errorsound

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.util.UUID

@State(name = "ErrorSoundAlertSettings", storages = [Storage("errorSoundAlert.xml")])
@Service(Service.Level.APP)
class AlertSettings : PersistentStateComponent<AlertSettings.State> {

    /** Where a custom regex rule pattern is applied when classifying output. */
    enum class MatchTarget { LINE_TEXT, FULL_OUTPUT, EXIT_CODE_AND_TEXT }

    /**
     * A single user-defined regex rule (Phase 5).
     * All fields have defaults so IntelliJ's XML serializer can create instances without arguments.
     */
    data class CustomRuleState(
        var id: String = UUID.randomUUID().toString(),
        var enabled: Boolean = true,
        var pattern: String = "",
        var matchTarget: String = MatchTarget.LINE_TEXT.name,
        var kind: String = ErrorKind.GENERIC.name,
    )

    /**
     * A single exit-code-to-kind mapping rule for terminal alerts (Phase 6).
     * All fields have defaults for XML serializer compatibility.
     *
     * - [suppress] = true means the alert is silenced for this exit code entirely.
     * - [soundId]  = non-null overrides the built-in sound used for this event only;
     *               null means use normal kind/global resolution.
     */
    data class ExitCodeRuleState(
        var exitCode: Int = 0,
        var enabled: Boolean = true,
        var kind: String = ErrorKind.GENERIC.name,
        var soundId: String? = null,
        var suppress: Boolean = false,
    )

    data class State(
        var enabled: Boolean = true,

        // Monitoring filter flags (sidebar panel controls)
        var monitorConfiguration: Boolean = true,
        var monitorCompilation: Boolean = true,
        var monitorTestFailure: Boolean = true,
        var monitorNetwork: Boolean = true,
        var monitorException: Boolean = true,
        var monitorGeneric: Boolean = true,
        var monitorSuccess: Boolean = false,

        var volumePercent: Int = 80,

        // Per-kind volume overrides (Phase 8 — Per-Kind Volume).
        // null = inherit the global volumePercent; non-null overrides playback volume for that kind only.
        // Applies regardless of sound-source choice and global built-in mode.
        var configurationVolumePercent: Int? = null,
        var compilationVolumePercent:   Int? = null,
        var testFailureVolumePercent:   Int? = null,
        var networkVolumePercent:       Int? = null,
        var exceptionVolumePercent:     Int? = null,
        var genericVolumePercent:       Int? = null,
        var successVolumePercent:       Int? = null,
        var soundSource: String = SoundSource.BUNDLED.name,
        var builtInSoundId: String = BuiltInSounds.default.id,
        var useGlobalBuiltInSound: Boolean = true,
        var configurationSoundEnabled: Boolean = true,
        var configurationSoundId: String = "huh",
        var compilationSoundEnabled: Boolean = true,
        var compilationSoundId: String = "punch",
        var testFailureSoundEnabled: Boolean = true,
        var testFailureSoundId: String = "dog_laughing_meme",
        var networkSoundEnabled: Boolean = true,
        var networkSoundId: String = "yooo",
        var exceptionSoundEnabled: Boolean = true,
        var exceptionSoundId: String = "boom",
        var genericSoundEnabled: Boolean = true,
        var genericSoundId: String = BuiltInSounds.default.id,
        var successSoundEnabled: Boolean = false,
        var successSoundId: String = "yeah_boy",
        var customSoundPath: String = "",
        var alertDurationSeconds: Int = 3,
        var minProcessDurationSeconds: Int = 0,

        // Visual notification (balloon) settings — off by default
        var showVisualNotification: Boolean = false,
        var visualNotificationOnError: Boolean = true,
        var visualNotificationOnSuccess: Boolean = true,

        // Custom regex rules — evaluated before built-in classification (Phase 5)
        var customRules: MutableList<CustomRuleState> = mutableListOf(),

        // Exit-code rules for terminal alerts (Phase 6).
        // Default rules cover common shell exit codes.
        var exitCodeRules: MutableList<ExitCodeRuleState> = mutableListOf(
            // 130 = SIGINT (Ctrl+C) — user-initiated, suppress by default
            ExitCodeRuleState(exitCode = 130, enabled = true, kind = ErrorKind.GENERIC.name, soundId = null, suppress = true),
            // 127 = command not found
            ExitCodeRuleState(exitCode = 127, enabled = true, kind = ErrorKind.GENERIC.name, soundId = null, suppress = false),
            // 137 = SIGKILL (OOM or kill -9)
            ExitCodeRuleState(exitCode = 137, enabled = true, kind = ErrorKind.GENERIC.name, soundId = null, suppress = false),
            // 143 = SIGTERM (graceful kill)
            ExitCodeRuleState(exitCode = 143, enabled = true, kind = ErrorKind.GENERIC.name, soundId = null, suppress = false),
        ),
    )

    enum class SoundSource {
        BUNDLED,
        CUSTOM,
    }

    private var state = State()

    @Volatile
    private var compiledRuleEngine: CustomRuleEngine? = null

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state.copy(
            volumePercent = state.volumePercent.coerceIn(0, 100),
            // Phase 8: clamp per-kind volume overrides; null is preserved as-is
            configurationVolumePercent = state.configurationVolumePercent?.coerceIn(0, 100),
            compilationVolumePercent   = state.compilationVolumePercent?.coerceIn(0, 100),
            testFailureVolumePercent   = state.testFailureVolumePercent?.coerceIn(0, 100),
            networkVolumePercent       = state.networkVolumePercent?.coerceIn(0, 100),
            exceptionVolumePercent     = state.exceptionVolumePercent?.coerceIn(0, 100),
            genericVolumePercent       = state.genericVolumePercent?.coerceIn(0, 100),
            successVolumePercent       = state.successVolumePercent?.coerceIn(0, 100),
            builtInSoundId = normalizeSoundId(state.builtInSoundId),
            configurationSoundId = normalizeSoundId(state.configurationSoundId),
            compilationSoundId = normalizeSoundId(state.compilationSoundId),
            testFailureSoundId = normalizeSoundId(state.testFailureSoundId),
            networkSoundId = normalizeSoundId(state.networkSoundId),
            exceptionSoundId = normalizeSoundId(state.exceptionSoundId),
            genericSoundId = normalizeSoundId(state.genericSoundId),
            successSoundId = normalizeSoundId(state.successSoundId),
            alertDurationSeconds = state.alertDurationSeconds.coerceIn(1, 10),
            minProcessDurationSeconds = state.minProcessDurationSeconds.coerceIn(0, 300),
            customRules = state.customRules
                .take(CustomRuleEngine.MAX_RULES)
                .map { r ->
                    r.copy(
                        id = r.id.ifBlank { UUID.randomUUID().toString() },
                        pattern = r.pattern.trim().take(CustomRuleEngine.MAX_PATTERN_LENGTH),
                        matchTarget = (MatchTarget.entries.find { it.name == r.matchTarget }
                            ?: MatchTarget.LINE_TEXT).name,
                        kind = (ErrorKind.entries.find { it.name == r.kind }
                            ?.takeIf { it in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS }
                            ?: ErrorKind.GENERIC).name,
                    )
                }
                .toMutableList(),
            exitCodeRules = state.exitCodeRules.map { r ->
                r.copy(
                    kind = (ErrorKind.entries.find { it.name == r.kind }
                        ?.takeIf { it in CustomRuleEngine.ALLOWED_CUSTOM_RULE_KINDS }
                        ?: ErrorKind.GENERIC).name,
                    // Normalize soundId: blank/CUSTOM_FILE treated as no override
                    soundId = r.soundId
                        ?.takeIf { it.isNotBlank() && it != BuiltInSounds.CUSTOM_FILE_ID }
                        ?.let { normalizeSoundId(it) },
                )
            }.toMutableList(),
        )
        compiledRuleEngine = null  // invalidate cached engine whenever settings change
    }

    /**
     * Returns the compiled custom rule engine, creating and caching it on first access.
     * The cache is invalidated by [loadState] (i.e., when the user presses Apply in settings).
     */
    fun getCompiledRuleEngine(): CustomRuleEngine {
        return compiledRuleEngine ?: CustomRuleEngine(state.customRules).also {
            compiledRuleEngine = it
        }
    }

    private fun normalizeSoundId(id: String): String {
        return if (id == BuiltInSounds.CUSTOM_FILE_ID) {
            BuiltInSounds.CUSTOM_FILE_ID
        } else {
            BuiltInSounds.findByIdOrDefault(id).id
        }
    }

    companion object {
        fun getInstance(): AlertSettings = ApplicationManager.getApplication().getService(AlertSettings::class.java)
    }
}
