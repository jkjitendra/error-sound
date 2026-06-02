package com.drostwades.errorsound

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TerminalCommandSuppressionEngine(
    private val suppressions: List<AlertSettings.TerminalCommandSuppressionState>,
) {
    data class Match(
        val rowNumber: Int,
        val suppression: AlertSettings.TerminalCommandSuppressionState,
    )

    fun firstMatch(command: String, exitCode: Int): Match? {
        suppressions.take(MAX_RULES).forEachIndexed { index, suppression ->
            if (!suppression.enabled) return@forEachIndexed
            if (suppression.pattern.isBlank()) return@forEachIndexed
            if (!exitCodeMatches(suppression, exitCode)) return@forEachIndexed
            if (commandMatches(suppression, command)) {
                return Match(index + 1, suppression)
            }
        }
        return null
    }

    private fun exitCodeMatches(
        suppression: AlertSettings.TerminalCommandSuppressionState,
        exitCode: Int,
    ): Boolean {
        return when (TerminalCommandSuppressionExitCodeMode.fromStored(suppression.exitCodeMode)) {
            TerminalCommandSuppressionExitCodeMode.ANY_NON_ZERO -> exitCode != 0
            TerminalCommandSuppressionExitCodeMode.SPECIFIC_EXIT_CODE -> exitCode == suppression.exitCode
        }
    }

    private fun commandMatches(
        suppression: AlertSettings.TerminalCommandSuppressionState,
        command: String,
    ): Boolean {
        val pattern = suppression.pattern.trim().take(MAX_PATTERN_LENGTH)
        return when (TerminalCommandSuppressionMatchType.fromStored(suppression.matchType)) {
            TerminalCommandSuppressionMatchType.EXACT_COMMAND ->
                command.trim() == pattern
            TerminalCommandSuppressionMatchType.COMMAND_CONTAINS ->
                command.contains(pattern, ignoreCase = true)
            TerminalCommandSuppressionMatchType.COMMAND_REGEX ->
                runCatching { Pattern.compile(pattern) }
                    .getOrNull()
                    ?.matcher(command)
                    ?.find()
                    ?: false
        }
    }

    companion object {
        const val MAX_RULES = 100
        const val MAX_PATTERN_LENGTH = CustomRuleEngine.MAX_PATTERN_LENGTH
        const val MAX_DESCRIPTION_LENGTH = 240
        const val MIN_EXIT_CODE = -9999
        const val MAX_EXIT_CODE = 9999

        fun isValidRegex(pattern: String): Boolean =
            try {
                Pattern.compile(pattern)
                true
            } catch (_: PatternSyntaxException) {
                false
            }
    }
}
