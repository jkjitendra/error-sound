package com.drostwades.errorsound

import java.util.regex.PatternSyntaxException

/**
 * Compiles suppression rules once and provides target-specific match methods.
 * Suppression rules silence known-noisy matches before alerts are dispatched.
 */
class SuppressionRuleEngine(rules: List<AlertSettings.SuppressionRuleState>) {

    data class SuppressionRuleMatch(
        val id: String,
        val pattern: String,
        val target: AlertSettings.MatchTarget,
        val description: String,
    )

    private data class CompiledRule(
        val id: String,
        val pattern: String,
        val regex: Regex,
        val target: AlertSettings.MatchTarget,
        val description: String,
    ) {
        fun toMatch(): SuppressionRuleMatch = SuppressionRuleMatch(id, pattern, target, description)
    }

    private val compiledRules: List<CompiledRule>

    val hasLineTextRules: Boolean
    val hasFullOutputRules: Boolean
    val hasExitCodeAndTextRules: Boolean

    init {
        val compiled = mutableListOf<CompiledRule>()
        for (rule in rules.take(MAX_RULES)) {
            if (!rule.enabled) continue
            val pattern = rule.pattern.trim().take(MAX_PATTERN_LENGTH)
            if (pattern.isBlank()) continue
            val target = AlertSettings.MatchTarget.entries.find { it.name == rule.matchTarget }
                ?: AlertSettings.MatchTarget.LINE_TEXT
            val regex = try {
                Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            } catch (_: PatternSyntaxException) {
                continue
            }
            compiled += CompiledRule(
                id = rule.id,
                pattern = pattern,
                regex = regex,
                target = target,
                description = rule.description.trim().take(MAX_DESCRIPTION_LENGTH),
            )
        }
        compiledRules = compiled
        hasLineTextRules = compiled.any { it.target == AlertSettings.MatchTarget.LINE_TEXT }
        hasFullOutputRules = compiled.any { it.target == AlertSettings.MatchTarget.FULL_OUTPUT }
        hasExitCodeAndTextRules = compiled.any { it.target == AlertSettings.MatchTarget.EXIT_CODE_AND_TEXT }
    }

    fun matchLineText(line: String): Boolean =
        explainLineText(line) != null

    fun explainLineText(line: String): SuppressionRuleMatch? =
        compiledRules.firstOrNull {
            it.target == AlertSettings.MatchTarget.LINE_TEXT && it.regex.containsMatchIn(line)
        }?.toMatch()

    fun matchFullOutput(text: String): Boolean =
        explainFullOutput(text) != null

    fun explainFullOutput(text: String): SuppressionRuleMatch? =
        compiledRules.firstOrNull {
            it.target == AlertSettings.MatchTarget.FULL_OUTPUT && it.regex.containsMatchIn(text)
        }?.toMatch()

    fun matchExitCodeAndText(text: String, exitCode: Int): Boolean =
        explainExitCodeAndText(text, exitCode) != null

    fun explainExitCodeAndText(text: String, exitCode: Int): SuppressionRuleMatch? {
        val combined = "exitcode:$exitCode\n$text"
        return compiledRules.firstOrNull {
            it.target == AlertSettings.MatchTarget.EXIT_CODE_AND_TEXT &&
                it.regex.containsMatchIn(combined)
        }?.toMatch()
    }

    companion object {
        const val MAX_RULES = CustomRuleEngine.MAX_RULES
        const val MAX_PATTERN_LENGTH = CustomRuleEngine.MAX_PATTERN_LENGTH
        const val MAX_DESCRIPTION_LENGTH = 200
    }
}
