package com.drostwades.errorsound

import java.util.regex.PatternSyntaxException

/**
 * Compiles custom rules from [AlertSettings.State.customRules] once and provides match methods
 * per [AlertSettings.MatchTarget].
 *
 * Rules are compiled on construction and reused until settings change (i.e., until
 * [AlertSettings.loadState] invalidates the cached instance). Callers should obtain the engine
 * via [AlertSettings.getCompiledRuleEngine] rather than constructing it directly.
 *
 * Invalid regex patterns are skipped silently at runtime (the persisted text is preserved
 * so the user can correct the pattern in the UI without data loss).
 */
class CustomRuleEngine(rules: List<AlertSettings.CustomRuleState>) {

    private data class CompiledRule(
        val regex: Regex,
        val target: AlertSettings.MatchTarget,
        val kind: ErrorKind,
    )

    private val compiledRules: List<CompiledRule>

    /** True if any enabled LINE_TEXT rules are compiled. Guards the hot console-filter path. */
    val hasLineTextRules: Boolean

    /** True if any enabled FULL_OUTPUT rules are compiled. Guards full-buffer evaluation. */
    val hasFullOutputRules: Boolean

    /** True if any enabled EXIT_CODE_AND_TEXT rules are compiled. */
    val hasExitCodeAndTextRules: Boolean

    init {
        val compiled = mutableListOf<CompiledRule>()
        for (rule in rules.take(MAX_RULES)) {
            if (!rule.enabled) continue
            val pattern = rule.pattern.trim().take(MAX_PATTERN_LENGTH)
            if (pattern.isBlank()) continue
            val target = AlertSettings.MatchTarget.entries.find { it.name == rule.matchTarget }
                ?: AlertSettings.MatchTarget.LINE_TEXT
            val kind = ErrorKind.entries.find { it.name == rule.kind }
                ?.takeIf { it in ALLOWED_CUSTOM_RULE_KINDS }
                ?: ErrorKind.GENERIC
            val regex = try {
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            } catch (_: PatternSyntaxException) {
                continue  // invalid pattern — skip at runtime, text is kept in state for UI editing
            }
            compiled += CompiledRule(regex, target, kind)
        }
        compiledRules = compiled
        hasLineTextRules = compiled.any { it.target == AlertSettings.MatchTarget.LINE_TEXT }
        hasFullOutputRules = compiled.any { it.target == AlertSettings.MatchTarget.FULL_OUTPUT }
        hasExitCodeAndTextRules = compiled.any { it.target == AlertSettings.MatchTarget.EXIT_CODE_AND_TEXT }
    }

    /**
     * Matches LINE_TEXT rules against a single line/chunk of text.
     * Returns the [ErrorKind] of the first matching rule, or null if none match.
     */
    fun matchLineText(line: String): ErrorKind? =
        compiledRules.firstOrNull {
            it.target == AlertSettings.MatchTarget.LINE_TEXT && it.regex.containsMatchIn(line)
        }?.kind

    /**
     * Matches FULL_OUTPUT rules against the complete buffered output text.
     * Returns the [ErrorKind] of the first matching rule, or null if none match.
     */
    fun matchFullOutput(text: String): ErrorKind? =
        compiledRules.firstOrNull {
            it.target == AlertSettings.MatchTarget.FULL_OUTPUT && it.regex.containsMatchIn(text)
        }?.kind

    /**
     * Matches EXIT_CODE_AND_TEXT rules against a combined context string:
     * `"exitcode:<code>\n<text>"`.
     *
     * This allows patterns such as:
     * - `exitcode:1` — matches any non-zero exit code 1
     * - `my-error` — matches the string "my-error" anywhere in the text
     * - `exitcode:127` — matches exactly exit code 127
     *
     * Returns the [ErrorKind] of the first matching rule, or null if none match.
     */
    fun matchExitCodeAndText(text: String, exitCode: Int): ErrorKind? {
        val combined = "exitcode:$exitCode\n$text"
        return compiledRules.firstOrNull {
            it.target == AlertSettings.MatchTarget.EXIT_CODE_AND_TEXT &&
                it.regex.containsMatchIn(combined)
        }?.kind
    }

    companion object {
        /** Maximum number of custom rules that will be compiled and evaluated. */
        const val MAX_RULES = 100

        /** Maximum pattern length (characters) accepted per rule. */
        const val MAX_PATTERN_LENGTH = 500

        /**
         * ErrorKind values permitted for custom rules.
         * NONE and SUCCESS are excluded — they are not user-triggerable error categories.
         */
        val ALLOWED_CUSTOM_RULE_KINDS: Set<ErrorKind> = setOf(
            ErrorKind.CONFIGURATION,
            ErrorKind.COMPILATION,
            ErrorKind.TEST_FAILURE,
            ErrorKind.NETWORK,
            ErrorKind.EXCEPTION,
            ErrorKind.GENERIC,
        )
    }
}
