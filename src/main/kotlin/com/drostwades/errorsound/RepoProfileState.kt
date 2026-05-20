package com.drostwades.errorsound

data class RepoProfileState(
    val schemaVersion: Int,
    val profileName: String?,
    val enabled: Boolean,
    val overrides: Overrides,
) {
    data class Overrides(
        val masterEnabled: Boolean? = null,
        val monitorConfiguration: Boolean? = null,
        val monitorCompilation: Boolean? = null,
        val monitorTestFailure: Boolean? = null,
        val monitorNetwork: Boolean? = null,
        val monitorException: Boolean? = null,
        val monitorGeneric: Boolean? = null,
        val monitorSuccess: Boolean? = null,
        val useGlobalBuiltInSound: Boolean? = null,
        val builtInSoundId: String? = null,
        val soundPerKind: Map<ErrorKind, KindSoundOverride> = emptyMap(),
        val volumePercent: Int? = null,
        val volumePerKind: Map<ErrorKind, KindVolumeOverride> = emptyMap(),
        val alertDurationSeconds: Int? = null,
        val useActualSoundDuration: Boolean? = null,
        val showVisualNotification: Boolean? = null,
        val visualNotificationOnError: Boolean? = null,
        val visualNotificationOnSuccess: Boolean? = null,
        val minProcessDurationSeconds: Int? = null,
    )

    data class KindSoundOverride(
        val enabled: Boolean? = null,
        val soundId: String? = null,
    )

    data class KindVolumeOverride(
        val enabled: Boolean,
        val volumePercent: Int? = null,
    )

    fun applyTo(base: AlertSettings.State): AlertSettings.State {
        if (!enabled) return base

        var resolved = base

        overrides.masterEnabled?.let { resolved = resolved.copy(enabled = it) }

        if (overrides.hasMonitoringOverrides()) {
            resolved = resolved.copy(
                monitorConfiguration = overrides.monitorConfiguration ?: resolved.monitorConfiguration,
                monitorCompilation = overrides.monitorCompilation ?: resolved.monitorCompilation,
                monitorTestFailure = overrides.monitorTestFailure ?: resolved.monitorTestFailure,
                monitorNetwork = overrides.monitorNetwork ?: resolved.monitorNetwork,
                monitorException = overrides.monitorException ?: resolved.monitorException,
                monitorGeneric = overrides.monitorGeneric ?: resolved.monitorGeneric,
                monitorSuccess = overrides.monitorSuccess ?: resolved.monitorSuccess,
            )
        }

        if (overrides.hasSoundOverrides()) {
            resolved = resolved.copy(
                useGlobalBuiltInSound = overrides.useGlobalBuiltInSound ?: resolved.useGlobalBuiltInSound,
                builtInSoundId = overrides.builtInSoundId ?: resolved.builtInSoundId,
                configurationSoundEnabled = overrides.soundEnabled(ErrorKind.CONFIGURATION, resolved.configurationSoundEnabled),
                configurationSoundId = overrides.soundId(ErrorKind.CONFIGURATION, resolved.configurationSoundId),
                compilationSoundEnabled = overrides.soundEnabled(ErrorKind.COMPILATION, resolved.compilationSoundEnabled),
                compilationSoundId = overrides.soundId(ErrorKind.COMPILATION, resolved.compilationSoundId),
                testFailureSoundEnabled = overrides.soundEnabled(ErrorKind.TEST_FAILURE, resolved.testFailureSoundEnabled),
                testFailureSoundId = overrides.soundId(ErrorKind.TEST_FAILURE, resolved.testFailureSoundId),
                networkSoundEnabled = overrides.soundEnabled(ErrorKind.NETWORK, resolved.networkSoundEnabled),
                networkSoundId = overrides.soundId(ErrorKind.NETWORK, resolved.networkSoundId),
                exceptionSoundEnabled = overrides.soundEnabled(ErrorKind.EXCEPTION, resolved.exceptionSoundEnabled),
                exceptionSoundId = overrides.soundId(ErrorKind.EXCEPTION, resolved.exceptionSoundId),
                genericSoundEnabled = overrides.soundEnabled(ErrorKind.GENERIC, resolved.genericSoundEnabled),
                genericSoundId = overrides.soundId(ErrorKind.GENERIC, resolved.genericSoundId),
                successSoundEnabled = overrides.soundEnabled(ErrorKind.SUCCESS, resolved.successSoundEnabled),
                successSoundId = overrides.soundId(ErrorKind.SUCCESS, resolved.successSoundId),
            )
        }

        if (overrides.hasVolumeOverrides()) {
            resolved = resolved.copy(
                volumePercent = overrides.volumePercent ?: resolved.volumePercent,
                configurationVolumePercent = overrides.volumePercent(ErrorKind.CONFIGURATION, resolved.configurationVolumePercent),
                compilationVolumePercent = overrides.volumePercent(ErrorKind.COMPILATION, resolved.compilationVolumePercent),
                testFailureVolumePercent = overrides.volumePercent(ErrorKind.TEST_FAILURE, resolved.testFailureVolumePercent),
                networkVolumePercent = overrides.volumePercent(ErrorKind.NETWORK, resolved.networkVolumePercent),
                exceptionVolumePercent = overrides.volumePercent(ErrorKind.EXCEPTION, resolved.exceptionVolumePercent),
                genericVolumePercent = overrides.volumePercent(ErrorKind.GENERIC, resolved.genericVolumePercent),
                successVolumePercent = overrides.volumePercent(ErrorKind.SUCCESS, resolved.successVolumePercent),
            )
        }

        if (overrides.alertDurationSeconds != null || overrides.useActualSoundDuration != null) {
            resolved = resolved.copy(
                alertDurationSeconds = overrides.alertDurationSeconds ?: resolved.alertDurationSeconds,
                useActualSoundDuration = overrides.useActualSoundDuration ?: resolved.useActualSoundDuration,
            )
        }

        if (
            overrides.showVisualNotification != null ||
            overrides.visualNotificationOnError != null ||
            overrides.visualNotificationOnSuccess != null
        ) {
            resolved = resolved.copy(
                showVisualNotification = overrides.showVisualNotification ?: resolved.showVisualNotification,
                visualNotificationOnError = overrides.visualNotificationOnError ?: resolved.visualNotificationOnError,
                visualNotificationOnSuccess = overrides.visualNotificationOnSuccess ?: resolved.visualNotificationOnSuccess,
            )
        }

        overrides.minProcessDurationSeconds?.let {
            resolved = resolved.copy(minProcessDurationSeconds = it)
        }

        return resolved
    }

    private fun Overrides.hasMonitoringOverrides(): Boolean =
        monitorConfiguration != null ||
            monitorCompilation != null ||
            monitorTestFailure != null ||
            monitorNetwork != null ||
            monitorException != null ||
            monitorGeneric != null ||
            monitorSuccess != null

    private fun Overrides.hasSoundOverrides(): Boolean =
        useGlobalBuiltInSound != null || builtInSoundId != null || soundPerKind.isNotEmpty()

    private fun Overrides.hasVolumeOverrides(): Boolean =
        volumePercent != null || volumePerKind.isNotEmpty()

    private fun Overrides.soundEnabled(kind: ErrorKind, fallback: Boolean): Boolean =
        soundPerKind[kind]?.enabled ?: fallback

    private fun Overrides.soundId(kind: ErrorKind, fallback: String): String =
        soundPerKind[kind]?.soundId ?: fallback

    private fun Overrides.volumePercent(kind: ErrorKind, fallback: Int?): Int? {
        val override = volumePerKind[kind] ?: return fallback
        return if (override.enabled) override.volumePercent else null
    }
}
