package com.drostwades.errorsound

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ErrorSoundAlertSettings", storages = [Storage("errorSoundAlert.xml")])
@Service(Service.Level.APP)
class AlertSettings : PersistentStateComponent<AlertSettings.State> {

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
    )

    enum class SoundSource {
        BUNDLED,
        CUSTOM,
    }

    private var state = State()

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
        fun getInstance(): AlertSettings = ApplicationManager.getApplication().getService(AlertSettings::class.java)
    }
}
