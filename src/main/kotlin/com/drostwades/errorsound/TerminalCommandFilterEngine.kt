package com.drostwades.errorsound

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TerminalCommandFilterEngine(
    private val mode: TerminalCommandFilterMode,
    private val filters: List<AlertSettings.TerminalCommandFilterState>,
) {
    data class Match(
        val rowNumber: Int,
        val filter: AlertSettings.TerminalCommandFilterState,
    )

    data class Decision(
        val eligible: Boolean,
        val mode: TerminalCommandFilterMode,
        val match: Match?,
    )

    fun evaluate(command: String): Decision {
        if (mode == TerminalCommandFilterMode.OFF) {
            return Decision(eligible = true, mode = mode, match = null)
        }

        val match = firstMatch(command)
        return when (mode) {
            TerminalCommandFilterMode.OFF -> Decision(eligible = true, mode = mode, match = null)
            TerminalCommandFilterMode.ALLOWLIST_ONLY -> Decision(eligible = match != null, mode = mode, match = match)
            TerminalCommandFilterMode.BLOCKLIST -> Decision(eligible = match == null, mode = mode, match = match)
        }
    }

    private fun firstMatch(command: String): Match? {
        filters.take(MAX_RULES).forEachIndexed { index, filter ->
            if (!filter.enabled) return@forEachIndexed
            if (filter.pattern.isBlank()) return@forEachIndexed
            if (commandMatches(filter, command)) {
                return Match(index + 1, filter)
            }
        }
        return null
    }

    private fun commandMatches(
        filter: AlertSettings.TerminalCommandFilterState,
        command: String,
    ): Boolean {
        val pattern = filter.pattern.trim().take(MAX_PATTERN_LENGTH)
        return when (TerminalCommandFilterMatchType.fromStored(filter.matchType)) {
            TerminalCommandFilterMatchType.EXACT_COMMAND ->
                command.trim() == pattern
            TerminalCommandFilterMatchType.COMMAND_CONTAINS ->
                command.contains(pattern, ignoreCase = true)
            TerminalCommandFilterMatchType.COMMAND_REGEX ->
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

        fun isValidRegex(pattern: String): Boolean =
            try {
                Pattern.compile(pattern)
                true
            } catch (_: PatternSyntaxException) {
                false
            }
    }
}
