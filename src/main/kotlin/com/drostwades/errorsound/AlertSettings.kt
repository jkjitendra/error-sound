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

    /** Where a custom rule pattern is applied when classifying output. */
    enum class MatchTarget { LINE_TEXT, FULL_OUTPUT, EXIT_CODE_AND_TEXT }

    /**
     * A single user-defined regex rule.
     * All fields have defaults so IntelliJ's XML serializer can create instances without arguments.
     */
    data class CustomRuleState(
        var id: String = UUID.randomUUID().toString(),
        var enabled: Boolean = true,
        var pattern: String = "",
        var matchTarget: String = MatchTarget.LINE_TEXT.name,
        var kind: String = ErrorKind.GENERIC.name,
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

        // Custom regex rules — evaluated before built-in classification
        var customRules: MutableList<CustomRuleState> = mutableListOf(),
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
