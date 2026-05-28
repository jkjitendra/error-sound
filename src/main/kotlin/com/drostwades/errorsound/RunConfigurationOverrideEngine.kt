package com.drostwades.errorsound

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class RunConfigurationOverrideEngine(
    private val overrides: List<AlertSettings.RunConfigurationOverrideState>,
) {
    data class Context(
        val configurationName: String,
        val configurationTypeId: String?,
        val configurationTypeName: String?,
    ) {
        val typeText: String =
            listOfNotNull(configurationTypeId, configurationTypeName)
                .joinToString(" ")
    }

    data class Match(
        val rowNumber: Int,
        val override: AlertSettings.RunConfigurationOverrideState,
    )

    fun firstMatch(context: Context): Match? {
        overrides.forEachIndexed { index, override ->
            if (!override.enabled) return@forEachIndexed
            if (override.pattern.isBlank()) return@forEachIndexed
            if (matches(override, context)) {
                return Match(index + 1, override)
            }
        }
        return null
    }

    fun applyTo(
        settings: AlertSettings.State,
        match: Match,
    ): AlertSettings.State {
        val override = match.override
        var resolved = settings
        if (override.overrideMinProcessDurationSeconds) {
            resolved = resolved.copy(
                minProcessDurationSeconds = override.minProcessDurationSeconds.coerceIn(0, 300),
            )
        }
        if (override.overrideAlertDurationSeconds) {
            resolved = resolved.copy(
                alertDurationSeconds = override.alertDurationSeconds.coerceIn(1, 10),
            )
        }
        if (override.overrideUseActualSoundDuration) {
            resolved = resolved.copy(useActualSoundDuration = override.useActualSoundDuration)
        }
        if (override.overrideShowVisualNotification) {
            resolved = resolved.copy(showVisualNotification = override.showVisualNotification)
        }
        if (override.overrideVisualNotificationOnError) {
            resolved = resolved.copy(visualNotificationOnError = override.visualNotificationOnError)
        }
        if (override.overrideVisualNotificationOnSuccess) {
            resolved = resolved.copy(visualNotificationOnSuccess = override.visualNotificationOnSuccess)
        }
        return resolved
    }

    private fun matches(
        override: AlertSettings.RunConfigurationOverrideState,
        context: Context,
    ): Boolean {
        val pattern = override.pattern.trim()
        val type = RunConfigurationOverrideMatchType.fromStored(override.matchType)
        return when (type) {
            RunConfigurationOverrideMatchType.EXACT_NAME ->
                context.configurationName == pattern
            RunConfigurationOverrideMatchType.NAME_CONTAINS ->
                context.configurationName.contains(pattern, ignoreCase = true)
            RunConfigurationOverrideMatchType.NAME_REGEX ->
                runCatching { Pattern.compile(pattern) }
                    .getOrNull()
                    ?.matcher(context.configurationName)
                    ?.find()
                    ?: false
            RunConfigurationOverrideMatchType.TYPE_CONTAINS ->
                context.typeText.contains(pattern, ignoreCase = true)
        }
    }

    companion object {
        const val MAX_OVERRIDES = 100
        const val MAX_DESCRIPTION_LENGTH = 240

        fun isValidRegex(pattern: String): Boolean =
            try {
                Pattern.compile(pattern)
                true
            } catch (_: PatternSyntaxException) {
                false
            }
    }
}
